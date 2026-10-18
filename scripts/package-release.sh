#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
mvn -f backend/pom.xml package
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run build
stage_dir="$(mktemp -d)"
trap 'rm -rf "$stage_dir"' EXIT
mkdir -p "$stage_dir/backend" "$stage_dir/frontend" "$stage_dir/scripts" "$stage_dir/docs" dist
cp backend/target/finance-support-0.1.0-SNAPSHOT.jar "$stage_dir/backend/finance-support.jar"
cp -R frontend/dist "$stage_dir/frontend/dist"
mkdir -p "$stage_dir/deploy"
cp deploy/Caddyfile deploy/finance-support.service deploy/caddy-finance.conf \
  deploy/backend.env.example deploy/caddy.env.example "$stage_dir/deploy/"
cp docs/deployment.md docs/authentication.md "$stage_dir/docs/"
cp scripts/create-user.sh scripts/setup-vm-postgres.sh scripts/install-on-vm.sh "$stage_dir/scripts/"
tar -czf dist/finance-support-release.tar.gz -C "$stage_dir" .
printf 'Release: %s/dist/finance-support-release.tar.gz\n' "$project_dir"
