#!/bin/sh
# Restores a backup over the server's database: ./restore.sh backups/beacon-2026-11-09-0300.dump
# Stops the API and web app while it runs, replaces every table, then starts them again.
set -eu
cd "$(dirname "$0")"

file="${1:?Usage: ./restore.sh path/to/beacon-....dump}"
[ -f "$file" ] || { echo "No such file: $file" >&2; exit 1; }

printf 'This replaces everything in the server'"'"'s Beacon database with %s. Type "restore" to go on: ' "$file"
read -r answer
[ "$answer" = "restore" ] || { echo "Nothing changed."; exit 1; }

docker compose stop web api
docker compose up -d db
# --clean drops each object before recreating it; --if-exists keeps a fresh database from erroring.
docker compose exec -T db pg_restore -U postgres -d beacon --clean --if-exists --no-owner < "$file"
docker compose up -d
echo "Restored $file. The API runs any newer migrations as it starts."
