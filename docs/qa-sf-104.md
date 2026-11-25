# Persistent HTTP sessions — SF-104

## Scope

Spring Session JDBC persists servlet sessions in PostgreSQL. Flyway V10 creates
its tables in `shopflow`; the default `SESSION` cookie is host-only. Production
retains Secure, HttpOnly, SameSite=Lax and the eight-hour idle timeout. The first
rollout cannot migrate servlet-container sessions; users must sign in again.
The session principal omits the password hash. PostgreSQL availability is
required for session operations. Rollback and retained session-row considerations
are documented in [deployment](deployment.md#persistent-http-sessions-sf-104).

## Verification

- `PersistentSessionTests`: PostgreSQL 17 Testcontainers and real HTTP test
  login, separate JVM backend restart with the same database, session/role,
  CSRF write, logout and replay, credential-version invalidation, session
  timeout, cookie flags, Google authorization state across application restart,
  and registration OTP across restart with a local fake email server. Seven
  cases passed in the final combined run. A separate eighth case injected a
  simulated pending Google-link proof into JDBC, restarted the application,
  linked the account over HTTP and verified session rotation; it passed. The
  test checks that a password hash is absent from serialized session attributes.
- The regular `test` profile excludes JDBC session auto-configuration because
  existing MockMvc auth/Google/OTP tests intentionally use `MockHttpSession`.
  `PersistentSessionTests` explicitly enables JDBC and exercises real servlet
  HTTP and PostgreSQL instead. The mocked suites are not evidence of real
  external Google or email delivery.
- `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify`:
  269 tests passed, with no failures/errors/skips. This run preceded the final
  expiry, customer-session and successful inventory-write assertions; those
  passed in the later seven-case focused session suite. The Google-link case
  was added and passed separately after that run.
- Focused legacy auth/registration, Google, migration and backup/restore
  regression: 48 tests passed before the final additions to session tests.
  An earlier full run failed because those mock-session tests were running with
  the JDBC filter and two migration assertions assumed V9. Those incompatibilities
  were corrected before the focused rerun; no production data was used. One
  expiry test initially changed only the cleanup timestamp and failed; the final
  test also sets last-access time to simulate expiration, and passed.
- Frontend lint and build passed after `npm ci` restored the lockfile dependencies.
  Full Playwright with no retries: 139 passed and two timed out waiting for page
  elements. Both passed in a separate focused, one-worker run with no retries;
  the original failures remain unexplained. Browser APIs were mocked.
- `python3 scripts/test-deploy-stdin.py`: four passed;
  `python3 scripts/test-deploy-tls.py`: four passed. The disposable Flyway
  preflight regression passed with V10 and still rejected a missing V8 and a
  modified migration checksum. The migration-validator image built successfully;
  its JAR contained the V8 Java class and V10 SQL migration.

## Boundaries

No production deploy, migration, R2 update, live Google login or real email was
performed. OAuth cancellation and email delivery use local simulated providers;
full provider consent/linking on both public domains remains manual acceptance.
Session persistence does not make other application in-memory state shared across
multiple backend instances. The `test` profile and JDBC integration checks have
different session configurations as described above.
