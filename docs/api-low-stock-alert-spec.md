# ShopFlow — Low Stock Alert API Specification

**Phiên bản:** 1.0

**Ngày cập nhật:** 26/07/2026

**Liên quan:**

- [SF-9: Send Low Stock Alert](https://tuanwork.atlassian.net/browse/SF-9)
- [SF-64: Define Low Stock Alert Rules and API Contract](https://tuanwork.atlassian.net/browse/SF-64)
- [`SRS.md`](./SRS.md) — FR-08, BR-14
- [`api-inventory-management-spec.md`](./api-inventory-management-spec.md) — inventory read model
- [`database-schema.md`](./database-schema.md) — `products.low_stock_threshold`, `inventory_items`

---

## 1. Tổng quan

Low stock alert là trạng thái suy ra khi đọc dữ liệu, không lưu cờ độc lập và
không gửi notification ra ngoài hệ thống (FR-08.4):

```text
lowStock = lowStockThreshold != null && availableStock <= lowStockThreshold
```

- `lowStockThreshold` cấu hình theo product, số nguyên không âm, nullable;
  `null` nghĩa là không theo dõi cảnh báo (FR-08.1).
- Product có threshold khác `null` được đánh dấu `LOW_STOCK` khi available
  stock nhỏ hơn hoặc bằng threshold (FR-08.2, BR-14) và tự gỡ khi available
  stock vượt threshold (FR-08.3) — vì cờ được tính lại ở mỗi lần đọc.

| Thao tác | Endpoint |
| --- | --- |
| Xem tồn kho kèm cờ low stock | `GET /inventory` |
| Cập nhật threshold theo product | `PUT /inventory/{productId}/threshold` |

## 2. GET /inventory — read model mở rộng

`GET /inventory` (xem [`api-inventory-management-spec.md`](./api-inventory-management-spec.md))
bổ sung hai field cho từng item:

```json
{
  "productId": 3,
  "productName": "Áo thun cotton",
  "onHandStock": 5,
  "reservedStock": 2,
  "availableStock": 3,
  "lowStockThreshold": 5,
  "lowStock": true
}
```

| Field | Kiểu | Quy tắc |
| --- | --- | --- |
| `lowStockThreshold` | integer, nullable | Threshold hiện tại của product; `null` = không theo dõi. |
| `lowStock` | boolean | Tính khi đọc theo công thức ở mục 1; luôn `false` khi threshold `null`. |

`POST /inventory/{productId}/adjustments` trả về cùng shape, nên cờ low stock
phản ánh ngay sau điều chỉnh.

## 3. PUT /inventory/{productId}/threshold

Đặt hoặc gỡ threshold cho một product.

### Request

```json
{ "lowStockThreshold": 5 }
```

| Field | Bắt buộc | Quy tắc |
| --- | --- | --- |
| `lowStockThreshold` | Không | Số nguyên ≥ 0 trong dải 32-bit, hoặc `null` để ngừng theo dõi. |

### Response — `200 OK`

Inventory item sau cập nhật, cùng shape với phần tử của `GET /inventory`.

Cập nhật threshold không phải nghiệp vụ thay đổi stock: không ghi
`stock_movements` (BR-13 không áp dụng).

## 4. Validation và lỗi

Error body dùng chung format `{ "message", "status", "fieldErrors" }` của
inventory:

| Trường hợp | Status |
| --- | --- |
| `lowStockThreshold` âm, fractional hoặc sai kiểu | `400` |
| Body malformed | `400` |
| Product không tồn tại | `404` |

## 5. Schema decision

`products.low_stock_threshold` (`INT`, nullable, `CHECK >= 0`) đã tồn tại từ
`V1__init.sql`; SF-9 không cần migration mới.

## 6. Test cases tối thiểu

1. Product có threshold, available ≤ threshold → `lowStock = true` (biên: available = threshold).
2. Product có threshold, available > threshold → `lowStock = false`.
3. Product threshold `null` → `lowStock = false` với mọi available.
4. Đặt threshold hợp lệ → `200`, response phản ánh threshold và cờ mới.
5. Đặt threshold `null` → `200`, gỡ theo dõi.
6. Threshold âm → `400`; product không tồn tại → `404`; không thay đổi dữ liệu.
7. Adjustment/receiving đưa available vượt threshold → lần đọc kế tiếp `lowStock = false` (FR-08.3).

## 7. Version History

| Phiên bản | Ngày | Thay đổi |
| --- | --- | --- |
| 1.0 | 26/07/2026 | Contract đầu tiên cho low stock alert (SF-64). |
