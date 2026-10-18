# Catalog vector index QA — SF-75 / SF-96

Verified 2026-10-18 on `feature/SF-75-catalog-vector-index`, based on develop
`40a233c`. Contract was written before implementation:
[API](api-catalog-index-spec.md). No UI or Playwright scope in this story.

## Automated scenarios

| Story requirement / scenario | Evidence | Result |
| --- | --- | --- |
| One embedding per active product; correct document/query format, dimensions and auth | `GeminiEmbeddingClientTests.formatsDocumentsAndDimensionsInsideEachBatchRequest`, `formatsSearchQueryWithoutDocumentPrefixOrTaskType` | Pass; HTTP mocked, not proof of Google API compatibility |
| Missing key / invalid embedding / upstream error | `rejectsMissingKeyBeforeAnyRequest`, `rejectsWrongDimensionsAndWrongBatchCount`, `hidesUpstreamBodyAndKeyOnFailure` | Pass, no external requests or body/key disclosure |
| Batch size 2 splits 3 products into 2/1; all upserts precede deletion | `CatalogIndexServiceTests.batchesUpsertsBeforeRemovingOnlyStaleIds` | Pass; mocked clients |
| Second Gemini batch or Qdrant upsert fails: no deletion | `neverDeletesAfterSecondBatchFails`, `neverDeletesAfterUpsertFails` | Pass; mocked clients |
| Empty active catalog clears old derived points | `emptyCatalogRemovesAllOldPointsWithoutEmbedding` | Pass |
| Scroll pagination, API key, stale delete IDs and dimension error guidance | `QdrantClientTests.scrollsAllPagesAndDeletesExplicitStaleIdsWithAuthentication`, `explainsDimensionMismatchWithoutPretendingTheIndexSucceeded` | Pass; mock REST |
| Create collection, repeat index without duplicates, update name, deactivate and hard-delete products, successful owner POST/GET | `CatalogIndexIntegrationTests.realQdrantReindexIsIdempotentAndRemovesInactiveAndDeletedProducts` | Pass; real Qdrant v1.18.3, test H2 catalog, synthetic 3-dimensional Gemini vectors |
| Anonymous 401, CUSTOMER 403, WAREHOUSE 403 on both endpoints | `AuthorizationTests` parameterized `protectedEndpoints` / `shopOwnerOnlyEndpoints` | Pass |
| Forbidden requests do not embed or change Qdrant points | `CatalogIndexIntegrationTests` same test, post-index forbidden calls | Pass; Gemini has zero interactions; real point IDs unchanged |
| Missing CSRF on POST | `AuthorizationTests.rejectsStateChangingRequestsThatCarryNoCsrfToken`; manual HTTP smoke | Pass, 403 |
| Missing key returns 503 before Qdrant calls; invalid q/limit returns 400 | `CatalogIndexControllerTests.missingKeyReturns503BeforeQdrantIsTouched`, `invalidQueriesReturn400WithoutCallingQdrant` | Pass |
| Prefix regression is actually detected | Temporarily swap document/query prefixes, run `GeminiEmbeddingClientTests`, restore exact source bytes and rerun | 2 expected failures, then 5/5 pass |

No CI test calls Google or needs a Gemini secret. Real Qdrant is a disposable
Testcontainers service with its own authentication key. Mock body assertions
verify the application's wire format; independent live calls below establish
that the actual Gemini endpoint accepted that format.

## Commands and results

- `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify`:
  **159 tests, 0 failures/errors/skips; BUILD SUCCESS** (139 existing + 20 added
  cases including parameterized authorization cases). No dependency or migration
  changes.
- Focused search/authorization suite: **47 passed**.
- Mutation: `bash backend/mvnw -f backend/pom.xml -B
  -Dtest=GeminiEmbeddingClientTests test`: **2 failures** after swapping prefixes.
  Restore original bytes, then `spotless:check -Dtest=GeminiEmbeddingClientTests
  test`: **5 passed**. Mutation is not retained in the diff.
- `docker compose -p sf75-qa up -d qdrant`:
  `curl --fail --silent http://127.0.0.1:6333/healthz`: **200**, healthz passed.
