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

### SSH script stdin (SF-128)

The workflow sends its remote script to `bash -s` through SSH stdin. Flyway's
`docker compose run` and both `docker compose exec -T` health probes redirect
stdin from `/dev/null` so Compose cannot consume the remaining script. `-T`
disables pseudo-TTY allocation, not stdin. Without this isolation, validation
can succeed while Bash reaches EOF before rollout, yielding a green deploy job
without updating containers. Keep these redirects when changing deploy commands.

Run the regression check with Docker Compose available:

```bash
python3 scripts/test-deploy-stdin.py
```

The check reads the three commands from the workflow, retains their Compose
options and redirections, and substitutes harmless commands in a temporary
`postgres:17-alpine` container with no network or production mounts. It verifies
that subsequent markers run, nonzero child exits stop the script, and removing
each stdin redirect reproduces silent truncation. Containers are cleaned up
even on test failure. CI runs this before the Java build; the first run may
pull the test image. This does not exercise SSH, Flyway, rollout or live health
endpoints. After a release, verify running containers and the live application
independently of the workflow's exit status.

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


## Catalog vector index (SF-75)

Before the first SF-75 release to main, add the following to `/opt/shopflow/.env`
without committing or printing their values:

- `GEMINI_API_KEY`: AI Studio key permitted to call `gemini-embedding-2`.
- `QDRANT_API_KEY`: independently generated secret (for example, use Python's
  `secrets.token_hex(32)` and write it directly into the protected env file).
- `QDRANT_URL`: production Compose sets this to `http://qdrant:6333`; no host
  override is required. Host-run local backend defaults to `http://localhost:6333`.

Missing `QDRANT_API_KEY` fails Compose interpolation, including the Flyway
preflight. Missing Gemini key does not prevent startup; index/search returns
503 until configured. Never print `docker compose config` with real secrets;
use `config --quiet` or validate with dummy environment values.

Qdrant uses image `qdrant/qdrant:v1.18.3` and volume
`shopflow_prod_qdrant_data`. It has no published ports, public domain or reverse
proxy. Local Compose binds only loopback port 6333. No curl/wget healthcheck is
invented for the minimal image: `up --wait` checks its running state, not Qdrant
API readiness. The application boots without reaching Qdrant/Gemini.

After deployment, authenticate as owner, obtain the session/CSRF cookies and
run `POST /catalog-index` manually (see [contract](api-catalog-index-spec.md)).
Check `indexed` against the active product count and try owner-only
`GET /catalog-index/search`. Re-running replaces vectors by product ID and
removes inactive/deleted products only after every batch upserts successfully.
Do not run catalog imports while reindexing. The current implementation assumes
one backend instance; it serializes index calls within that instance.

Ordinary container recreation preserves the named volume. Never use
`docker compose down -v` on production. The index can be rebuilt from PostgreSQL
and Gemini; SF-75 does not add snapshot/backup scheduling. Changing model or
vector dimensions requires rebuilding the dedicated derived collection; it
must never share a collection with other applications.

Observe actual Qdrant memory/CPU with `docker stats --no-stream` after idle,
indexing and searches. This story does not deploy itself on a develop merge;
the separate approved release to main triggers production rollout.


## Product advisory chat (SF-76)

No new external account, public Qdrant domain, database migration or frontend
secret is required. Keep GEMINI_API_KEY on the backend. SF-75's private Qdrant
service must be deployed and indexed; configure QDRANT_API_KEY before promotion
to main as described above. Guest chat uses the existing session-CSRF flow.

Shared application properties select Gemma 4 31B with one Gemma 4 26B A4B fallback,
daily AI-attempt budget 500 and initial retrieval score floor 0.70. Both models
use Minimal thinking, temperature 0.2 and a structured JSON reply. Output is
bounded to 800 tokens to accommodate Vietnamese text, JSON and the requested
signature. Model availability and account quota must be checked independently;
being listed by models.list does not prove reliable generation access.

Each chat consumes one embedding attempt and normally one generation attempt;
fallback costs another attempt. The local synchronized counter is per backend
process and resets on restart, not a durable billing limit. Provider HTTP 429
may affect both models even when ShopFlow's own counter has room. Shared quota
or key errors cannot be fixed by model fallback alone.

Chat embedding/Qdrant use shorter network timeouts than owner indexing. The
primary generation attempt has up to 6s and fallback up to 10s, both bounded
by the remaining 18s pipeline budget. Only frontend chat uses a 20s timeout;
other requests keep their existing default. This is not a hard SLA for a stalled
local database. Never publish model tracebacks, API keys or request transcripts
in application logs; public chat errors use sanitized 400/429/503 bodies, with
403 enforced by Spring Security before controller work.

