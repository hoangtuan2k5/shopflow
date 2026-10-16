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

The frontend healthcheck probes Caddy's loopback admin API
(`http://127.0.0.1:2019/config/`), so it does not depend on public DNS, TLS,
or Cloudflare. Docker runs a minimal init process in the container to reap any
healthcheck child processes. Application traffic still uses the SPA and
`/api/*` routes. The deploy job separately verifies the public HTTPS endpoint
from the VPS host with normal TLS validation.

## TLS verification (SF-106, SF-119, SF-120)

PR [#103](https://github.com/hoangtuan2k5/shopflow/pull/103) removed the
certificate-verification bypass and separated two checks:

- Docker checks Caddy's loopback HTTP admin endpoint. This checks the local
  process without depending on DNS, Cloudflare or a public certificate.
- After rollout, the deploy job runs `curl --fail --silent --show-error
  --connect-timeout 5 --max-time 10 https://shopflow.hoangtuan.dev/healthz`.
  Certificate verification remains enabled; an untrusted certificate or a
  hostname mismatch returns a non-zero exit code and fails the deploy job.

This replaces SF-106's original proposal to make container health depend on
public TLS. A TLS failure does **not** make the container unhealthy. The HTTPS
check runs after rollout, so failure reports an unsuccessful deployment but
neither prevents that rollout nor automatically rolls it back. It is not a
scheduled certificate monitor. With Cloudflare proxying enabled, the public
probe validates the edge certificate; it does not directly inspect Caddy's
origin certificate. Keep Cloudflare in Full (strict) mode for origin validation.

### Repeatable local verification

From the repository root, with Python 3, OpenSSL and curl installed:

```bash
python3 scripts/test-deploy-tls.py
```

The test reads the HTTPS probe arguments from the deployment workflow and uses
an ephemeral TLS server bound to `127.0.0.1`. Certificates and private keys are
generated in a temporary directory and removed afterwards. No production
configuration, certificate or data is changed. The valid case explicitly trusts
the temporary certificate; the invalid cases never disable certificate checks.

Results on 2026-10-15: **4 tests passed**.

| Scenario | Expected and observed result |
| --- | --- |
| Production configuration | HTTPS probe keeps `--fail`; no `-k`/`--insecure`; no `--no-check-certificate` in workflow or production Compose |
| Trusted certificate, matching hostname | curl exits `0` and receives `ok` |
| Untrusted self-signed certificate | curl exits `60`; request is rejected |
| Trusted certificate, wrong hostname | curl exits `60`; request is rejected |

The backend CI job runs this check before the Java build. These tests verify
local curl certificate rejection and the configured probe, not production
DNS, Cloudflare settings, Caddy certificate renewal or expiry handling by an
actual expired certificate. The existing successful production deployment is
recorded in [run 30922577367](https://github.com/hoangtuan2k5/shopflow/actions/runs/30922577367).

## Persistent database volume and migrations

`POSTGRES_USER` and `POSTGRES_PASSWORD` initialize a PostgreSQL role only when
`shopflow_prod_pgdata` is first created. Changing `DB_USERNAME` or
`DB_PASSWORD` in `.env` does not change an existing database role. For a
password rotation, change the existing PostgreSQL role first, then update
`.env` and redeploy; do not change `.env` alone.

The PostgreSQL healthcheck authenticates with those credentials through the
Docker service network, so a credential drift makes the database unhealthy
instead of allowing a backend startup that cannot acquire a connection.

Before rebuilding an existing stack, the deploy job runs `flyway validate`
against the current database with the application credentials. It aborts the
deploy if the credentials or migration history do not match. Once a migration
has been deployed, do not edit, rename, or delete it; create a new versioned
migration instead. Use `flyway repair` only as an incident-recovery action
after a backup and review.

## Local verification

Use temporary values when validating the Compose file locally:

```bash
DB_USERNAME=test DB_PASSWORD=test docker compose -f docker-compose.prod.yml config
docker compose -f docker-compose.prod.yml build
```

## Product image storage (SF-74 / SF-90)

Set `R2_ENDPOINT`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET` and
`R2_PUBLIC_BASE_URL` in `/opt/shopflow/.env`. Use the account S3 API endpoint
for `R2_ENDPOINT` and the HTTPS custom domain for `R2_PUBLIC_BASE_URL`.
The staging profile and production Compose require all five values. Never
commit credentials or expose them through frontend environment variables.

Use a dedicated public product-image bucket, a bucket-scoped Object Read & Write
API token and a custom domain; disable the development `r2.dev` URL. Keep backup
buckets private with separate credentials. The Java S3 client uses region `auto`,
path style and disabled chunked encoding. Dev starts without credentials; upload
requires configured storage. CI uses a shared mock client and never calls R2.

Migration V6 adds nullable `products.image_key`; existing products keep their
placeholder. See [the image contract](./api-product-image-spec.md) for limits,
permissions and replacement/rollback behavior. This change does not deploy itself:
production deployment still requires the release workflow from `develop` to `main`.
