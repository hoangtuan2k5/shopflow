# ShopFlow — Inventory Management API Specification

**Phiên bản:** 1.0

**Ngày cập nhật:** 21/07/2026

**Liên quan:**

- [SF-6: Manage Inventory Stock](https://tuanwork.atlassian.net/browse/SF-6)
- [SF-14: Define Inventory Stock Fields](https://tuanwork.atlassian.net/browse/SF-14)
- [`SRS.md`](./SRS.md) — FR-05, BR-01, BR-02, BR-12, BR-13
- [`database-schema.md`](./database-schema.md) — `products`, `inventory_items` và `stock_movements`

---

## 1. Tổng quan

API cho phép nhân viên kho xem tồn kho của mọi product và điều chỉnh số lượng
on-hand bằng một delta có lý do. Available stock luôn được tính khi đọc:

```text
availableStock = onHandStock - reservedStock
```

| Thao tác | Endpoint |
| --- | --- |
| Xem tồn kho | `GET /inventory` |
| Điều chỉnh thủ công | `POST /inventory/{productId}/adjustments` |

MVP không cung cấp phân trang, tìm kiếm, lịch sử movement hoặc chỉnh trực tiếp
reserved stock.

---

## 2. Inventory fields

```json
{
  "productId": 1,
  "productName": "iPhone 15",
  "onHandStock": 10,
  "reservedStock": 3,
  "availableStock": 7
}
```

| Field | Type | Nguồn | Quy tắc |
| --- | --- | --- | --- |
| `productId` | integer | `products.id` | Product identifier |
| `productName` | string | `products.name` | Tên hiện tại của product |
| `onHandStock` | integer | `inventory_items.on_hand_stock` | Không âm |
| `reservedStock` | integer | `inventory_items.reserved_stock` | Không âm và không lớn hơn on-hand |
| `availableStock` | integer | computed | `onHandStock - reservedStock`; không lưu DB |

Product chưa có `inventory_items` được đọc như `0/0/0`. Inventory Management
hiển thị cả product active và inactive vì trạng thái bán hàng không làm mất nhu
cầu kiểm kê.

---

## 3. GET /inventory

### Response — `200 OK`

Trả một JSON array sắp xếp tăng dần theo `productId`:

```json
[
  {
    "productId": 1,
    "productName": "iPhone 15",
    "onHandStock": 10,
    "reservedStock": 3,
    "availableStock": 7
  }
]
```

Không có product trả `[]`.

---

## 4. POST /inventory/{productId}/adjustments

Điều chỉnh on-hand stock của một product. Client gửi mức thay đổi, không gửi số
lượng cuối cùng.

### Request

```json
{
  "delta": -2,
  "reason": "Damaged during stock count"
}
```

| Field | Type | Required | Quy tắc |
| --- | --- | --- | --- |
| `delta` | integer | yes | Khác `0`; số dương tăng và số âm giảm on-hand |
| `reason` | string | yes | Trimmed, non-blank, tối đa 500 ký tự |

Client không được gửi on-hand, reserved, available, movement type hoặc
timestamp.

### Response — `200 OK`

Trả inventory fields sau khi điều chỉnh:

```json
{
  "productId": 1,
  "productName": "iPhone 15",
  "onHandStock": 8,
  "reservedStock": 3,
  "availableStock": 5
}
```

### Side effects

Trong cùng một transaction, server:

1. Khóa product và inventory hiện tại.
2. Tính `newOnHandStock = onHandStock + delta`.
3. Từ chối nếu `newOnHandStock < 0` hoặc `newOnHandStock < reservedStock`.
4. Cập nhật on-hand và `updated_at`.
5. Ghi một `stock_movements` với `type = MANUAL_ADJUSTMENT`, `quantity = delta`
   và `note = reason`.

Nếu product chưa có inventory, delta dương tạo row `0/0` rồi áp dụng delta
trong cùng transaction. Delta âm bị từ chối.

---

## 5. Validation và lỗi

Error body:

```json
{
  "message": "Invalid inventory adjustment",
  "status": 400,
  "fieldErrors": {
    "reason": "Reason is required"
  }
}
```

| HTTP | Trường hợp |
| --- | --- |
| `400 Bad Request` | Body malformed; delta thiếu, bằng 0 hoặc không phải integer; reason blank hoặc quá 500 ký tự |
| `404 Not Found` | Product không tồn tại |
| `409 Conflict` | Adjustment làm on-hand âm hoặc nhỏ hơn reserved |

Request bị từ chối không thay đổi inventory và không ghi movement.

---

## 6. Atomicity và concurrency

Adjustment khóa product trước inventory, cùng thứ tự với order reservation. Việc
tạo inventory khi thiếu, cập nhật on-hand và ghi movement nằm trong một
transaction. Các request đồng thời không được làm mất update hoặc phá vỡ invariant
`0 <= reservedStock <= onHandStock`.

---

## 7. Test cases tối thiểu

1. Danh sách hiển thị đúng on-hand, reserved và available cho mọi product.
2. Product chưa có inventory hiển thị `0/0/0`.
3. Delta dương hoặc âm hợp lệ cập nhật on-hand/available và ghi đúng movement note.
4. Delta `0`, reason không hợp lệ hoặc body malformed trả 400 và không đổi dữ liệu.
5. Product không tồn tại trả 404.
6. Delta phá vỡ invariant trả 409 và không đổi inventory/movement.
7. Delta dương cho product chưa có inventory tạo row và movement atomically.
8. Adjustment đồng thời không làm mất update hoặc phá vỡ invariant.

---

## 8. Version History

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 2026-07-21 | Chốt stock fields, endpoints, adjustment rules và error semantics |