After rollout, verify guest CSRF, a matching product with current price/stock,
VI/EN text, close/open product details, mobile input/cart access and model outage
recovery on both public storefront domains. Conversation disappears on reload.
No transcript is stored server-side, and live advisory text still needs factual
review before commercial use of the demo descriptions. See
[chat QA](qa-product-advisory-chat.md) for observed provider/quality limits.

Normal `mvn verify` excludes tests tagged external and needs no AI key. A manual
external test is available only against a disposable loopback backend:
`CHAT_QA_BASE_URL=http://127.0.0.1:<port> bash backend/mvnw -f backend/pom.xml
-Dtest=ChatExternalTests -Dgroups=external -DexcludedGroups=none test`.
It records exploratory model responses and verifies returned cards against
catalog API data; it does not certify language or compatibility accuracy.

## Transactional email (SF-143)

Add only these settings to each environment's existing private `.env`:

```dotenv
RESEND_API_KEY=replace-with-a-resend-sending-key
MAIL_FROM='ShopFlow <shopflow@hoangtuan.dev>'
```

Keep `/opt/shopflow/.env` and local workspace `.env` separate, mode600, outside
Git. Never overwrite existing DB/R2/Qdrant credentials. The root `.env.example`
is a customer-auth example, not a replacement for a complete deployment file.
Compose now explicitly forwards both variables to the backend; editing the env
file alone does not change a running container. Container recreation occurs
only in the approved deployment workflow. Never paste unredacted Compose
configuration, environment output or provider responses into logs/Jira.

The client uses POST `https://api.resend.com/emails`, text and HTML, with the
challenge UUID as `Idempotency-Key`. Password-change notices use a separate
`password-changed/<challenge UUID>` key. There are no automatic retries; an
ambiguous timeout is a failed send for the auth caller. Missing key/sender
throws a sanitized `EmailUnavailableException` only when mail is required;
application startup, catalog and legacy demo login continue to work. The client
returns the accepted provider ID, which does **not** prove delivery to Inbox.

Registration/reset services must reserve quota and commit PENDING_SEND before
calling this client outside DB transactions/locks, then update only the matching
generation. They must return the contract's uniform202 on recipient-specific
provider failures and must never mark a failed generation ISSUED. These services
and their four-second response floor are subsequent tasks, not part of SF-143.

Before enabling real mail, check in the Resend dashboard:

1. `hoangtuan.dev` is verified and the sending key is scoped to the intended domain.
2. Publish the exact Resend DKIM/SPF records in Cloudflare DNS and verify them;
   preserve other mail providers' records and avoid multiple SPF records at the
   same DNS name. Check DMARC policy/alignment and the configured sender.
3. Cloudflare R2 is object storage; it does not configure outgoing email. Email
   Routing is optional inbound forwarding and does not provide this sender API.
4. Obtain owner approval for the exact test recipient before a real send. Record
   UTC send time, redacted recipient, provider message ID/status and observed
   Inbox/Spam result separately. Never record the OTP, key or full message body.

No DNS changes, real mail, container recreation or production env edits are
performed by this implementation task. CI uses only a loopback fake HTTP server
and empty mail credentials in the test profile.

