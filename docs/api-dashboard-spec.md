# Shop-owner dashboard API (SF-129)

## Access and query

`GET /dashboard` (`/api/dashboard` through the gateway) requires SHOP_OWNER:
401 for anonymous callers, 403 for other roles. The endpoint is read-only.

| Parameter | Meaning |
| --- | --- |
| `from`, `to` | ISO local dates, both supplied or both omitted; inclusive |
| `groupBy` | `day` (default) or `month` |

Defaults to the last 30 calendar days including today in `Asia/Ho_Chi_Minh`.
Reversed ranges, more than 366 inclusive days, malformed dates, a missing date
partner or an unsupported grouping return 400 `{message,status}`. Database
bounds are local midnight at `from` inclusive and midnight after `to` exclusive.

## Response

```json
{
  "from": "2026-10-01",
  "to": "2026-10-02",
  "groupBy": "day",
  "timeZone": "Asia/Ho_Chi_Minh",
  "paidAmount": "250000",
  "orderCount": 2,
  "processingOrders": 1,
  "lowStockProducts": 3,
  "series": [
    {"date":"2026-10-01","paidAmount":"250000","orderCount":2},
    {"date":"2026-10-02","paidAmount":"0","orderCount":0}
  ],
  "statuses": [{"status":"PENDING_PAYMENT","count":1},{"status":"PAID","count":1},{"status":"PAYMENT_FAILED","count":0},{"status":"CANCELLED","count":0}],
  "topProducts": [{"productId":37,"productName":"Filter","quantity":2,"paidAmount":"250000"}]
}
```

Money is a decimal integer VND string, preserving precision beyond JavaScript's
safe-integer range. Counts are JSON integers. `series` is sorted ascending,
including zero buckets; monthly bucket dates are the first of each month.
`statuses` contains all four order statuses, including zero counts. Top products
are limited to five, quantity descending then product ID ascending.

## Metric definitions

- Paid amount: sum order totals once per order, dated by its **first** successful
  payment's `paid_at`. Deduplicate before applying the date filter. Pending,
  failed and expired payments are excluded; multiple item/payment rows cannot
  multiply totals. This is gross paid value, not profit or net revenue. Returns
  do not reduce it because the application has no refund accounting.
- Order count/status distribution: orders created in the interval; statuses
  describe their current state, not historical state at the end of the interval.
- Top products: same paid-order cohort, quantities and unit prices from purchase
  snapshots. Names come from the latest matching order-item snapshot, not current
  catalog data, so renamed/inactive products remain reportable.
- Processing orders: current PAID orders with NONE/PREPARING/SHIPPED delivery.
- Low stock: current active products whose configured threshold is at least
  `on_hand_stock - reserved_stock`, matching inventory behavior.
- The last two metrics are current snapshots and are not date-filtered.

Queries run against one read-only repeatable-read database snapshot. The browser
only presents aggregates; exact labels/tables use integer VND values. Chart
coordinates may use floating-point approximations; tooltips/tables preserve the
original amounts. No schema changes or new infrastructure are required.
