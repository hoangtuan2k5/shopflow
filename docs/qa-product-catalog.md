# Product Catalog QA Checklist

- Jira: SF-39
- Verified: 2026-07-11
- Scope: Product Catalog API and customer catalog UI

## API Scenarios

| # | Scenario | Automated coverage | Result |
| --- | --- | --- | --- |
| 1 | List active products with name, price and stock status | `listsOnlyActiveProductsWithComputedStockStatus` | Pass |
| 2 | Return an empty list when no active products exist | `returnsEmptyListWhenNoActiveProductsExist` | Pass |
| 3 | Mark unavailable products as `OUT_OF_STOCK` | `listsOnlyActiveProductsWithComputedStockStatus` | Pass |
| 4 | Return active product detail including description, price and stock status | `returnsProductDetail` | Pass |
| 5 | Return 404 for an unknown product | `returnsNotFoundForMissingOrInactiveProduct` | Pass |
| 6 | Return 404 for an inactive product | `returnsNotFoundForMissingOrInactiveProduct` | Pass |
| 7 | Return `IN_STOCK` when available stock is greater than zero | `listsOnlyActiveProductsWithComputedStockStatus` | Pass |
| 8 | Return `OUT_OF_STOCK` when available stock is zero or missing | `listsOnlyActiveProductsWithComputedStockStatus`, `treatsMissingInventoryAsOutOfStock` | Pass |

Run the API checks with:

```sh
cd backend
sh mvnw -Dtest=CatalogControllerTests test
```

## UI Scenarios

| Scenario | Expected | Result |
| --- | --- | --- |
| Catalog list | Active products show name, formatted price and stock status | Pass |
| Out-of-stock product | Red `Out of stock` status remains visible without opening detail | Pass |
| Product detail | Dialog shows description, price and stock status | Pass |
| Missing product detail | Dialog shows `Product unavailable` after a 404 response | Pass |
| Empty catalog | Empty state shows `No products available` | Pass |
| Catalog request failure | Error state shows `Catalog unavailable` and a retry action | Pass |
| Responsive layout | Three columns at 1440 px and one column at 390 px without overlap | Pass |
| Accessibility smoke check | Dialog has an accessible name, close control and no browser console errors | Pass |

The UI checks use the local development backend for the happy path and Playwright route interception for empty, error and 404 responses. No production data is modified.

## Regression Checks

```sh
cd frontend
npm run lint:ci
npm run build
```

## SF-127 — Incremental catalog loading (2026-10-17)

The earlier results above are the historical SF-39 verification. SF-127 changes
the list response to `{ items, nextCursor }`; all callers and mocks now use that
contract. Detail and image-upload responses are unchanged. See the
[catalog contract](./api-product-catalog-spec.md).

| Check | Evidence / result |
| --- | --- |
| Bounded SQL, stable ID cursor, only active products | PostgreSQL `CatalogControllerTests`: 45 products load as 20 / 20 / 5; deleted cursor boundary, limit 100 and end-of-list covered |
| Query validation | Limits 0, -1, 101, nonnumeric limit/cursor, negative/overflow cursor and a query over 255 characters return 400 |
| Server-side name search | Finds an item beyond the first batch; Vietnamese case/diacritics, trimmed whitespace and literal `%`, `_`, `!` covered |
| Automatic loading | `catalog-pagination.spec.ts`: 20 → 40 → 45; no duplicate cursor request, no repeated products, existing card position preserved |
| Failed next batch | Existing cards retained after the established automatic retry; further attempts require the retry button and reuse the failed cursor |
| Search and cart | Changing/clearing search starts at the first batch; a slow old response cannot overwrite a newer result; selected cart products survive search changes |
| Owner image management | Load all three batches and upload an image for item 45; refreshed thumbnail appears without losing loaded rows |
| Keyboard and regression | Load-more retains focus; existing auth, image fallback/upload, dialog SF-126, receiving, inventory, delivery and return tests pass |

Verification commands:

```sh
bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify
npm --prefix frontend run lint:ci
npm --prefix frontend run build
npm --prefix frontend run test:e2e
```

Results: **133 backend tests**, **45 browser tests**, lint and build passed.
The first full browser run timed out before opening one 360×640 dialog (blank
development page); that case passed three isolated repetitions and the complete
45-test rerun passed. No timeout increase or retry setting change was made.

An additional smoke used the production frontend build, the actual Java backend
and a disposable PostgreSQL 17 database, with 45 local QA products. Its first 30
names/prices/image references came from the public catalog; 15 additional rows
were explicitly named as pagination test products. No catalog API was mocked.
At 1440×900 and 390×700, observed response batches were **20 / 20 / 5** and search
found item 45. The first 20 real R2 images loaded, with no horizontal overflow or
uncaught JavaScript errors. Screenshots:

- [Desktop: initial 20 products](./qa/sf-127/catalog-desktop.png)
- [Mobile: search finds item 45](./qa/sf-127/catalog-mobile-search.png)

The disposable database and QA processes were removed. No production database,
R2 objects, dependencies or migrations were changed. Native iOS/Android devices
were not tested. Cursor pagination is not a snapshot while catalog records change;
DOM virtualization and the unrelated deploy-stdin defect remain outside SF-127.