API reference: [Resend send email](https://resend.com/docs/api-reference/emails/send-email).

## Customer registration (SF-144)

Add `OTP_HMAC_SECRET` to each environment's existing private env. It must decode
from Base64 to at least32 random bytes, be independent of Resend/Google keys,
and remain outside Git and the database. Generate a different secret per dev
and production environment, for example locally with:

```bash
python3 -c 'import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())'
```

Store the output privately; never paste it into chat/Jira/logs. Preserve all
existing env settings. Compose explicitly forwards this variable to the backend;
only an approved deployment recreates the container. A missing, invalid Base64
or short key disables OTP operations with 503 without preventing application
startup or demo password login. Rotation invalidates pending challenges only;
keep the same production key across all backend instances serving both domains.

Apply V7 after SF-142 review/merge, then configure Resend after SF-143 and run
SF-144 together. The worktree currently contains uncommitted copies of these
prerequisites for validation; it is not an independent rollout over old schema.
Do not expose the temporary test mail server or its code-reading endpoint.

New guest writes require CSRF. Bootstrap using GET `/auth/session`; send cookie
`XSRF-TOKEN` in `X-XSRF-TOKEN`, retain the pre-auth session across issue/verify,
and refresh CSRF after login/logout. Allowed Origin values are the two public
HTTPS domains in staging and explicit local Vite origins for dev. No arbitrary
credentialed CORS is enabled. IP quota currently uses the servlet peer address,
ignoring raw X-Forwarded-For. Behind Caddy this may share a quota across clients
until a trusted proxy chain is pinned; do not enable unrestricted forwarded
header trust to bypass this limit.

Registration reserves persistent quota before the provider call, commits a
pending challenge, sends outside the transaction, then activates only the same
generation. Synthetic and recipient-specific delivery failures retain the
same202 body and four-second minimum response floor. Pending signup does not
create a user. Verified signup creates CUSTOMER only, clears sensitive payload
and does not log in automatically. Legacy demo login and sessions are retained.

Password reset, Google login, guest checkout enforcement/cart preservation and
real-provider acceptance remain separate tasks. Do not claim those are enabled
by deploying this registration change.

## Password recovery (SF-145)

Use the same verified Resend sender and independent `OTP_HMAC_SECRET` already
configured for registration. No additional secret, migration, SMTP dependency,
queue or reset-token store is introduced. The shared OTP service/repository
now support REGISTER and RESET_PASSWORD with separate email-purpose budgets,
HMAC inputs and challenge slots; IP/daily budgets remain shared.

Enable POST `/auth/password/forgot` and `/auth/password/reset` only with the
reviewed prerequisites SF-142–144. Both require CSRF and allowed Origin checks.
The existing `/auth/otp/resend` derives purpose from the bound challenge. There
is no independent OTP-verify endpoint, passwordless login or reset capability
token. Reset accepts only challengeId, OTP and newPassword; the target account
is read from the database, not email/userId supplied by a client.

Only an active verified account with a local hash receives reset mail. Unknown,
inactive and Google-only accounts receive the same202 challenge response and
response floor, without acquiring a local password. Successful proof updates
the hash, increments auth_version and consumes/invalidates reset challenges
in one transaction. Old sessions fail on their next request; requests already
authorized before commit are not retroactively canceled. No auto-login occurs.

The password-change notification is sent after commit and outside database
locks. Notification failure does not undo the password change and is not
retried automatically. Requesting a code never clears login failures or logs
out users; only successful reset clears the canonical email's login limit.

Real-mail/Inbox testing and production rollout still require separate approval.
Preserve environment values, demo accounts and all order data. Deploy/reset
verification must include two existing sessions, old-password rejection,
new-password login and replay rejection.

## Google sign-in (SF-146)

Use a Google OAuth client of type **Web application**. Set only the backend
environment variables `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`; production
Compose forwards them explicitly. Keep workspace and production `.env` separate
and mode 600. Never add these secrets to Vite variables, screenshots, PRs or logs.
Missing either variable disables Google start/callback with 503 without blocking
catalog or existing demo/password login. Startup does not fetch Google metadata.

Google Console checklist for the owner (this change does not edit Console):

1. Branding: app name ShopFlow; user support/contact
   `hoangchieunguyentuan@gmail.com`. Verify ownership for `hoangtuan.dev` and
   `phamthuy.dev`; use those root domains in Authorized domains when requested.
2. Audience: keep Testing until the owner is ready to publish; add the Google
   accounts used for acceptance as test users. Supply real homepage/privacy/terms
   pages when required by Console; do not invent unavailable policy URLs.
3. Data access: only `openid`, `email`, `profile`. No Gmail, Drive, offline
   access or refresh-token persistence.
4. Web client Authorized redirect URIs must match exactly:
   - `https://shopflow.hoangtuan.dev/api/login/oauth2/code/google`
   - `https://shopflow.phamthuy.dev/api/login/oauth2/code/google`
5. For local development, register an explicit URI on a development client:
   `http://localhost:5173/api/login/oauth2/code/google` or
   `http://127.0.0.1:5173/api/login/oauth2/code/google`. Use the same hostname
   throughout. These hosts are disabled under the production `staging` profile.
   No Google JavaScript SDK is used; there is no frontend client secret.

Public authorization start is `/api/oauth2/authorization/google`, with optional
`returnTo=/customer` or `/customer?checkout=1`. Caddy strips `/api`; backend
start/callback mappings omit it. Caddy pins Host/forwarded host/scheme and
removes incoming Forwarded/prefix headers. The backend uses a fixed host→origin
map, ignores arbitrary forwarded redirects and binds the callback to its start
origin. Do not enable arbitrary framework forwarded-header inference.

Session cookie is HttpOnly, Secure in production and SameSite=Lax for the
cross-site GET callback. All business writes retain CSRF checks. After login
or linking the session rotates; frontend GET `/auth/session` boots a fresh
XSRF cookie before a write. Google tokens are validated by Spring Security,
never persisted as authorized clients and never exposed to browser storage.
State/nonce/PKCE are session-bound; the flow expires after 5 minutes.

A new Google identity creates CUSTOMER without a password. A returning subject
keeps its existing account/email/role even if Google reports a different email.
A local email collision produces a five-minute pending proof in the server
session, without logging in; POST `/auth/google/link` accepts only the existing
password and requires CSRF. Successful password proof links atomically and
rotates the session. Inactive users, a different Google subject, expired/replayed
proof and a password reset since the callback cannot be linked.

Before production acceptance, use each public domain separately: Google login
→ internal `/auth/session` → first CSRF write → logout; also test cancellation,
local collision/link and account switching. Verify Secure/HttpOnly/Lax and no
redirect to HTTP/backend/unknown hosts. Google live consent and public-domain
proxy/cookie acceptance require the owner's account and rollout approval; fake
OIDC tests are not live Google evidence. Cart persistence is SF-147, and order
ownership/checkout enforcement is SF-148; this change does not claim those done.

References: [Spring OAuth2 Login](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/login/core.html),
[Google OIDC](https://developers.google.com/identity/openid-connect/openid-connect).

## Browser cart persistence (SF-147)

No new backend configuration, schema or service. Browser key `shopflow.cart.v1`
stores version1 plus product ID/quantity pairs only, bounded to64 KiB. Each
origin has its own cart. Product display is reloaded via `/products/{id}`, never
from stored names/prices. Unknown/unavailable/out-of-stock items prevent checkout
until removal or successful refresh. API checkout remains authoritative.

Blocked/quota-limited storage falls back to RAM with a visible warning, including
on the login page before Google navigation. An older snapshot is removed after
a failed save so reload cannot silently restore different items. Do not promise
reload/OAuth durability when the browser refuses storage. No shipping PII, user
identity, credentials, proof or orderRef is persisted.

Logout/account change clears checkout form/order/payment state while preserving
the unplaced cart. Only the existing explicit start-over flow clears the cart;
failed/pending order requests leave it intact. Return intent opens the restored
form without posting an order. Login gating/owner enforcement comes in SF-148.

## Authenticated checkout cutover (SF-148)

Requires V7 and the reviewed SF-142–147 prerequisites. No new environment
variable or migration. POST orders/payments now require a valid internal account
and CSRF. All demo/staff/customer roles can buy; payment belongs exclusively to
the creating account, with no shop-owner override. Owner comes from the session,
not request fields or shipping email. API response shapes are retained.

Account row locking prevents deactivation/deletion during the transaction;
checkout/payment transactions for the same account serialize. Existing sorted
stock locks and atomic reserve/release remain. No load benchmark was run.

Before approving a release, inventory legacy PENDING_PAYMENT orders (owner null)
and their reserved items. They remain unchanged but cannot pay through storefront.
Do not claim by email, cancel, release stock or change migrations to make them
pass. Prepare a restorable backup and any operational decision separately.

Smoke after an approved rollout: guest browse/add works; guest order/payment401
with CSRF; each role/demo can buy/pay its own order; another account and legacy
order receive generic404 with no reserve/movement changes; first write after
login has real cookie CSRF; 401 mid-checkout keeps cart/intent without auto-submit.
Local/CI evidence is in `docs/qa-checkout-ownership.md`; public-provider/domain
acceptance and final rollout readiness are SF-149.

## Customer authentication release gates (SF-149)

Use [the integration matrix and reviewed backup/rollback checklist](qa-customer-authentication.md)
before a release. Source integration and disposable restore checks are complete;
live Google/Inbox and production backup/approval remain separate gates. Do not
interpret .env presence or a synthetic invalid-code response as live acceptance.

At the 2026-11-06 read-only baseline, production was V6; the running backend
lacked the five new auth/mail variables despite both env files being ready.
The existing main workflow validates applied checksums with pending migrations
allowed, then recreates Compose services; backend startup applies V7. A reviewed
backup must precede that approved action. Do not manually migrate or restore
production as part of a read-only readiness check.

Existing legacy pending orders must retain null owners/reservations. An old app
may still fit the additive schema but reopens guest payment; rollback requires
an approved checkout maintenance block/forward-fix plan, not blind DB restore.
No old unrelated Jira issue is completed by this auth rollout.

### Transactional email appearance and language

Registration, reset OTP and password-change emails use the existing ShopFlow PNG
logo rendered into an opaque white PNG banner embedded as a CID attachment,
with a text alternative when images are hidden. The banner needs no public image URL.
The website sends its current `en` or `vi` selection in `Accept-Language` for each
mail-producing request, including resends and reset confirmation. Spring's request
locale selects a single language for subject, HTML and plain text; Vietnamese is
used for `vi`, English for other locales. This does not change the sender avatar,
OTP lifetime, proof binding or resend limits.
