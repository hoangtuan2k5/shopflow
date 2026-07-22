# Supplier Receiving QA Checklist

**Ngày kiểm tra:** 22/07/2026

**Scope:** `POST /receivings`, inventory update, receiving audit, concurrency và
Warehouse receiving UI.

## Backend scenarios

| # | Scenario | Evidence | Kết quả |
| --- | --- | --- | --- |
| 1 | Receipt cho product active tăng on-hand và available | `ReceivingControllerTests.receivesActiveAndInactiveProductsWithAnAuditReference` | Pass |
| 2 | Product inactive vẫn được nhận hàng | Same controller test | Pass |
| 3 | Product chưa có inventory tạo row `0/0` rồi tăng stock | `createsMissingInventoryBeforeReceiving` | Pass |
| 4 | Response/audit chứa receiving fields, `createdBy=null`, `STOCK_RECEIVED` và `RECEIVING` reference | `receivesActiveAndInactiveProductsWithAnAuditReference` | Pass |
| 5 | Zero, âm, fraction, numeric string, blank/oversized text bị từ chối không có side effect | `rejectsInvalidBodiesWithoutSideEffects` | Pass |
| 6 | Product không tồn tại trả 404; overflow trả 409 | `rejectsMissingProductsAndInventoryOverflowWithoutWriting` | Pass |
| 7 | Receipt đồng thời trên inventory có sẵn không mất update | `concurrentReceiptsForExistingInventoryDoNotLoseStock` | Pass |
| 8 | Hai receipt đầu tiên đồng thời chỉ tạo một inventory row | `concurrentFirstReceiptsCreateOneInventoryRow` | Pass |
| 9 | Audit movement fail rollback inventory và receiving record | `auditFailureRollsBackInventoryAndReceivingRecord` | Pass |

Controller tests dùng H2 cho API/validation nhanh. Concurrency và rollback chạy
với PostgreSQL Testcontainers, vì đây là bằng chứng cho locking và transaction
trên database production-compatible.

## Warehouse browser scenarios

Playwright intercept API responses; các test không đọc hoặc sửa development
inventory.

| Scenario | Evidence | Kết quả |
| --- | --- | --- |
| Submit hợp lệ chỉ gửi contract fields, hiển thị submitting/success và refresh row | `receiving.spec.ts` test 1 | Pass |
| Quantity/supplier/note không hợp lệ có lỗi tại field và text dài không bị cắt ngầm | test 2 | Pass |
| `409`, `404` và network error giữ dữ liệu để retry; retry thành công | test 3 | Pass |
| Loading, API failure, retry, empty state, mobile width và Escape/focus restore | test 4 | Pass |

## Commands và kết quả

```bash
cd backend && bash mvnw -q -Dtest=ReceivingControllerTests,ReceivingConcurrencyTests test
cd backend && bash mvnw -q spotless:check verify
cd frontend && npm run type-check
cd frontend && npm run lint:ci
cd frontend && npm run build
cd frontend && npm run test:e2e -- receiving.spec.ts
git diff --check
```

- Receiving controller tests: 4 passed.
- PostgreSQL concurrency and rollback tests: 3 passed.
- Receiving Playwright scenarios: 4 passed.
- Full backend verify, frontend checks và diff whitespace check: passed.

## Traceability

- SF-7 acceptance criteria: successful receipt, positive quantity validation and
  basic audit record.
- SRS: FR-06, BR-11, BR-13, NFR-01.
- Related implementation tasks: SF-55, SF-56, SF-57, SF-58.

## Out of scope

Supplier master data, receiving history UI, pagination/search and authentication
remain separate work. The Warehouse route is an MVP workflow, not a security
boundary; `createdBy` stays `null` until server-side authentication exists.
