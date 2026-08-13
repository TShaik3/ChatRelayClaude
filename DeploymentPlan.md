# DeploymentPlan.md

Plan for running ChatRelay on a Raspberry Pi at home, reachable from any device the user owns
(laptop, phone, tablet) whether at home or away — without opening any ports on the router.

**Chosen access model: Tailscale.** The Pi and every device that should reach it join a private
WireGuard-based mesh VPN (Tailscale). There is no public URL, no port forwarding, and no exposed
IP — only devices logged into the same tailnet can reach the app at all. This sidesteps the one
open risk the migration explicitly flagged and deferred: `SecurityConfig` disables CSRF, which
was called "acceptable for a same-origin SPA... but would need revisiting behind a public
multi-origin deployment" (see [MigrationPlan.md](MigrationPlan.md), Phase 3). Tailscale keeps the
app off the public internet entirely, so that revisit isn't required for this deployment.

No application code changes were needed to ship this. The existing [Dockerfile](Dockerfile)
already produces a single-deployable image (Svelte build baked into the Spring Boot jar, served
same-origin on port 8080) that runs on Pi hardware unmodified — this plan is mostly
infrastructure, plus a couple of small [docker-compose.yml](docker-compose.yml) tweaks (a
`restart` policy, and a configurable Tailscale-only port binding) covered in Phases 2-3 below.

---

## Target hardware & OS

- **Raspberry Pi 4 (4GB+) or Pi 5.** Postgres + a JVM together want more headroom than a Pi
  Zero/3 can give; 4GB is a comfortable minimum, 8GB if the budget allows it.
- **Raspberry Pi OS Lite, 64-bit.** No desktop environment needed — this is a headless server.
  64-bit matters because every base image the [Dockerfile](Dockerfile) uses
  (`eclipse-temurin:21-jdk-alpine`/`-jre-alpine`, `node:22-alpine`, `postgres:16-alpine`) publishes
  official `linux/arm64` variants, so the existing multi-stage build runs unmodified on Pi
  hardware — nothing in the Dockerfile or compose file needs to change for architecture.
- **External USB SSD for the Postgres volume**, strongly recommended over the microSD card.
  SD cards wear out under a database's constant small writes and are a common cause of silent
  corruption on long-running Pi servers — the pgdata volume is exactly the kind of write pattern
  that triggers this.

---

## Phase 0 — Prepare the Pi

- Flash Raspberry Pi OS Lite (64-bit) with Raspberry Pi Imager; enable SSH and set a hostname
  (e.g. `chatrelay-pi`) during imaging so there's no monitor/keyboard step.
- `sudo apt update && sudo apt full-upgrade -y`.
- Install Docker Engine + the Compose plugin (Docker's `get.docker.com` convenience script covers
  Raspberry Pi OS correctly); add the login user to the `docker` group so `docker compose` doesn't
  need `sudo` for every command.
- If using an external SSD: format it, mount it (e.g. at `/mnt/chatrelay-data`), and add it to
  `/etc/fstab` so it remounts automatically after a reboot or power loss — a home Pi will lose
  power eventually.

## Phase 1 — Install Tailscale everywhere

- On the Pi: install via `curl -fsSL https://tailscale.com/install.sh | sh`, then `sudo tailscale
  up` and authenticate to the account's tailnet. `systemctl enable --now tailscaled` (the install
  script does this by default) so it reconnects automatically after every reboot.
- On every device that should reach the app — laptop, phone, tablet — install the Tailscale
  client and log into the **same tailnet**. That's the entire "any device" requirement: once a
  device is on the tailnet, it can resolve and reach the Pi with no other configuration.
