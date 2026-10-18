# Product advisory chat QA — SF-76 / SF-100

Verified 2026-10-18 on feature/SF-76-product-advisory-chat, based on develop
ec191cc. Contract was written before code:
[chat API](api-product-advisory-chat-spec.md). No production deployment is implied.

## Backend scenarios

| Requirement | Evidence | Result |
| --- | --- | --- |
| Real, active, candidate-whitelisted product cards; current database facts | ChatControllerTests.returnsOnlyActiveWhitelistedIdsAndCurrentDatabaseFacts | Pass, real test catalog/database, mocked retrieval/model; includes an active product outside candidate set so final hydration alone cannot satisfy the test |
| Primary success has no fallback; primary fails then fallback once; both fail sanitized 503; catalog remains usable | ChatControllerTests.fallsBackOnceAndReturns503IfBothModelsFail and primary verification in whitelist test | Pass |
| Empty suggestions, six-message history, question trim | ChatControllerTests.acceptsEmptySuggestionsAndTrimsHistoryToSixMessages | Pass |
| Invalid/null/oversized question/history and client system-role injection | ChatControllerTests.rejectsInvalidInputsBeforeExternalWork | Pass before external interactions |
| Guest and all roles; CSRF required before model calls | ChatControllerTests.allowsAllRolesAndGuestsButRequiresCsrf; AuthorizationTests.keepsGuestShoppingAndHealthChecksOpen | Pass |
| Missing key fails before retrieval; score floor removes low-score candidate | ChatControllerTests.missingKeyFailsBeforeRetrieval; removesCandidatesBelowFloorAndDoesNotInventProducts | Pass |
| Atomic cap and Vietnam-midnight reset | ChatQuotaGuardTests.atomicallyCapsAttemptsAndResetsAtVietnamMidnight | Exactly 50 admitted from 100 concurrent attempts; Retry-After 60s before midnight, next day admitted |
| Quota prevents embedding/generation; fallback counts as another attempt | ChatQuotaControllerTests.exhaustedQuotaReturnsRetryAfterWithoutEmbeddingOrGeneration; ChatServiceQuotaTests.fallbackConsumesQuotaAndCannotExceedTheCap | Pass; 429 header present; no post-cap calls |
| Fallback may use remaining time beyond primary 6s | ChatServiceQuotaTests.fallbackCanUseRemainingTimeBeyondThePrimarySixSecondLimit | Pass, fallback at most 10s and bounded by pipeline remaining time |
| Both model requests send owner prompt, Minimal, 0.2 and JSON schema; ignore thought text | GemmaClientTests.sendsMinimalTemperatureSchemaAndOwnerPromptForBothModels | Pass, mocked HTTP wire assertions, not model-quality evidence |
| Invalid/truncated/noninteger-ID model output | GemmaClientTests.rejectsMalformedSchemaTruncatedResponsesAndUpstreamFailures | Pass, fails closed |
| Actual HTTP read timeout | GemmaClientTests.boundsRealHttpRequestsByReadTimeout | Local server stalls; short read timeout maps to sanitized unavailable error |
| Model price prose cannot override server cards | GemmaClientTests.rejectsFreeFormPricesButAllowsMachineModelNumbers | Numeric currency/unit claims rejected, 5500-2 machine code allowed; same single-fallback policy |

Whitelist mutation: temporarily remove `.filter(allowed::contains)`, run
`ChatControllerTests#returnsOnlyActiveWhitelistedIdsAndCurrentDatabaseFacts`.
The test failed because active-but-unretrieved product 80004 entered the response.
Restore original bytes, rerun the controller tests: all seven passed. Final full
suite also verifies restored logic. No mutation is retained.

## Browser scenarios

`frontend/e2e/chat.spec.ts` mocks chat, catalog and session. It verifies:

- Guest request shape, product price/stock and navigation into existing detail dialog.
- Eager chat thumbnails, preserving the ProductImage default for other callers.
- Only six successful messages sent; long reply context bounded to 2000 characters;
  reload clears temporary conversation.
- 503 retry sends identical question/history, with one user bubble; closing chat
  preserves storefront/cart behavior.
- 429 Retry-After blocks repeat sends; 403 explains token refresh; malformed price
  response is rejected; HTML-like prose remains escaped text.
- Loading prevents duplicate sends, blank submit disabled, input maxlength 500,
  Escape restores trigger focus.
- Mobile trigger does not cover cart bar; checkout remains reachable.
- Vietnamese geometry at 360/390/1440: no horizontal overflow and input/send visible.

The existing full suite additionally covers catalog pagination/search, login,
dashboard, image management, receiving, delivery and returns. Browser mocks cannot
prove live model language, compatibility, availability or factual accuracy.

## Live exploratory observations

The optional ChatExternalTests uses a fixture of 15 questions against a disposable
loopback Java backend. For every 200 response it checks each returned product
against GET /products/{id}, including name, price, stock and image URL. It records
free-form replies as observations rather than claiming a deterministic quality gate.
It is tagged external, excluded from normal Maven/CI and requires an explicit
CHAT_QA_BASE_URL pointing at 127.0.0.1. No AI secret is passed to Maven.

The first run (before final prompt/timeout refinements) returned 8 HTTP 200 and
7 HTTP 503. Upstream tracing through a local QA-only proxy showed Google HTTP
500, delayed primary responses and shared HTTP 429 affecting both model choices.
All 15 embedding calls were 200. These failures are not local daily-cap 429s.
See [exploratory upstream trace](qa/sf-76/exploratory-upstream-trace.json) and
[recorded observations](qa/sf-76/live-evidence.json).

