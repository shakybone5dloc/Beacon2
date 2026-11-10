#!/bin/sh
# Pulls the newest images CI built from main and restarts what changed. Data is untouched;
# the API applies any new database migrations as it starts.
#
#   ./update.sh          update now, and show what's running
#   ./update.sh --auto   for cron: does nothing (and prints nothing) unless a new version is out,
#                        backs the database up before switching, and logs to update.log
set -eu
cd "$(dirname "$0")"

auto=false
[ "${1:-}" = "--auto" ] && auto=true

log() {
    line="$(date '+%Y-%m-%d %H:%M:%S') $*"
    echo "$line"
    if $auto; then echo "$line" >> update.log; fi
}

# One run at a time: a slow pull must not overlap the next cron run.
exec 9> .update.lock
if ! flock -n 9; then
    $auto || echo "Another update is running." >&2
    exit 0
fi

# The image IDs behind Beacon's own two images (Postgres updates only by hand).
ids() {
    for image in $(docker compose config --images | grep beacon2- | sort); do
        docker image inspect --format '{{.Id}}' "$image" 2>/dev/null || echo none
    done
}

before=$(ids)
if ! docker compose pull --quiet 2> pull.err; then
    log "Pull failed: $(tail -n 3 pull.err | tr '\n' ' ')"
    rm -f pull.err
    exit 1
fi
rm -f pull.err
after=$(ids)

if [ "$before" = "$after" ]; then
    if $auto; then exit 0; fi
    echo "Already up to date."
else
    # A copy of the data from just before the new version's migrations run, for ./restore.sh.
    if docker compose ps --status running --services | grep -qx db; then
        file="backups/beacon-$(date +%Y-%m-%d-%H%M)-before-update.dump"
        if docker compose exec -T db pg_dump -U postgres -d beacon -Fc > "$file"; then
            log "Backed up to $file before updating"
        else
            rm -f "$file"
            log "Backup before updating FAILED; not updating"
            exit 1
        fi
    fi
    log "New version found; restarting"
fi

if $auto; then
    docker compose up -d --quiet-pull > /dev/null 2>&1
else
    docker compose up -d --quiet-pull
fi
docker image prune -f > /dev/null

if [ "$before" != "$after" ]; then
    # The API reports healthy once it has started and migrated the database.
    for _ in $(seq 1 36); do
        status=$(docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q api)" 2>/dev/null || echo unknown)
        [ "$status" = healthy ] && break
        sleep 5
    done
    log "Update finished; API is $status"
fi

$auto || docker compose ps
