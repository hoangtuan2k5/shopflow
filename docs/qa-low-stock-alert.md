# Low Stock Alert QA Checklist

**Ngày kiểm tra:** 26/07/2026

**Scope:** cờ `lowStock` trong `GET /inventory`,
`PUT /inventory/{productId}/threshold` và alert trong Inventory UI.

## Backend scenarios

| # | Scenario | Evidence | Kết quả |
| --- | --- | --- | --- |
| 1 | `lowStock = true` khi available ≤ threshold (kể cả biên và threshold 0); `false` khi vượt threshold hoặc threshold `null` | `InventoryControllerTests.flagsLowStockAtOrBelowThresholdOnly` | Pass |
| 2 | Đặt threshold trả cờ mới; adjustment đưa available vượt threshold gỡ cờ ở response kế tiếp; threshold `null` ngừng theo dõi; không ghi movement | `updatesThresholdAndRecomputesFlagWithoutMovements` | Pass |
| 3 | Threshold âm trả 400, fraction trả 400 malformed, product không tồn tại trả 404; threshold giữ nguyên | `rejectsInvalidThresholdRequests` | Pass |

Tests dùng H2 qua MockMvc như các inventory test hiện có; low stock là logic
đọc thuần nên không cần concurrency coverage riêng.

## Browser scenarios

Playwright intercept API responses; các test không đọc hoặc sửa development
inventory.

| Scenario | Evidence | Kết quả |
| --- | --- | --- |
| Banner đếm số product low stock, badge `Low stock` và threshold hiển thị theo row; đặt threshold gửi đúng `PUT` body | `low-stock.spec.ts` test 1 | Pass |
| Xóa threshold gỡ alert và badge; giá trị âm bị chặn tại client | test 2 | Pass |

## Commands và kết quả

```bash
cd backend && bash mvnw -q -Dtest=InventoryControllerTests test
cd backend && bash mvnw -q spotless:check verify
cd frontend && npm run type-check
cd frontend && npm run lint:ci
cd frontend && npm run build
cd frontend && npm run test:e2e -- low-stock.spec.ts
git diff --check
```

- Inventory controller tests (gồm 3 test low stock mới): 11 passed.
- Low stock Playwright scenarios: 2 passed.
- Full backend verify, frontend checks và diff whitespace check: passed.

## Traceability

- SF-9 acceptance criteria: đánh dấu `Low Stock` khi available ≤ threshold,
  alert hiển thị cho warehouse, alert gỡ khi stock phục hồi.
- SRS: FR-08, BR-14.
- Related implementation tasks: SF-64, SF-65, SF-66.

## Out of scope

Không lưu cờ độc lập, không background job và không gửi email/SMS/push
notification (FR-08.4); alert chỉ suy ra khi đọc dữ liệu hiện tại.
