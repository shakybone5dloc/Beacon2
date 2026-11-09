#!/bin/sh
# Pulls the newest images CI built from main and restarts what changed. Data is untouched;
# the API applies any new database migrations as it starts.
set -eu
cd "$(dirname "$0")"
docker compose pull
docker compose up -d
docker image prune -f > /dev/null
docker compose ps
