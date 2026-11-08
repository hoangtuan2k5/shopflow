# Account self-service QA — SF-156

Candidate branch: `feature/SF-140-account-self-service`, based on develop `7cd1f31`.
Scope: SF-150–SF-156; contract: [account self-service](api-account-self-service-spec.md).
This is local candidate evidence, not production or real Google/Inbox acceptance.

## Evidence

| Check | Result |
| --- | --- |
| Account capabilities, ownership, demo protection | AccountSelfServiceTests checks private profile data, verified local/Google-only capabilities and demo restrictions |
| Credential proof and revocation | Actual Spring Security cookie/header CSRF in MockMvc; wrong password, forged fields, Origin/CSRF rejection, failed-attempt limit and stale sessions |
| Setup OTP isolation | Authenticated account + session + SET_PASSWORD purpose + issued auth_version; public resend/reset cannot consume setup proof |
| OTP lifecycle | 10-minute expiry, five wrong attempts, resend cooldown, failed delivery, late provider callback and two concurrent confirmations |
| Delivery profile | One row per account; A cannot read/delete B, invalid fields rejected, save/delete and no historical backfill |
| Checkout | Saved address fills shipping fields; manual edits remain order-only; late response cannot overwrite edits; manual fallback on load failure |
| Privacy | Private query cache cleared on confirmed logout/401/sign-in; cart remains IDs/quantities; password, OTP and delivery PII absent from browser storage |
| Localization/accessibility | English/Vietnamese settings at 320/1280px; Escape returns focus, accessible menu actions, responsive staff header |
| OpenAPI |33 paths/51 schemas: six new account paths, five new schemas; all 27 old paths and 46 old schemas unchanged |
| PostgreSQL17 rehearsal | V7 backup/restore, V7→V9 and restored-V7→V9 validation; existing application-table digests, demo credentials, Google identity and pending OTP preserved; V9 backup/restore includes delivery profile and SET_PASSWORD issued version |

Test sessions are constructed principals for controlled account fixtures. Google OIDC validation remains covered by the existing Google suite; these fixtures do not prove a live Google sign-in.
Resend is mocked in account integration tests; the email client suite uses a local HTTP stub to inspect branded single-language payloads without sending customer mail.

## Commands and outcomes

Final run results are recorded below before PR creation:

- Broad backend run: `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify` —258 tests,257 passed; one old migration assertion expected V7 instead of the new V9. Corrected that expectation for both PostgreSQL and H2.
- Final corrected verification: `bash backend/mvnw -f backend/pom.xml -B spotless:apply verify -Dtest=CustomerIdentityMigrationTests,AccountSelfServiceTests,ApiContractTests,ResendEmailClientTests` —31 passed, no failures/errors/skips, JAR built. Includes real email/password login after Google-only setup. `spotless:check` passed afterward. CI repeats the complete suite on the final commit.
- Frontend: `npm --prefix frontend run lint:ci` and `npm --prefix frontend run build` —passed. Existing VueUse annotation warnings remain non-failing build output.
- Browser: `CI=1 npm --prefix frontend run test:e2e -- --retries=0 --reporter=list --trace=retain-on-failure` —112 passed, zero retries. After the final resend-error recovery correction, the account-menu suite passed all11 cases with zero retries; lint/build passed again. CI runs all113 final browser cases.
- Final setup UI checks cover Retry-After blocking,503 without claiming delivery and discarding the superseded challenge after a failed resend so a fresh request remains possible.
- Deploy helpers: `python3 scripts/test-deploy-tls.py` and `python3 scripts/test-deploy-stdin.py` —4 cases each.
- Initial broader run identified an unnamed CHECK portability issue in H2; V8 is now a Java migration that discovers only V7's purpose constraint before replacing it. V7 is unchanged. PostgreSQL/H2 are both required to pass the final run.
- Initial concurrent browser/backend runs failed when Docker Testcontainers changed host networks: retained traces show Chromium `net::ERR_NETWORK_CHANGED` while loading application JavaScript. Browser verification is repeated after backend containers stop, with unchanged timeouts/assertions; no network-error bypass is added to the application. The fresh CI-mode run owns its Vite server rather than reusing the terminated run's server, which produced connection-refused errors during an intermediate attempt.

Screenshots generated locally in `/tmp/shopflow-account-password-{en,vi}-{320,1280}.png` and `/tmp/shopflow-staff-account-menu-320.png`. These contain synthetic fixtures only. Screenshots are reviewed locally, not evidence of a production deployment.

