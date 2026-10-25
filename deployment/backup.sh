#!/bin/sh
# Usage: sh deployment/backup.sh /etc/service-advertiser/deployment.env /srv/backups
set -eu
umask 077
config=${1:?Pass the absolute deployment environment file}
destination=${2:?Pass the backup directory outside the database volume}
case "$config" in /*) ;; *) echo 'Configuration path must be absolute' >&2; exit 1;; esac
mkdir -p "$destination"
backup="$destination/service-advertiser-$(date -u +%Y%m%dT%H%M%SZ)-$$.dump"
trap 'rm -f "$backup.tmp"' EXIT HUP INT TERM
docker compose --env-file "$config" -f "$(dirname "$0")/compose.yaml" --profile database \
  exec -T postgres sh -ec 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup.tmp"
test -s "$backup.tmp"
mv "$backup.tmp" "$backup"
printf '%s\n' "$backup"
