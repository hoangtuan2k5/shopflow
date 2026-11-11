# Customer authentication integration and rollout — SF-149

## Acceptance update — 2026-11-10 (SF-149)

This section supersedes the pre-rollout status below. Evidence dates are retained;
this documentation update does not claim a fresh production test run.

- Released: [v0.14.2](https://github.com/hoangtuan2k5/shopflow/releases/tag/v0.14.2),
  main `3858a67f3057ef26b3aeae9117a38bc263faf0b7`. Release publication,
  successful main CI and the deployed checkout SHA were rechecked on 2026-11-10.
- [Release CI](https://github.com/hoangtuan2k5/shopflow/actions/runs/42609245671)
  completed successfully: 260 backend and 141 browser cases. These are release
  results, not tests rerun for this documentation-only change.
- Owner-confirmed manual acceptance: Google login and OTP-based initial password
  setup both work. SF-146 and SF-155 are Done. The owner did not provide a
  per-domain or new/returning/collision/link scenario breakdown; those details
  must not be inferred from the confirmation.

| Area | Result and evidence boundary |
| --- | --- |
| Live email registration | PASS on both public domains, EN/VI, using two dedicated QA accounts and actual application/API/DB requests on 2026-11-09 |
| OTP and email transport | PASS using actual application messages and Resend official test recipients; provider API supplied the OTP in memory. This does not establish personal Inbox placement |
| Recovery and password change | PASS live: incorrect proof rejected; correct proof changes credentials, rejects the old password, revokes old sessions on both domains and does not auto-login |
| Session and CSRF | PASS live: Secure/HttpOnly/SameSite=Lax session cookie, actual CSRF cookie/header on writes; private endpoints return401 after revocation/logout |
| Account privacy and cart | PASS live: account A/B separation, private cache cleared, cart retained; storage contains only product IDs/quantities |
| Demo roles and menus | PASS live: customer/warehouse/owner on each domain at320/1280px, role capabilities, Escape/focus and logout; demo credentials unchanged |
| Google public redirects | PASS agent browser: both-domain HTTPS callbacks, PKCE S256, state/nonce, openid/email/profile scopes and cancellation preserving the cart |
| Google login and initial password | PASS owner manual confirmation; agent did not perform Google sign-in/consent. Initial-password UI/backend security additionally passed controlled tests |
| Google rejection/link/race cases | PASS automated fake-provider suite with the real Spring token decoder; detailed public-provider collision/link scenarios were not separately observed |
| Checkout and historical orders | Live checkout prefill and isolated form edits passed. No production order/payment was submitted; ownership and order snapshots rely on existing integration/release CI |
| Backup and cleanup | Restricted pre-QA V9 dump restored to isolated PostgreSQL17 with matching table digests. Two QA users/challenges removed; all pre-existing application-table digests unchanged; rate limits preserved |
| Rollout and schema | Production is v0.14.2 with V9, rather than the historical V6/not-deployed baseline. The SQL/Java migration-validator correction shipped before this release |

### Focused review evidence — 2026-11-09

- `bash backend/mvnw -f backend/pom.xml -B -Dtest=GoogleLoginTests,GoogleDisabledTests,AccountSelfServiceTests,PasswordRecoveryTests,ApiContractTests test`
  —36 passed, no failures/errors/skips; disposable PostgreSQL and fake OIDC/email.
- `npm --prefix frontend run lint:ci` —PASS.
- `CI=1 npm --prefix frontend run test:e2e -- e2e/google-login.spec.ts e2e/account-menu.spec.ts e2e/password-guidance.spec.ts --retries=0 --reporter=list --workers=2`
  —24 passed without retries. UI APIs are mocked. A fixture product lookup logged
  a refused localhost backend connection; this is not live API evidence.
- Local evidence: `/tmp/shopflow-auth-acceptance/acceptance-summary.md`,
  `review-follow-up.md`, `review-backend.log`, `review-browser.log`,
  `run-bad270c819/report.json`, `google-report.json`, `menu-report.json`.
  Temporary artifacts are local working evidence, not durable public attachments;
  Jira acceptance comments retain the results and provenance.

### Remaining boundaries and rollback

Public Google consent was owner-accepted, not agent-run. Personal Inbox placement,
Gmail/Outlook font rendering, a new production order/payment and full application
rollback were not rechecked. WNC health was not part of the latest auth QA; its
older timeout below is historical, not a fresh incident report. Runtime OpenAPI
is disabled under the staging profile; contract verification used source,
ApiContractTests and real account API responses.

No env values, Google tokens, OTPs, passwords or customer PII belong in this
report. Keep Resend, current scopes and configuration unchanged. Before a future
rollout, take a new restricted backup and verify restore; never restore an old
backup over newer orders. Application rollback does not undo credential changes.
In-memory sessions still end on backend restart; persistence remains SF-104.
SF-102/SF-103 and architecture issues remain outside this closeout.

See [account self-service acceptance](qa-account-self-service.md) for SF-156.
Documentation closeout must be reviewed and merged before SF-149 is marked Done.

## Historical candidate evidence — 2026-11-06

Everything below records the original candidate and its then-current production
baseline. Statements such as NOT READY, NOT DEPLOYED, V6 and NOT RUN are historical
and are superseded by the dated acceptance update above.

**Checked:** 2026-11-06 UTC. Candidate base `e062d18` on develop after PR #151.
Branch `feature/SF-149-customer-auth-integration-readiness`.

**Release status: NOT READY.** Source and
local/CI checks pass; live Google consent on both public domains, real Inbox
acceptance, production backup and rollout approval are NOT RUN. No release,
production migration, container recreation, DNS/Console edit or email was sent.

## Evidence matrix

| Area | Result | Environment / evidence |
| --- | --- | --- |
| Full backend | PASS | Java21, wrapper, PostgreSQL18.4/H2; `spotless:check clean verify`:241 tests, 0 failures/errors/skips; `/tmp/sf149-full-backend.log` |
| Frontend build/lint | PASS | `npm --prefix frontend run build`, `lint:ci`; zero lint warnings/errors; existing dependency PURE annotation warning remains |
| Browser CI-mode | PASS | `CI=1 npm --prefix frontend run test:e2e`:93 passed, no failed/flaky/retry result; `/tmp/sf149-browser.log` |
| Signup/email login/demo | PASS automated | Registration/Auth/Credentials suites; fixed CUSTOMER, email proof, Unicode/72-byte composition and legacy demo paths |
| Reset/OTP/session revocation | PASS automated | Ten-minute single-use proof, purpose/session/generation, failure budgets, resend/races; two old sessions revoked only after reset; no Google-only password creation |
| OIDC/linking | PASS fake provider | Real Spring token exchange/RSA decoder with loopback issuer/JWKS; subject identity, collision, password proof/version/session, race and rejection coverage |
| Google public-domain consent | NOT RUN | Needs owner account/Console settings and an approved candidate rollout; no mock result substitutes for this |
| Cart/privacy | PASS automated | Login/reload/full-page cancel, ID outside first page, current API prices, storage failure/size/quantity boundaries and 401 private-state clearing |
| Checkout/ownership | PASS automated | All three roles/demo buy/pay only their own order; generic404 before status disclosure; legacy/injection/guest/no-effect cases |
| Real cookie/HTTP integration | PASS localhost | SF-148 real browser + Spring Boot + PostgreSQL17; owner FK, first CSRF write, other-SHOP_OWNER404 and simulated payment/logout; see [QA](qa-checkout-ownership.md) |
| Secure/Lax through public proxy | NOT RUN | Local HTTP proved HttpOnly/Lax, not production Secure or cross-site Google callback cookies |
| V6/V7 backup/restore | PASS disposable DB | New CustomerAuthRolloutTests runs pg_dump/pg_restore before/after V7, validates Flyway and compares every application-table digest |
| Old producer schema compatibility | PASS limited | Old-shape order insert fits V7 and rolls back; this is not a full old-application rollback test or authorization equivalence |
| Env files/candidate Compose | PASS read-only | Both files mode600, five values present; HMAC decodes ≥32 bytes and differs between environments; provider settings match; candidate Compose forwards all five |
| Current production auth env | NOT DEPLOYED | Running backend lacks all five new mail/Google/HMAC variables; files alone do not update a container |
| Resend domain/DNS | PASS read-only | API200, target domain verified; DKIM TXT and SPF MX/TXT verified; DNS SPF/DKIM/MX/DMARC present |
| Real email/Inbox | NOT RUN | No approved recipient/send; API/domain verification does not prove delivery or Inbox placement |
| Google credential smoke | LIMITED | Official token endpoint returns400 invalid_grant for intentionally invalid code, not invalid_client; no valid code, token or live login tested |
| Existing ShopFlow health | PASS baseline | Deploy-equivalent TLS curl to both /healthz endpoints returned200; current backend/frontend/Postgres containers healthy |
| WNC baseline | NOT RUN / timeout | Existing WNC homepages timed out with10-second curl before auth rollout; no routing change was made |

The OpenAPI snapshot still contains27 paths/46 schemas. SF-149 does not rewrite
it. Architecture, legacy snapshot/price/reservation and operational role checks
passed in the fresh suite. `python3 scripts/test-deploy-tls.py` and
`python3 scripts/test-deploy-stdin.py` passed4 tests each. The final focused
restore/FK check passed after the full241-test run; CI repeats the final test.

## Migration and restore rehearsal

`CustomerAuthRolloutTests` uses disposable PostgreSQL17, never production:

1. Flyway to V6; add a legacy PENDING_PAYMENT order, snapshot item and reservation.
2. pg_dump custom archive → fresh database → pg_restore; compare application data.
3. Migrate original and restored V6 to V7; Flyway validate; compare old data,
   demo hashes/roles, customer snapshots, stock/movements and null legacy owner.
4. Exercise one old-producer insert inside a rolled-back transaction.
5. Add local and Google-only users plus an owned order; dump/restore V7; compare
   all application-table digests, owner IDs, null credentials and order sequence. A final focused run also
   rejects an orphan owner FK after restore and confirms the data snapshot stays
   unchanged.

Focused rehearsal passed (V7 132ms); the fresh full-suite rehearsal passed
(V7 89ms); final focused FK check passed (125ms). These timings describe tiny
test fixtures, not production lock time.
V7 ALTER TABLE/constraint validation can hold locks on users/orders. Dumps are
inside the disposable container and are removed with it; no real-data dump was
created. Commands are implemented in the test class and repeat in normal CI.

## Provider/config baseline

Read-only probe script `/tmp/sf149-readiness-probe.py` parsed approved values in
memory; output `/tmp/sf149-readiness-sanitized.json` contains booleans/statuses,
not key values, client IDs/secrets, HMAC, tokens, cookies or personal addresses.
Resend GET domains/domain returned200 and verified target records. Google used
one intentionally invalid code, returning invalid_grant; this cannot establish
successful client authentication/consent/callback behavior. No token was issued.

TXT/MX checks used dig. Resend verification and DNS presence are not real mail
acceptance. Provider references: [Resend domains](https://resend.com/docs/dashboard/domains/introduction),
[Resend error distinctions](https://resend.com/docs/api-reference/errors),
[Google web OAuth errors](https://developers.google.com/identity/protocols/oauth2/web-server).
No Cloudflare Email Sending/SMTP service or DNS settings were enabled; Resend
remains the chosen sender integration.

The two env files were read separately, never copied over one another. Existing
DB/R2/Qdrant values were not modified. Candidate Compose was rendered in memory;
current runtime inspection printed only presence flags. All five new variables
are absent from the current backend container, so a reviewed image/Compose
rollout and recreation are required even though .env is ready.

## Production data baseline — read only

The current database is V6 with no owner column. Aggregate check at2026-11-06:
8 legacy PENDING_PAYMENT orders contain11 units; total inventory reserved is71
across all statuses. The latter is not attributed solely to pending orders.
No snapshots, addresses, emails, orderRefs or hashes were exported. Counts are
in `/tmp/sf149-production-baseline.json` and must be refreshed before cutover.

The approved policy keeps these orders/data/reservations, adds null owners and
blocks their storefront payment. Do not backfill by email, auto-cancel or release.
A separate operational action would need a concrete owner-approved scope.

Public health is only baseline evidence for the existing stack. Exact curl
style used by deployment returned200 for both ShopFlow hosts; Python urllib
returned403 from Cloudflare. This client-dependent difference was not changed
or treated as an application regression. WNC probes timed out; their Caddy
route blocks were compared byte-for-byte with origin/main and are unchanged;
their live status must be checked during approved rollout smoke.

## Concrete rollout gates

1. Owner reviews source PRs/evidence and chooses the release/test environment.
   Suggested feature release is v0.13.0; this is a proposal; this work has not created a version/tag.
2. Before production-changing actions, create a mode600 backup outside Git and
   verify the archive; retain the prior image/commit and inventory baseline.
   Production backup is NOT RUN. The disposable restore test is not that backup.
3. Confirm Google Web client testing users/branding and exact /api callbacks on
   both domains; support/contact remains the approved one in the contract.
4. Owner explicitly approves a real test recipient before any Resend send.
5. Prepare/review release notes and a develop→main PR. Release uses a merge
   commit, never squash; owner signs the tag. No direct push to either branch.
6. Only after explicit release/deploy approval run the established main workflow.
   It validates applied migrations while allowing pending ones, then Compose
   rebuilds/recreates; backend Flyway applies V7 at startup. Do not run an
   unapproved manual migrate, clean, checksum repair or volume/schema deletion.
7. Verify first real cookie/CSRF write, signup/Inbox/reset/two old sessions,
   Google new/returning/collision/link/cancel on each origin, owned checkout,
   other-owner/legacy rejection, demo/ops, catalog/R2/chat/dashboard and WNC.
   Confirm no HTTP/backend-host redirect, secret in URL, lost cart or auto-POST.

After gates pass, update this matrix with timestamps/results; only then declare
release-ready and close the live-acceptance work. SF-146 and SF-149 stay In Review
while those checks remain missing. Do not change old unrelated Jira statuses.

## Backup and rollback review

Example backup commands for an approved maintenance step (not executed):

```sh
cd /opt/shopflow
install -d -m 700 /opt/shopflow-backups
umask 077
sf149_backup="/opt/shopflow-backups/pre-customer-auth-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker compose --env-file .env -f docker-compose.prod.yml exec -T postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' </dev/null > "$sf149_backup"
docker compose --env-file .env -f docker-compose.prod.yml exec -T postgres pg_restore --list < "$sf149_backup" >/dev/null
```

Do not restore a stale database over post-cutover sales automatically. V7 is
additive and old SQL fits, but an old application reopens capability-only guest
payment and lacks email/Google semantics. A code rollback therefore needs an
approved checkout block/maintenance plan or a forward fix; schema compatibility
alone is not a safe authorization rollback. Full old-application rollback was
NOT RUN. Session persistence remains SF-104: restarting backend logs users out.
