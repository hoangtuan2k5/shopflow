# ShopFlow production deployment

Production runs from `docker-compose.prod.yml` on the primary VPS. The stack
contains a Caddy frontend, a private Spring Boot backend, and a private
PostgreSQL service.

## VPS setup

Create `/opt/shopflow/.env` on the VPS and keep it out of Git:

```dotenv
DB_NAME=shopflow
DB_USERNAME=shopflow
DB_PASSWORD=replace-with-a-long-random-password
```

The deploy job checks out `origin/main` into `/opt/shopflow` and runs:

```bash
docker compose --env-file .env -f docker-compose.prod.yml up -d --build --remove-orphans --wait
```

The PostgreSQL volume is named `shopflow_prod_pgdata`. It is separate from the
local `shopflow_pgdata` volume.

## Cloudflare

Create proxied or DNS-only A records for `shopflow` in both zones, pointing to
the VPS public IP. Keep them DNS-only while Caddy obtains its first
Let's Encrypt certificates. After HTTPS is verified, enable the Cloudflare
proxy and use **Full (strict)** SSL mode.

## GitHub Actions secrets

Add these repository secrets:

- `VPS_HOST`: `ssh.hoangtuan.dev`
- `VPS_USER`: `ubuntu`
- `VPS_SSH_KEY`: the private Ed25519 key used by Actions
- `VPS_HOST_KEY`: the complete verified line from `ssh-keyscan -t ed25519 ssh.hoangtuan.dev`

The deploy job runs only after the backend and frontend jobs pass on a push to
`main`. Pull requests and feature/develop pushes never deploy.

The frontend container exposes `/healthz` only as a local container health
probe; application traffic still uses the SPA and `/api/*` routes.

## Local verification

Use temporary values when validating the Compose file locally:

```bash
DB_USERNAME=test DB_PASSWORD=test docker compose -f docker-compose.prod.yml config
docker compose -f docker-compose.prod.yml build
```
