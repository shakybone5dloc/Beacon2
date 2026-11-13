# Running Beacon on the home server

Beacon runs on the server in Docker: Postgres, the API, the web app (nginx serving Angular and
passing `/api` to the API), the page printer and its proxy, and a nightly backup. Only the web
app has a port, `127.0.0.1:8790`, and Tailscale puts HTTPS in front of it, so Beacon is reachable
from your own devices anywhere and from nobody else.

```
phone / laptop ──Tailscale (HTTPS)──▶ server:443 ──▶ web (nginx, :8790) ──▶ api (:8080) ──▶ db
                                                                              │      backup ──▶ ./backups
                                                      internal network only   ▼
                                                     capture (Chromium, :8090) ──▶ capture-proxy ──▶ internet
```

**The page printer.** "Save posting as PDF" opens whatever URL is on an application in
Chromium, so Chromium runs walled off in the `capture` container. It holds no secrets (no
`.env`), it's on an internal Docker network with no route out, and every request it makes,
including redirects and the page's own scripts, goes through `capture-proxy` (Squid), which
refuses any address that isn't the public internet: the database and other containers, this
machine's network and tailnet, and a cloud server's metadata service. `capture-proxy`'s log
(`docker compose logs capture-proxy`) shows each request and whether it was allowed.

New versions: every push to `main` runs the tests on GitHub Actions and, when they pass, builds
`ghcr.io/shakybone5dloc/beacon2-api`, `beacon2-web`, `beacon2-capture` and
`beacon2-capture-proxy`. Branches and pull requests run the tests
but build nothing, so only what's merged into `main` goes live. The server checks for new images
every 10 minutes and updates itself (see [Updates](#updates)).

## One-time setup

### 1. Tailscale

On the server:

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
```

Open the link it prints and sign in. Install the Tailscale app on your phone and PC and sign in
with the same account. In the Tailscale admin console, under **DNS**, turn on **MagicDNS** and
**HTTPS certificates**. Your server's address is shown there, e.g. `server.tail1234.ts.net`.

### 2. Files

From the repo on your PC:

```bash
ssh server mkdir -p beacon/backups
scp deploy/compose.yaml deploy/backup.sh deploy/restore.sh deploy/update.sh deploy/.env.example server:beacon/
ssh server 'cd beacon && chmod +x *.sh && cp .env.example .env'
```

Then edit `~/beacon/.env` on the server (`ssh server nano beacon/.env`):

- `POSTGRES_PASSWORD`: anything long and random, e.g. from `openssl rand -base64 24`.
- `BEACON_SECRET_KEY`: **the same value as your PC's `.env`**, so your saved API keys still work.
- `BEACON_APP_URL`: `https://` plus your server's Tailscale name.
- `MAIL_*`: the same as your PC's `.env`.

### 3. Images

If the packages on GitHub are private (the default for new packages), the server needs to sign
in to pull them. Create a token at GitHub → Settings → Developer settings → Personal access
tokens (classic) with only **read:packages**, then:

```bash
ssh server
echo YOUR_TOKEN | docker login ghcr.io -u shakybone5dloc --password-stdin
```

Or make the packages public under your GitHub profile → Packages → each package → Package
settings → Change visibility. They hold no secrets; your data and keys live only on the server.
New packages start private, so after a new image is added (like `beacon2-capture` and
`beacon2-capture-proxy`), make it public too, or rely on the token above.

### 4. Start it

```bash
ssh server 'cd beacon && ./update.sh'
ssh server 'sudo tailscale serve --bg 8790'
```

`tailscale serve` survives reboots. Open `https://server.<your-tailnet>.ts.net` on any of your
devices. Docker restarts the containers whenever the server boots.

### 5. Move your data (once)

On your PC, in the repo:

```powershell
powershell -File deploy/migrate-from-pc.ps1
```

It dumps your local database (leaving it as it is), copies the dump to the server and prints
the `ssh -t server '... ./restore.sh ...'` command that loads it. After that, use the server's
Beacon, not the one on your PC.

### 6. Automatic updates

```bash
ssh server '(crontab -l 2>/dev/null; echo "*/10 * * * * $HOME/beacon/update.sh --auto > /dev/null 2>&1") | crontab -'
```

## Updates

Cron runs `./update.sh --auto` every 10 minutes. When CI has published a new version, it:

1. backs up the database to `backups/beacon-…-before-update.dump`, so a new version's database
   changes can be undone with `./restore.sh` (it doesn't update if that backup fails);
2. restarts the containers whose image changed; the API applies any database migrations as it
   starts;
3. waits for the API to report healthy and writes each step to `update.log`.

When nothing is new, it does nothing and writes nothing. So: merge into `main`, CI takes about
10 minutes, and the server is updated within 10 more. To hold the server on one version, set
`BEACON_TAG=<commit sha>` in `.env`; to stop updates, remove the line with `crontab -e`.

## Everyday

| To | Run on the server, in `~/beacon` |
|---|---|
| Update right now, without waiting for cron | `./update.sh` |
| See when it last updated | `tail update.log` |
| See what's running | `docker compose ps` |
| Read the API's log | `docker compose logs -f api` |
| List backups | `ls -lh backups` |
| Back up right now | `docker compose restart backup` |
| Restore a backup | `./restore.sh backups/beacon-2026-11-09-0300.dump` |
| Go back to an earlier version | Set `BEACON_TAG=<commit sha>` in `.env`, then `./update.sh` |

## Backups

The `backup` container dumps the database when it starts and every night at about 3 AM, into
`~/beacon/backups`, keeping 14 days (`BACKUP_KEEP_DAYS`). Each dump holds everything: applications,
documents, saved keys (still encrypted with `BEACON_SECRET_KEY`, so keep a copy of that key
somewhere safe, apart from the backups).

The backups sit on the same disk as the database, which covers mistakes and bad updates but not
a dead disk. To keep a copy elsewhere, pull them to your PC now and then:

```bash
scp -r server:beacon/backups ./beacon-backups
```

**Test a restore** after setting up, so you know it works before you need it: restore last
night's dump with `./restore.sh` and check that your applications are there.
