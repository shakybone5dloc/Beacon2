#!/bin/sh
# Runs in the backup container: one dump a night at about 3 AM server time, and one right away
# on start, kept for KEEP_DAYS days. Dumps are pg_dump's custom format; restore.sh reads them.
set -eu

dump() {
    file="/backups/beacon-$(date +%Y-%m-%d-%H%M).dump"
    if pg_dump -Fc -f "$file.partial"; then
        mv "$file.partial" "$file"
        echo "$(date -Iseconds) backed up to $(basename "$file") ($(du -h "$file" | cut -f1))"
    else
        rm -f "$file.partial"
        echo "$(date -Iseconds) backup FAILED" >&2
    fi
    find /backups -name 'beacon-*.dump' -mtime "+$KEEP_DAYS" -delete
}

dump
while true; do
    # Seconds until the next 3:00.
    now=$(date +%s)
    next=$(date -d "$(date +%Y-%m-%d) 03:00" +%s 2>/dev/null || date -D '%Y-%m-%d %H:%M' -d "$(date +%Y-%m-%d) 03:00" +%s)
    [ "$next" -le "$now" ] && next=$((next + 86400))
    sleep $((next - now))
    dump
done
