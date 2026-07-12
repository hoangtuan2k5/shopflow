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