- `docker compose -f docker-compose.prod.yml config --format json` with dummy
  DB/R2/Gemini/Qdrant environment values: verified no Qdrant published ports,
  API-key authentication, persistent volume and backend internal URL. Omitting
  QDRANT_API_KEY correctly rejects configuration before rollout. Real secrets
  were never printed through Compose configuration.
- Actual `curl :embedContent`, config containing the API-key header fed via stdin,
  JSON `embedContentConfig.outputDimensionality=768`: **HTTP 200, 768 values**.
  Independent real `:batchEmbedContents` request with that wrapper: **HTTP 200,
  768 values** per input. No API key or vectors are stored in this repository.
- Actual packaged Java backend + PostgreSQL 17 disposable copy of the demo DB +
  Compose Qdrant: authenticated as owner, obtained session/XSRF cookies, then
  `POST /catalog-index`: **200**, `{indexed:90,removed:0,dimensions:768}`.
  Point count matches SQL `SELECT count(*) FROM shopflow.products WHERE active`.
  Index HTTP call took **4.02 s** on this run, including the first collection
  creation and Gemini batch.
- `docker compose -p sf75-qa up -d --force-recreate qdrant`: **90 points preserved**.
- Stop Qdrant: search returns **503**, catalog and health remain **200**. Restart
  the packaged backend while Qdrant is stopped: boot and health still **200**.
  These are real network checks, not mocked health assertions.
- `docker stats --no-stream`: empty Qdrant **76.51 MiB**, after index **88.77 MiB**.
  Two during-index samples measured 81.19/88.62 MiB and 0.04%/2.17% CPU. Sampling
  is sparse and does not establish an absolute peak or a load-test guarantee.
- `python3 scripts/test-deploy-stdin.py`: **4 passed**;
  `python3 scripts/test-deploy-tls.py`: **4 passed**.
- Temporary database/backend/Qdrant services and QA volumes removed. The only
  production DB operation was a read-only pg_dump into the disposable test DB;
  no production data, R2 object or deployed configuration changed.

Sanitized live results and top-five matches:
[live-index-evidence.json](qa/sf-75/live-index-evidence.json).
Private execution logs are under `/tmp/sf75-*.log` on the QA machine; they are
local evidence, not permanent artifacts or a prerequisite for running tests.

## Repeat the single live Gemini check

After exporting GEMINI_API_KEY from the protected env file, feed the header through
stdin so it is not included in curl process arguments:

```bash
printf 'header = "x-goog-api-key: %s"\n' "$GEMINI_API_KEY" | \
  curl --fail-with-body --silent --show-error --config - \
  -H 'Content-Type: application/json' \
  --data '{"content":{"parts":[{"text":"task: search result | query: màng lọc HEPA"}]},"embedContentConfig":{"outputDimensionality":768}}' \
  'https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:embedContent' | \
  python3 -c 'import json,sys; print(len(json.load(sys.stdin)["embedding"]["values"]))'
```

Observed output: `768`. Only run manually with permission to use API quota.

## Retrieval observations and limits

Three Vietnamese queries were tried, not a statistically representative benchmark:

- Xiaomi bedroom purifier: top four results are the four Xiaomi purifier models.
- Japanese rice-cooker Vietnamese labels: top two results are matching label sets.
- Winix 5500-2 HEPA: matching Filter H for 5500-2 ranks second; a Filter G labeled
  for 5500 ranks first. Semantic proximity does **not** establish exact equipment
  compatibility. SF-76 must verify model compatibility from reliable product
  information, not describe the highest-scoring result as guaranteed compatible.

Score ordering is verified, but there is no score threshold, relevance percentage,
exact SKU filter or guarantee against unrelated nearest matches. Demo descriptions
may be imperfect. This feature retrieves candidates; it does not certify facts.

Reindex is synchronous, full and serialized only within one backend instance.
No retry/cache/automatic synchronization; a failure may leave some updated batches,
but stale points are not deleted unless all upserts succeed. Catalog writes/imports
must not overlap indexing. More than ~1000 products or repeated 429s warrants a
separate design review. Changing model/dimensions requires rebuilding the index.

Frontend suites were not rerun: no frontend code or dependency changed. No native
UI, concurrent-user load test, production deployment or CI claim is included.
Before production release, configure QDRANT_API_KEY and verify Gemini quota;
follow the separate release/changelog/tag-signing process. SF-75 has no background
job, public Qdrant dashboard, scheduled backups or chatbot UI.
