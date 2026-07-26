# Customer Return QA Checklist

**Ngày kiểm tra:** 26/07/2026

**Scope:** `GET /returns`, `GET /returns/orders`, `POST /returns`,
`PATCH /returns/{returnId}`, restock inventory update, movement audit,
concurrency và Return Management UI.

## Backend scenarios

| # | Scenario | Evidence | Kết quả |
| --- | --- | --- | --- |
| 1 | Tạo return cho order `DELIVERED` trả `201`, status `REQUESTED`, không đổi inventory | `ReturnControllerTests.createsReturnForDeliveredOrderWithoutTouchingInventory` | Pass |
| 2 | Read model `/returns` và `/returns/orders` phản ánh returned/returnable quantity | Same controller test | Pass |
| 3 | Order chưa `DELIVERED` trả 409; order không tồn tại trả 404; item không thuộc order trả 400 | `rejectsCreationForUndeliveredMissingOrForeignOrders` | Pass |
| 4 | Items rỗng, quantity 0/fraction, reason blank, item lặp bị từ chối không side effect | `rejectsInvalidCreateBodiesWithoutSideEffects` | Pass |
| 5 | Tổng return `REQUESTED`/`APPROVED`/`RESTOCKED` không vượt quantity đã mua; `REJECTED` không tính | `enforcesReturnableQuantityAcrossRequestsExcludingRejected` | Pass |
| 6 | Approve giữ inventory; restock tăng on-hand từng item, mỗi item một `RETURN_RESTOCK` movement reference `RETURN`, `createdBy=null` | `approvesAndRestocksEveryItemAtomically` | Pass |
| 7 | Restock lặp lại, transition sai lifecycle, return không restockable trả 409; sai quy tắc `restockable` trả 400 | `rejectsInvalidTransitionsAndNonRestockableRestocks` | Pass |
| 8 | Hai request đồng thời tranh phần quantity còn lại chỉ một bên thắng | `ReturnConcurrencyTests.concurrentCreatesNeverExceedPurchasedQuantity` | Pass |
| 9 | Hai xác nhận restock đồng thời chỉ tăng on-hand một lần, một movement | `concurrentRestockConfirmationsIncreaseOnHandOnlyOnce` | Pass |

Controller tests dùng H2 cho API/validation nhanh. Concurrency chạy với
PostgreSQL Testcontainers, vì đây là bằng chứng cho locking và transaction trên
database production-compatible.

## Browser scenarios

Playwright intercept API responses; các test không đọc hoặc sửa development
data.

| Scenario | Evidence | Kết quả |
| --- | --- | --- |
| Warehouse tạo return, approve với restockable và confirm restock đủ lifecycle | `returns.spec.ts` test 1 | Pass |
| Shop owner reject request; return `APPROVED` restockable chờ warehouse, không có nút restock | test 2 | Pass |
| Client validation (thiếu order, thiếu quantity, vượt returnable) và `409` giữ dữ liệu để retry thành công | test 3 | Pass |

## Commands và kết quả

```bash
cd backend && bash mvnw -q -Dtest=ReturnControllerTests,ReturnConcurrencyTests test
cd backend && bash mvnw -q spotless:check verify
cd frontend && npm run type-check
cd frontend && npm run lint:ci
cd frontend && npm run build
cd frontend && npm run test:e2e -- returns.spec.ts
git diff --check
```

- Return controller tests: 6 passed.
- PostgreSQL concurrency tests: 2 passed.
- Return Playwright scenarios: 3 passed.
- Full backend verify, frontend checks và diff whitespace check: passed.

## Traceability

- SF-8 acceptance criteria: return hợp lệ cho order `Delivered`, restock có điều
  kiện, validation quantity vượt lượng đã mua.
- SRS: FR-07, BR-08 đến BR-10, BR-13, NFR-01.
- Related implementation tasks: SF-17, SF-18, SF-63.

## Out of scope

Refund và payment gateway không nằm trong MVP (FR-07.9). Việc chỉ nhân viên kho
xác nhận restock là UI boundary của MVP, không phải security boundary;
`created_by` của movement giữ `null` cho tới khi có authentication.
