#!/bin/sh
# Checks that Beacon is up and the server is healthy, and pushes an alert to your phone through
# ntfy (https://ntfy.sh) when something goes wrong, and again when it's fixed.
#
#   ./monitor.sh          check now; prints what it found (for cron, every 5 minutes)
#   ./monitor.sh --test   send a test alert, to check your phone gets it
#
# Set BEACON_NTFY_TOPIC in .env to a long, hard-to-guess topic name (anyone who knows it can read
# the alerts), and subscribe to that topic in the ntfy app. Checks:
#   web      the web app answers on this machine
#   api      the API answers through the web app (/api/site)
#   public   BEACON_MONITOR_URL (BEACON_APP_URL by default) answers; set it to "none" where the
#            server can't reach its own public address (Tailscale's HTTPS, from the same machine)
#   disk     the disk holding Beacon is under BEACON_DISK_ALERT_PERCENT full (85 by default)
#   backup   the newest nightly backup is under 26 hours old
# A check alerts after failing twice in a row, so a restart during an update doesn't page you.
set -u
cd "$(dirname "$0")"

# Read only the settings this script needs from .env, without running it as a script.
setting() {
    sed -n "s/^$1=//p" .env 2>/dev/null | tail -n 1 | sed 's/^["'\'']//; s/["'\'']$//'
}
topic=$(setting BEACON_NTFY_TOPIC)
server=$(setting BEACON_NTFY_SERVER); server=${server:-https://ntfy.sh}
port=$(setting BEACON_PORT); port=${port:-8790}
app_url=$(setting BEACON_MONITOR_URL); app_url=${app_url:-$(setting BEACON_APP_URL)}
disk_limit=$(setting BEACON_DISK_ALERT_PERCENT); disk_limit=${disk_limit:-85}
state=.monitor
mkdir -p "$state"

notify() { # title, message, priority, tags
    if [ -z "$topic" ]; then
        echo "BEACON_NTFY_TOPIC isn't set in .env, so no alert was sent: $1: $2" >&2
        return 1
    fi
    curl -fsS -m 15 -o /dev/null -H "Title: $1" -H "Priority: $3" -H "Tags: $4" \
        -d "$2" "$server/$topic"
}

if [ "${1:-}" = "--test" ]; then
    notify "Beacon: test alert" "Alerts from $(hostname) reach you." default white_check_mark \
        && echo "Sent. Check the ntfy app."
    exit
fi

# Records one check's result and alerts when it changes: on the 2nd failure in a row, and
# when it passes again after an alert.
record() { # name, ok|fail, detail
    file="$state/$1"
    count=$(cat "$file" 2>/dev/null || echo 0)
    if [ "$2" = ok ]; then
        echo "ok      $1"
        if [ "$count" -ge 2 ]; then
            notify "Beacon: $1 is OK again" "$3" default white_check_mark
        fi
        echo 0 > "$file"
    else
        echo "FAILING $1: $3"
        count=$((count + 1))
        echo "$count" > "$file"
        if [ "$count" -eq 2 ]; then
            notify "Beacon: $1 is failing" "$3" high warning
        fi
    fi
}

answers() { # url: true when it answers 2xx/3xx within 15 seconds
    curl -fsS -m 15 -o /dev/null "$1" 2>/dev/null
}

if answers "http://127.0.0.1:$port/"; then
    record web ok "The web app answers."
else
    record web fail "The web app on port $port isn't answering. Try: docker compose ps"
fi

if answers "http://127.0.0.1:$port/api/site"; then
    record api ok "The API answers."
else
    record api fail "The API isn't answering through the web app. Try: docker compose logs --tail 50 api"
fi

if [ -n "$app_url" ] && [ "$app_url" != none ]; then
    if answers "$app_url/"; then
        record public ok "$app_url answers."
    else
        record public fail "$app_url isn't answering, though it may only be the network or Tailscale."
    fi
fi

used=$(df -P . | awk 'NR == 2 { sub("%", "", $5); print $5 }')
if [ "${used:-0}" -lt "$disk_limit" ]; then
    record disk ok "The disk is $used% full."
else
    record disk fail "The disk is $used% full (alert at $disk_limit%). Old backups are in backups/."
fi

newest=$(ls -t backups/beacon-*.dump 2>/dev/null | head -n 1)
if [ -n "$newest" ] && [ -n "$(find "$newest" -mmin -1560)" ]; then
    record backup ok "The newest backup is $newest."
else
    record backup fail "No backup in the last 26 hours. Try: docker compose logs --tail 20 backup"
fi
