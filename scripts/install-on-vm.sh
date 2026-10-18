#!/usr/bin/env bash
# Run from the unpacked release on Ubuntu, with sudo. This installs files; start is explicit.
set -euo pipefail
umask 077
if [[ $EUID -ne 0 ]]; then echo 'Run with sudo.' >&2; exit 1; fi
release_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ -f "$release_dir/backend/finance-support.jar" ]]
[[ -d "$release_dir/frontend/dist" ]]
apt-get update </dev/null
DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-21-jre-headless caddy postgresql-client </dev/null
if ! id finance-support >/dev/null 2>&1; then
  useradd --system --user-group --home-dir /opt/finance-support --shell /usr/sbin/nologin finance-support
fi
install -d -m 755 /opt/finance-support /opt/finance-support/backend /opt/finance-support/frontend /opt/finance-support/scripts
install -d -m 700 /etc/finance-support /var/backups/finance-support
if [[ ! -f /etc/finance-support/backend.env ]]; then
  install -m 600 "$release_dir/deploy/backend.env.example" /etc/finance-support/backend.env
  if [[ -f /root/.config/finance-support/database.env ]]; then
    # Append generated DB settings so they take precedence over example values.
    cat /root/.config/finance-support/database.env >> /etc/finance-support/backend.env
  fi
  echo 'Created /etc/finance-support/backend.env. Fill OpenAI settings before starting.'
fi
if [[ ! -f /etc/finance-support/caddy.env ]]; then
  install -m 600 "$release_dir/deploy/caddy.env.example" /etc/finance-support/caddy.env
fi
# Stop writers before the snapshot. If backup or installation fails the service stays stopped.
systemctl stop finance-support.service 2>/dev/null || true
set -a
. /etc/finance-support/backend.env
set +a
if [[ "${DATABASE_PASSWORD:-replace_me}" == replace_me || -z "${DATABASE_PASSWORD:-}" ]]; then
  echo 'Set database credentials in /etc/finance-support/backend.env, then repeat installation.' >&2
  exit 1
fi
if [[ ! "$DATABASE_URL" =~ ^jdbc:postgresql://127\.0\.0\.1:5432/[a-zA-Z0-9_]+$ ]]; then
  echo 'Installer expects PostgreSQL at 127.0.0.1:5432 with a simple database name.' >&2
  exit 1
fi
backup_file="/var/backups/finance-support/before-release-$(date -u +%Y%m%dT%H%M%SZ)-$$.dump"
PGPASSWORD="$DATABASE_PASSWORD" pg_dump -w -h 127.0.0.1 -p 5432 -U "$DATABASE_USERNAME" -d "${DATABASE_URL##*/}" -Fc -f "$backup_file"
chmod 600 "$backup_file"
if [[ -f /opt/finance-support/backend/finance-support.jar ]]; then
  cp /opt/finance-support/backend/finance-support.jar /var/backups/finance-support/previous-finance-support.jar
fi
install -m 644 "$release_dir/backend/finance-support.jar" /opt/finance-support/backend/finance-support.jar
# Replace the complete asset directory so obsolete bundles are not published.
assets_stage="$(mktemp -d /opt/finance-support/frontend/.dist.XXXXXX)"
cp -R "$release_dir/frontend/dist/." "$assets_stage/"
chmod -R a+rX "$assets_stage"
rm -rf /opt/finance-support/frontend/dist
mv "$assets_stage" /opt/finance-support/frontend/dist
install -m 755 "$release_dir/scripts/create-user.sh" /opt/finance-support/scripts/create-user.sh
install -m 644 "$release_dir/deploy/finance-support.service" /etc/systemd/system/finance-support.service
install -d -m 755 /etc/systemd/system/caddy.service.d
install -m 644 "$release_dir/deploy/caddy-finance.conf" /etc/systemd/system/caddy.service.d/finance.conf
# Preserve the previous proxy configuration for manual rollback.
if [[ -f /etc/caddy/Caddyfile ]]; then cp /etc/caddy/Caddyfile /var/backups/finance-support/previous-Caddyfile; fi
install -m 644 "$release_dir/deploy/Caddyfile" /etc/caddy/Caddyfile
systemctl daemon-reload
printf 'Installed. Database backup: %s\n' "$backup_file"
echo 'Next: configure HTTP/HTTPS, create user, validate Caddy, then start services. See docs/deployment.md.'
