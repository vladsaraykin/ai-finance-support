#!/usr/bin/env bash
set -euo pipefail
umask 077
# Database variables must be exported before running. Password never goes into argv or a file.
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
default_jar="$project_dir/backend/target/finance-support-0.1.0-SNAPSHOT.jar"
if [[ -f "$project_dir/backend/finance-support.jar" ]]; then default_jar="$project_dir/backend/finance-support.jar"; fi
app_jar="${FINANCE_JAR:-$default_jar}"
if [[ ! -f "$app_jar" ]]; then
  echo 'Build backend first: cd backend && mvn package' >&2
  exit 1
fi
export BOOTSTRAP_LOGIN="${BOOTSTRAP_LOGIN:-vlad13reg}"
if [[ -z "${BOOTSTRAP_PASSWORD:-}" ]]; then
  read -r -s -p "Password for $BOOTSTRAP_LOGIN: " BOOTSTRAP_PASSWORD
  printf '\n'
fi
export BOOTSTRAP_PASSWORD
exec java -jar "$app_jar" --spring.main.web-application-type=none --app.bootstrap.enabled=true
