# Product Image QA Checklist

- Jira: SF-74, SF-89, SF-90, SF-91, SF-92
- Verified: 2026-10-16
- Scope: owner image upload, catalog images, replacement and fallback
- Contract: [Product Image API](./api-product-image-spec.md)

## Backend scenarios

Tests are in `ProductImageControllerTests`, unless another class is named.
CI uses a shared mock `S3Client`; it never needs R2 credentials.

| Scenario | Automated coverage | Result |
| --- | --- | --- |
| Owner uploads actual PNG bytes despite an untrusted filename/MIME; catalog list/detail return its URL | `uploadsAndReplacesOnlyAfterCommit` | Pass |
| Anonymous 401; customer/warehouse 403; missing/inactive product 404; rejected requests preserve the key and never call storage | `protectsRoleAndCsrfAndProductExistence` | Pass |
| Authenticated missing/invalid CSRF 403, valid cookie/header reaches validation | `CsrfTokenRoundTripTests.protectsImageUploadsWithTheRealCookieRoundTrip` | Pass |
| Fake PNG, SVG and corrupt image 415; oversized file 413; no storage calls or key changes | `refusesInvalidFilesWithoutStorageCalls` | Pass |
| Exactly 2 MiB accepted; JPEG beginning FF D8 FF E2 with a complete sRGB ICC_PROFILE APP2 segment accepted | `checksSizeBoundaryAndJpegApp2` | Pass |
| Missing/empty/duplicate file 400, preserving the previous key without storage calls | `rejectsMissingEmptyOrDuplicateFiles` | Pass |
| No image returns null; JSON request and excessive dimensions return 415 | `rejectsNonMultipartAndExcessivePixelsAndExposesNullByDefault` | Pass |
| Real servlet rejects a file larger than 2 MiB before controller selection with JSON 413 | `ProductImageHttpTests.servletRejectsOversizeMultipartWithTheGlobalJsonHandler` | Pass |
| Storage failure 503 does not expose SDK details; explicit rollback deletes only the new object | `preservesOldImageOnStorageFailureAndCleansRollback` | Pass |
| Database constraint failure after upload rolls back the key and cleans the new object | `databaseFailureAfterUploadRollsBackAndCleansOnlyTheNewObject` | Pass |
| Dev starts without credentials/network calls; staging rejects blank credentials | `R2ConfigurationTests` | Pass |
| Old-object deletion failure does not undo the new reference | `cleanupFailureDoesNotUndoNewImage` | Pass |

The replacement service locks the active product row with `FOR UPDATE` and registers
transaction completion cleanup. It deletes the previous key only on confirmed
commit, the new key on confirmed rollback, and neither on unknown transaction status.
A storage cleanup failure can leave an unreferenced object for manual removal.

## Browser scenarios

Coverage is in `frontend/e2e/product-image.spec.ts`. Every case uses `mockSession`
and intercepted catalog/image responses, including local PNG bytes.

| Scenario | Automated coverage | Result |
| --- | --- | --- |
| Multipart body and CSRF header, refreshed thumbnail, SPA navigation to catalog, detail and cart images | `Owner uploads multipart with CSRF and refreshed images reach the catalog, detail and cart` | Pass |
| Backend 415 reason is shown and the old image stays visible | `Backend rejection preserves the previous image and shows its reason` | Pass |
| Null and 404 images restore the existing gradient/package placeholder without uncaught JavaScript errors | `Null and failed images retain the existing placeholder` | Pass |
| Owner entry link; warehouse link absent and direct access redirected; guest sent to login | `Owner has an entry link while warehouse and guests cannot reach image management` | Pass |
| Pending upload disables controls; a previously failed thumbnail recovers after the URL changes | `A failed thumbnail recovers after replacement and pending uploads disable controls` | Pass |

Responsive review additionally caught a cramped native file input at 390 px. The
input now occupies its own row on mobile while the desktop layout stays inline.

A deliberate image 404 remains visible as a network/resource diagnostic in browser
developer tools. The component handles the image error and does not throw JavaScript
errors; the test does not hide browser diagnostics or replace console methods.

## Commands and results

Run from the repository root:

```sh
bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify
npm --prefix frontend run lint:ci
npm --prefix frontend run build
npm --prefix frontend run test:e2e
```

