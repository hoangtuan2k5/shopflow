# Customer Storefront QA Checklist

- Jira: SF-47
- Parent: SF-1
- Related: SF-2, SF-3
- Verified: 2026-07-20
- Scope: customer catalog, search, cart, and checkout presentation

## Storefront scenarios

| Scenario                                   | Expected                                                                                     | Result |
| ------------------------------------------ | -------------------------------------------------------------------------------------------- | ------ |
| Customer navigation                        | Warehouse and Shop owner workflow navigation is not shown                                    | Pass   |
| Catalog                                    | Product name, whole-VND price, and text stock status are visible                             | Pass   |
| Out-of-stock product                       | Product remains visible and its add action is disabled                                       | Pass   |
| Search by product name                     | Matching is case-insensitive and the visible product/stock counts update                     | Pass   |
| Search after selecting a product           | Cart count and selected quantity remain unchanged                                            | Pass   |
| Cart                                       | Customer can increase, decrease, or remove a selected product and see the whole-VND subtotal | Pass   |
| Checkout handoff                           | Existing delivery form receives the selected products and quantities                         | Pass   |
| Insufficient stock response                | Error and available quantity appear without losing the selected product                      | Pass   |
| Successful order response                  | CARD payload, order ID, status, and whole-VND total reach the payment handoff                | Pass   |
| Empty, loading, and request failure states | A distinct state and recovery action are shown when applicable                               | Pass   |
| Responsive layout                          | 390 × 844, 768 × 1024, and 1440 × 1000 have no horizontal overflow                           | Pass   |
| Browser console                            | No unexpected errors during the smoke flow                                                   | Pass   |

The browser smoke check ran against the production Vite build in headless
Chromium. Product API responses were intercepted, so the check did not read or
modify development data. It covered search, cart persistence, the responsive
mobile cart action, the desktop cart drawer, and the checkout dialog.
The order flow first returned the expected 400 insufficient-stock response and
then a 201 success response; that expected resource error was excluded from the
unexpected-console-error assertion.

## Component regression coverage

The existing checkout SSR regression check verifies the Vietnamese payment
handoff, exact order ID and status, whole-VND total, selected item, and
field-specific server error. The product price check continues to reject values
that would need rounding.

## Verification commands

```sh
cd frontend
node --experimental-strip-types --test src/api/__tests__/validateProductPrice.test.ts src/components/forms/__tests__/CheckoutForm.test.ts
npm run type-check
npm run lint:ci
npm run build
git diff --check
```

Results on 2026-07-20:

- Frontend native Node tests: 2 passed.
- Frontend type-check and lint: passed.
- Frontend production build: passed.
- Headless Chromium storefront smoke check: passed at all three viewports.

## Scope limits

- SF-47 changes no backend, database schema, API contract, or seed data.
- Product images remain placeholders because the current product API has no
  image field; media storage and management remain separate future work.
- Payment remains the existing simulation handoff.