Specific issues observed and addressed:

- A fallback returned 200 after 6.2s but the original 6s read limit discarded it.
  Fallback now may use up to 10s of the remaining 18s budget; primary stays 6s.
- One English question received Vietnamese; the final system prompt explicitly
  prioritizes QUESTION language over the language of catalog/system/history.
  A subsequent real Minimal/0.2 English probe returned English JSON successfully:
  [probe](qa/sf-76/language-refinement-probe.json).
- The model restated a price incorrectly in free prose while cards had correct DB
  prices. Final prompt directs prices/stock to cards; numeric currency/unit claims
  in model reply are now rejected as invalid output. Plain machine codes are allowed.
- A follow-up observation contained malformed Vietnamese words. Prompt reinforces
  grammatical language, but no claim is made that all prose errors are eliminated.

Final production-build frontend + packaged Java + disposable PostgreSQL 17 +
Qdrant + real model smoke passed at 1440/390/360 and is recorded in live-evidence.json
and three screenshots. In that final run, primary returned Google 500 (0.78s),
fallback returned 200 (5.17s); prose contained no money amounts and both cards
matched catalog data. Mobile thumbnails were visible; the desktop screenshot
caught thumbnails before async image decoding completed.
The 90 vectors were first generated through real SF-75 indexing, then reused only
inside disposable QA to avoid repeatedly spending embedding quota during harness
fixes. No production index, database or R2 object was modified. The QA proxy logs
only provider path/status/duration, not API-key headers or input transcripts.

Earlier private harness runs needed fixes for chunked request forwarding, a Python
module-name collision and reading a Playwright response after closing the browser.
These were QA-harness errors, not application changes. Final verification uses the
corrected harness. Private scripts/logs under /tmp/sf76-* are not runtime dependencies.

## Commands and results

- `bash backend/mvnw -f backend/pom.xml -B spotless:apply clean verify`: final full
  174 passed, zero failures/errors/skips; external-tagged tests are excluded and no AI key needed.
- `bash backend/mvnw -f backend/pom.xml -B spotless:check`: passed.
- `npm --prefix frontend run lint:ci`: passed.
- `npm --prefix frontend run build`: passed, includes type-check. Existing VueUse
  PURE-annotation warnings remain non-blocking; no dependencies were upgraded.
- Production-build Playwright run: **65 passed** (51.5s), two workers, no retries.
  Temporary config spread the repository config, changed baseURL/webServer to
  Vite preview on 127.0.0.1:4174 and workers to 2; no source config retained.
  Command: `npm --prefix frontend run test:e2e -- --config=playwright.sf76.local.config.ts`.
  To reproduce without a temporary config, start `npm --prefix frontend run preview -- --host 127.0.0.1 --port 4173`
  and run `npm --prefix frontend run test:e2e -- --workers=2`; existing config reuses that server.
- After the font startup fix: `npm --prefix frontend run test:e2e` on the normal
  Vite dev-server: **67 passed (1.2m)**, default workers, no retries.
- Earlier full browser runs had blank development-page loads in chat/dashboard and
  a login redirect case. Focused three-repeat checks passed (6 + 6 checks); the full
  suite passed at 64 tests before the last image test. The final Vite dev-server
  parallel run had 63/65 pass (chat long-history + dashboard blank loads); a serial
  run also hit a blank chat page. Production preview passed all 65. Root cause of
  the intermittent development-server blank navigation is not established; it is
  not retrospectively assigned to a single cause. Subsequent diagnosis reproduced
  blocking startup when Google Fonts CSS stalls; fonts are now served locally.
  See [startup reproducer and fix](qa-storefront-startup.md). No existing
  retry/timeout settings or Playwright config changed.
- `python3 scripts/test-deploy-stdin.py`: four passed.
- `python3 scripts/test-deploy-tls.py`: four passed.
- Production Compose configuration validated with dummy secrets; no public Qdrant
  ports or frontend AI key. Local Markdown/JSON links and staged whitespace checked.

## Screenshots

![Desktop product assistant](qa/sf-76/chat-1440.png)

[390px mobile](qa/sf-76/chat-390.png) · [360px mobile](qa/sf-76/chat-360.png)

## Traceability and scope limits

SF-76 / FR-09 → SF-97 API contract/SRS, SF-98 backend retrieval/model/quota,
SF-99 responsive UI, SF-100 offline/browser/live evidence. SF-75 provides the index.

Hard guarantees tested: response card IDs are active and whitelisted; card facts
come from the catalog; local quota blocks excess attempts; CSRF and input bounds
hold. Similarity is not compatibility proof. Free-form replies can contain mistakes
or misleading names/codes, even when cards are correct. Numeric-currency validation
is a targeted guard, not a natural-language fact checker. Catalog demo descriptions
also need shop verification before commercial deployment. No 100% hallucination-free
or relevance claim is made from 15 exploratory prompts.

No native-device/virtual-keyboard or concurrent-user load test. No streaming,
transcript persistence, answer cache, new IP maps, upload/image understanding,
automatic web search or Cloudflare rate-limit changes. In-memory quota resets on
restart and does not enforce a billing cap across replicas. Provider quota/key
errors can defeat both primary and fallback. Network deadline is not a hard SLA
for an unhealthy local database. Model availability must be reviewed before release.

No commit, push, PR, merge or production deployment was performed for SF-76 during
implementation. Configure QDRANT_API_KEY and follow changelog/release/tag-signing
approval steps separately before promoting this feature to production.