- Note the Pi's **MagicDNS name** (e.g. `chatrelay-pi.<tailnet-name>.ts.net`) from `tailscale
  status` or the admin console — this is what devices will use instead of a raw IP, and it stays
  stable even if the Pi's local network address changes.
- Optional but recommended: in the Tailscale admin console, mark the Pi's node key as non-expiring
  (or set a long refresh reminder) so a background server doesn't silently drop off the tailnet
  months later when a normal key-expiry prompt would otherwise require re-authenticating on a
  device with a screen.

## Phase 2 — Get the app running on the Pi

Two ways to build the image; start with the first and only move to the second if it becomes
annoying.

**Option A — build directly on the Pi (start here).** Clone this repo onto the Pi and run
`docker compose up --build -d` as-is. The multi-stage Dockerfile builds the Svelte frontend and
Spring Boot backend natively on the Pi's own arm64 CPU — no new infrastructure, no registry, works
out of the box. The one downside is speed: a Gradle + npm build on a Pi's CPU is meaningfully
slower than on a dev machine (rough expectation: several minutes to ~20 depending on Pi model),
which mostly matters if redeploying often.

**Option B — cross-build on a dev machine, ship the image.** If Option A's rebuild time becomes
a real friction point, build for arm64 elsewhere and push to a registry (Docker Hub or GHCR):

```bash
docker buildx build --platform linux/arm64 -t <registry>/chatrelay:latest --push .
```

Then point `docker-compose.yml`'s `backend` service at `image: <registry>/chatrelay:latest`
instead of `build: { context: ., dockerfile: Dockerfile }`, and on the Pi run `docker compose pull
&& docker compose up -d`. Only switch to this if iterating quickly; it trades one extra
piece of infrastructure (a registry) for faster deploys.

**Either way, before starting the stack for real:**
- If using the external SSD, point Postgres's volume at it instead of a plain Docker-managed
  volume — replace `docker-compose.yml`'s `pgdata:/var/lib/postgresql/data` named volume with a
  bind mount to the SSD path, e.g. `/mnt/chatrelay-data/postgres:/var/lib/postgresql/data`.
- Add `restart: unless-stopped` to both services in `docker-compose.yml` so the stack comes back
  up automatically after a reboot or crash, without a login session or cron job needed to start
  it.

## Phase 3 — Harden before anyone logs in over the tailnet

- **Change the seeded `admin`/`admin` login immediately.** The `ApplicationRunner` added in
  Phase 5 of the migration seeds that account on any empty database specifically so there's a way
  to log in at all — it is not meant to survive first contact. Log in once, then use the account
  settings UI (`PUT /api/users/me`, backed by
  [AccountSettingsDialog.svelte](frontend/src/lib/components/AccountSettingsDialog.svelte)) to set
  a real password before inviting anyone else onto the tailnet to use the app.
- **HTTPS via `tailscale serve` was tried first, and abandoned.** The plan going in was to proxy
  the app behind `tailscale serve` (`sudo tailscale serve --bg http://localhost:8080`) so devices
  hit a real TLS cert on the tailnet's own HTTPS hostname instead of the session cookie riding
  over plain HTTP. Page loads and REST calls worked fine through it, but the STOMP WebSocket
  (`/ws`) never completed its handshake — confirmed in the browser's Network tab as a repeating
  cycle of failed upgrade attempts with no response headers ever coming back, and unchanged after
  updating Tailscale to the latest version. Symptom in the app: sent messages never appeared for
  anyone (including the sender) without a manual page refresh, since
  [ChatArea.svelte](frontend/src/lib/components/ChatArea.svelte)'s send flow deliberately has no
  optimistic UI update and depends entirely on the WebSocket broadcast arriving.
- **What's actually running: bound straight to the Pi's Tailscale interface, no `serve` proxy.**
  `sudo tailscale serve reset` turns the proxy off. `docker-compose.yml`'s port binding reads from
  a `TAILSCALE_BIND` environment variable (defaulting to `127.0.0.1`, i.e. unreachable off-box, if
  unset) — on the Pi, a `.env` file (gitignored, since this value is host-specific) sets it to the
  Pi's own Tailscale address:
  ```bash
  tailscale ip -4   # note this address
  echo "TAILSCALE_BIND=<that address>" > .env
  docker compose up -d
  ```
  This still keeps the app off the regular LAN (only the Tailscale interface is bound, not
  `0.0.0.0`) while sidestepping whatever `tailscale serve`'s reverse proxy was doing to the
  WebSocket upgrade. The tradeoff: devices now browse to `http://<tailscale-ip-or-name>:8080`
  (plain HTTP, with a port number) instead of a clean HTTPS tailnet URL. That's a smaller loss
  than it sounds — traffic between tailnet nodes is already WireGuard-encrypted at the network
  layer, so this drops app-layer TLS on top of an already-encrypted link, not encryption itself.
