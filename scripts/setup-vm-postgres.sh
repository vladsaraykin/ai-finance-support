#!/usr/bin/env bash
# Run on Ubuntu as the SSH user, with passwordless sudo.
set -euo pipefail
umask 077
stage='initial checks'
trap 'setup_exit=$?; printf "Database setup failed during %s (exit %s).\n" "$stage" "$setup_exit" >&2' ERR

config_dir="${XDG_CONFIG_HOME:-$HOME/.config}/finance-support"
config_file="$config_dir/database.env"
pg_port="${FINANCE_POSTGRES_PORT:-5432}"
[[ "$pg_port" =~ ^[0-9]+$ ]] && (( pg_port > 0 && pg_port < 65536 )) || { echo 'Invalid FINANCE_POSTGRES_PORT' >&2; exit 1; }
mkdir -p "$config_dir"
chmod 700 "$config_dir"
exec 9>"$config_dir/database-setup.lock"
flock -n 9 || { echo 'Another setup is running' >&2; exit 1; }
sudo -n true </dev/null

if ! command -v psql >/dev/null || ! command -v pg_lsclusters >/dev/null; then
    stage='installing PostgreSQL packages'
    printf '[1/4] Installing PostgreSQL packages...\n'
    # bash -s reads this script from stdin; subprocesses must not consume it.
    sudo -n apt-get update </dev/null
    sudo -n env DEBIAN_FRONTEND=noninteractive apt-get -o DPkg::Lock::Timeout=120 install -y postgresql postgresql-client </dev/null
fi
stage='starting PostgreSQL'
printf '[2/4] Starting PostgreSQL...\n'
sudo -n systemctl start postgresql </dev/null
if ! pg_isready -q -h 127.0.0.1 -p "$pg_port"; then
    echo 'PostgreSQL is not listening on the selected port. Check pg_lsclusters; no existing configuration was changed.' >&2
    exit 1
fi

admin() { sudo -n -u postgres psql -X -q -v ON_ERROR_STOP=1 -p "$pg_port" -d postgres "$@"; }
stage='checking database and role'
role_exists="$(admin -Atc "SELECT count(*) FROM pg_roles WHERE rolname = 'finance_app'" </dev/null)"
db_owner="$(admin -Atc "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = 'finance_support'" </dev/null)"
if [[ -n "$db_owner" && "$db_owner" != finance_app ]]; then
    echo 'finance_support already exists with a different owner; inspect it before reusing this name.' >&2
    exit 1
fi
unsafe_role="$(admin -Atc "SELECT count(*) FROM pg_roles WHERE rolname = 'finance_app' AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls OR NOT rolcanlogin)" </dev/null)"
[[ "$unsafe_role" == 0 ]] || { echo 'Existing finance_app has unexpected privileges; review the role.' >&2; exit 1; }


if [[ -f "$config_file" ]]; then
    # Read only the generated hex secret; do not execute a configuration file.
    expected_url="DATABASE_URL=jdbc:postgresql://127.0.0.1:$pg_port/finance_support"
    grep -Fxq "$expected_url" "$config_file" || { echo 'Existing database.env uses another port or database; inspect it first.' >&2; exit 1; }
    grep -Fxq 'DATABASE_USERNAME=finance_app' "$config_file" || { echo 'Unexpected database user in database.env.' >&2; exit 1; }
    password="$(sed -n 's/^DATABASE_PASSWORD=//p' "$config_file")"
    [[ "$password" =~ ^[a-f0-9]{64}$ ]] || { echo 'Unexpected password format in database.env; inspect configuration.' >&2; exit 1; }
else
    [[ "$role_exists" == 0 ]] || { echo 'finance_app already exists but database.env is missing. Restore its credentials; the password was not reset.' >&2; exit 1; }
    password="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
    printf 'DATABASE_URL=jdbc:postgresql://127.0.0.1:%s/finance_support\nDATABASE_USERNAME=finance_app\nDATABASE_PASSWORD=%s\n' "$pg_port" "$password" > "$config_file"
fi
chmod 600 "$config_file"

# The generated password is strictly hex. Pass SQL over stdin, not in command arguments.
# Disable statement/error-statement logging for this session before handling the secret.
stage='creating database and role'
printf '[3/4] Preparing finance_support and finance_app...\n'
{
    printf "SET log_statement = 'none'; SET log_min_error_statement = 'panic';\n"
    printf "SET password_encryption = 'scram-sha-256';\n"
    printf "SELECT format('CREATE ROLE finance_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %%L', '%s') WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'finance_app') \\gexec\n" "$password"
    printf "SELECT format('CREATE DATABASE %%I OWNER %%I ENCODING %%L', 'finance_support', 'finance_app', 'UTF8') WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'finance_support') \\gexec\n"
} | admin


passfile="$(mktemp "$config_dir/pgpass.XXXXXX")"
trap 'rm -f "$passfile"' EXIT
printf '127.0.0.1:%s:finance_support:finance_app:%s\n' "$pg_port" "$password" > "$passfile"
stage='verifying database connection'
printf '[4/4] Verifying database connection...\n'
PGPASSFILE="$passfile" psql -X -w -h 127.0.0.1 -p "$pg_port" -U finance_app -d finance_support -v ON_ERROR_STOP=1 -Atc 'SELECT current_database(), current_user' </dev/null
printf 'Database connection verified. Credentials saved to %s (mode 600).\n' "$config_file"
printf 'Tables will be created by Flyway when the backend starts.\n'