## Release and rollback handoff

1. Review the shared PR into develop with backend/frontend/enhancement/documentation labels and at least one commit per SF issue. All CI must pass. Squash subject must retain `(#PR)`; body retains every SF key without Smart Commit commands.
2. Get owner approval for release promotion. Use a separate develop→main release PR with a merge commit. v0.14.0 notes are included; v0.13.0 tag/release stays unchanged.
3. Before production migration/recreation, take a restricted custom-format PostgreSQL backup and verify a disposable restore. Record current image/commit, Flyway status, application-table counts and reservation totals without exposing customer PII or secrets.
4. Deploy only after that approval. Migrate V8/V9, validate Flyway, check health on both storefront domains, then manually accept logout, default address, local password change and Google-only OTP setup with approved test accounts. Credential changes intentionally sign out all sessions on both domains.
5. No new environment variables, OAuth scopes or provider/DNS configuration are required. Existing Resend sender, website-selected language and OTP_HMAC_SECRET remain in use.
6. The previous application can coexist with the additive schema, but do not drop columns/tables or downgrade Flyway. Pause account writes before rollback and assess newly changed credentials/profile data. Never restore a stale database over new orders/payments; preserve current data and reservations. Reverting application code does not undo password changes.

Not run here: real Google redirect/Inbox acceptance on both public domains, production backup, migration, deployment, tag/signature or release publication. SF-146/SF-149 keep their existing manual-acceptance status.

## Post-release preflight correction (SF-156)

Release PR160 deployed main d6f06dc and V9 successfully; both-domain HTTP/demo/menu/logout/cart smoke checks passed and legacy application-table digests matched the fresh V7 backup.
A post-deploy repeat of the original SQL-only Flyway CLI validation failed because applied Java migration V8 was absent from its classpath. The running application was healthy; a later deploy would be blocked. v0.14.0 signing/publication is held pending the correction.
The correction builds a dedicated validator image with a JAR containing SQL and Java migrations, selects classpath:db/migration and builds that image before validation. No applied migration or database history is changed, and no missing-migration ignore/repair is used.
The new disposable PostgreSQL17 regression reproduces the old missing-V8 failure, validates the complete bundle and proves that a changed V7 SQL checksum remains rejected.

Correction verification: `docker build --target migration-validator` passed; the PostgreSQL17 SQL/Java preflight regression passed; deployment TLS and stdin helpers passed4 cases each. Read-only production validation using the actual built validator image and the candidate Compose definition passed all10 history entries (schema plus V1–V9). The temporary override/JAR were removed; production configuration was not patched. Commit/push/PR and deployment of the correction still require owner approval.

## Password guidance and OTP presentation follow-up (SF-156)

User-facing password hints omit byte/encoding terminology; registration, recovery, authenticated change and Google-only setup show a specific translated overlong-password message. The shared validator retains the existing Unicode composition and UTF-8 size boundary; the server policy is unchanged.
Verification emails keep the existing CID white logo banner, render the code as selectable40px bold centered text and remove resend/quota jargon from both HTML and plain text. Registration/reset/setup and English/Vietnamese wording remain distinct. Font preference uses Be Vietnam Pro via an optional web-font import, Arial/sans-serif fallback and an Outlook fallback style; actual client font support is not guaranteed.
Local verification:28 focused browser cases passed, including all four forms in both languages with overlong Unicode input and an accepted boundary value; frontend build/lint passed. ResendEmailClientTests:11 passed, covering purpose/locale copy, code layout, escaping, CID payload and transport behavior. Browser-rendered email previews at320/700px in both languages and with remote fonts blocked showed centered bold code and no horizontal overflow.
Registration uses the chosen display name; recovery, Google-only setup, resend and password-change notifications use the stored account display name. Names remain escaped in HTML; recovery/setup challenges retain a null pending registration name. RegistrationTests (13), PasswordRecoveryTests (9), AccountSelfServiceTests (13) and ResendEmailClientTests (11) passed.
Two owner-authorized sample emails were accepted by Resend and the owner approved their appearance. The later personalized production flow has not been deployed or accepted in a real Inbox; no additional email was sent for personalization. Preview artifacts use synthetic fixtures. Gmail/Outlook font behavior still requires client-specific acceptance. Font support reference:https://helpx.adobe.com/fonts/web/web-design-and-development/html-email.html.