- **HTTPS without `tailscale serve`: Spring Boot terminates TLS itself.** Since only running this
  for a few weeks, a one-time cert (no renewal automation) is enough — a Tailscale-issued cert is
  Let's Encrypt-backed and lasts ~90 days, well past that. This avoids `tailscale serve`'s proxy
  layer entirely (the thing that broke the WebSocket upgrade above) by having the app answer
  HTTPS directly; Spring Boot 3.1+ loads a PEM cert/key pair natively via `server.ssl.certificate`
  / `server.ssl.certificate-private-key`, no keystore conversion needed. Wired as opt-in env vars
  in `docker-compose.yml` (`TLS_ENABLED`/`TLS_CERT_FILE`/`TLS_KEY_FILE`, all unset/false by
  default) so this doesn't affect anyone running the compose file without certs.

  On the Pi:
  ```bash
  sudo tailscale cert <hostname>.<tailnet-name>.ts.net   # from `tailscale status`/admin console
  mkdir -p certs
  sudo mv <hostname>.<tailnet-name>.ts.net.crt certs/tailnet.crt
  sudo mv <hostname>.<tailnet-name>.ts.net.key certs/tailnet.key
  sudo chown "$USER":"$USER" certs/tailnet.*
  ```
  Add to `.env` (alongside the existing `TAILSCALE_BIND` line):
  ```
  TLS_ENABLED=true
  TLS_CERT_FILE=/certs/tailnet.crt
  TLS_KEY_FILE=/certs/tailnet.key
  ```
  Then `docker compose up -d` (no rebuild needed — this is config, not code). Browse to
  `https://<hostname>.<tailnet-name>.ts.net:8080` afterward, **not** the raw Tailscale IP — the
  cert is issued for the hostname, so a browser connecting by IP will fail certificate validation
  even though the connection itself works.

  The one thing to remember later: this cert quietly expires around the 90-day mark with nothing
  in this plan renewing it. If this deployment outlives "a few weeks," either rerun the
  `tailscale cert` step then, or revisit setting up actual renewal automation.

## Phase 4 — Keep it running

- **Backups.** A home Pi is a single point of failure with no redundant hardware behind it — back
  up the Postgres data, not just the code (the code is already in this git repo). A simple daily
  cron job running `docker compose exec postgres pg_dump -U chatrelay chatrelay` and copying the
  dump off the Pi (to a laptop, cloud storage, anywhere off-device) is enough; the point is that a
  failed SD card or SSD doesn't also erase every chat and account.
- **Logs.** `docker compose logs -f backend` for a live tail; add `docker compose logs --since
  24h` to a routine check-in if the app runs unattended for stretches. No log aggregation service
  is warranted at this scale.
- **Updates.** Pulling new code is `git pull && docker compose up --build -d` (Option A) or
  rebuilding + pushing then `docker compose pull && docker compose up -d` on the Pi (Option B).
  Either way Flyway (already wired into the Spring Boot startup) applies any new schema
  migrations automatically — no manual migration step.

---

## Why not the alternatives

- **Public internet + a domain (port forward + Caddy/nginx for HTTPS):** more moving parts (DNS,
  cert renewal, router configuration) and puts the login page in front of anyone on the internet,
  not just the user's own devices — a materially different risk profile for an app that ships
  with CSRF protection disabled. Worth revisiting only if the app needs to be reachable by people
  outside the user's own device set.
- **Cloudflare Tunnel:** avoids port forwarding like Tailscale does, but still ends in a
  world-reachable HTTPS URL by design, and routes traffic through Cloudflare's network. Reasonable
  if the goal is sharing the app publicly; not needed here since the goal is private access from
  the user's own devices.