- Backend: 129 tests passed, zero failures/errors/skips; includes PostgreSQL
  Testcontainers, Flyway V6 and the embedded Tomcat multipart check.
- Frontend: lint and build/type-check passed; 34 browser tests passed.
- Build retains existing non-blocking dependency PURE annotation warnings.
- Document links/JSON examples and `git diff --cached --check` passed.

### Permission mutation check

Temporarily remove the two-line `POST /products/*/image` matcher/`hasRole(SHOP_OWNER)`
rule from `SecurityConfig`. The existing final `anyRequest().authenticated()` rule
then admits any signed-in role. Run:

```sh
bash backend/mvnw -f backend/pom.xml -B test \
  -Dtest=ProductImageControllerTests#protectsRoleAndCsrfAndProductExistence
```

Observed: exit 1, one failed test, `Status expected:<403> but was:<200>` for a
customer. Restore the rule, then run the full verification command above: all
129 tests pass. The mutation was not committed. No additional mutations were run.

### One-time real R2 smoke

Performed outside CI on 2026-10-16, using a disposable PostgreSQL 17 container,
a loopback backend, a temporary same-origin Vite proxy and a dedicated QA product.
Credentials were read into process environment from the VPS `.env`, never printed
or placed in Git. The production application/database were not used.

1. Dev backend started without R2 credentials: health 200.
2. With the five R2 settings, owner upload returned 200. Public HTTPS image bytes
   matched the uploaded PNG exactly.
3. Replacement returned 200, database/catalog referenced the new key, and the
   previous object returned 404 through the signed R2 SDK HEAD request.
4. Real HTTP rejected fake PNG/SVG (415), oversized multipart (413), and warehouse
   upload (403); the current key was unchanged.
5. A real Vietnamese browser session uploaded another image through the owner
   screen. The image decoded successfully and the previous key was removed.
6. Catalog and owner screen were checked at 1440x1000 and 390x844 without
   horizontal overflow or uncaught JavaScript errors.
7. All generated test objects were deleted and verified 404 using the R2 SDK;
   the disposable database and temporary processes were removed.

Local evidence: `/tmp/sf74-qa/evidence.json`, catalog/owner desktop/mobile PNG
screenshots and temporary application logs. These local artifacts are not committed
and may be removed by temporary-directory cleanup. The initial curl-based read
verifier returned a signature mismatch; the successful smoke and cleanup verification
used the same Java SDK as the backend. Failed verifier runs were also cleaned up.

To repeat manually, start an isolated database/backend with the R2 environment,
create a dedicated active QA product, sign in as owner and follow steps 2–7. Use
S3 API HEAD/DELETE for deletion evidence: a custom-domain cache can still serve a
previously cached response. Never reuse a live product or leave test objects behind.

## Traceability

| Requirement | Evidence |
| --- | --- |
| SF-74 valid upload is stored in R2 and referenced by product | Controller upload/replacement tests and real R2/browser smoke |
| SF-74 invalid file is rejected with a reason and no object write | Validation/size/permission tests with `verifyNoInteractions`, unchanged-key assertions and real HTTP checks |
| SF-74 product without image keeps current placeholder | Null URL backend check and browser fallback scenario |
| SF-74 replacement appears in catalog | Replacement transaction test, cache/SPA browser scenario and real R2 smoke |
| SF-89 contract and SRS FR-11 | [Image API](./api-product-image-spec.md), [SRS](./SRS.md) |
| SF-90 backend/configuration; SF-91 owner UI | PR #117 and PR #118, controller/HTTP and browser suites |
| SF-92 verification | This checklist and the recorded permission mutation |

## Out of scope

- CI does not call R2. The real-bucket smoke above is a separate, authorized
  one-time check, not an automated external-network requirement.
- No production release/deploy, product import, product CRUD, gallery, preview,
  cropping, resizing, thumbnails or image-deletion endpoint.
- No load testing, CDN cache-purge test, forced database commit ambiguity or
  external storage outage injection. Rollback/cleanup failures are checked with mocks.
- Dashboard-only token scope and `r2.dev` settings were not independently inspected
  with the S3 credential. Deployment documentation requires a dedicated image bucket,
  bucket-scoped Object Read & Write token and disabled development URL.
