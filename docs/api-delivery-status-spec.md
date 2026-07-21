# ShopFlow — Delivery Status API Specification

**Phiên bản:** 1.0

**Ngày cập nhật:** 21/07/2026

**Liên quan:**

- [SF-5: Update Delivery Status](https://tuanwork.atlassian.net/browse/SF-5)
- [SF-49: Define Delivery Status API Contract and Lifecycle](https://tuanwork.atlassian.net/browse/SF-49)
- [`SRS.md`](./SRS.md) — FR-04, BR-06, BR-07, NFR-01, NFR-04 đến NFR-06
- [`database-schema.md`](./database-schema.md) — `orders`, `order_items`,
  `delivery_status_history`, inventory và stock movements

---

## 1. Tổng quan

API cung cấp hàng đợi delivery cho các order đã `PAID` và cho phép Warehouse
hoặc Shop Owner chuyển trạng thái theo đúng thứ tự:

```text
NONE -> PREPARING -> SHIPPED -> DELIVERED
```

| Thao tác | Endpoint |
| --- | --- |
| Xem các order đã thanh toán | `GET /deliveries` |
| Chuyển delivery status | `PATCH /orders/{orderId}/delivery` |

MVP không hỗ trợ bỏ qua bước, đi lùi, hủy delivery, shipping provider, tìm kiếm,
phân trang, ghi chú hoặc tự khai báo actor từ client.

---

## 2. Delivery lifecycle

| Order status | Delivery hiện tại | Delivery tiếp theo | Kết quả |
| --- | --- | --- | --- |
| `PAID` | `NONE` | `PREPARING` | Hợp lệ |
| `PAID` | `PREPARING` | `SHIPPED` | Hợp lệ |
| `PAID` | `SHIPPED` | `DELIVERED` | Hợp lệ; hoàn tất xuất kho |

Mọi tổ hợp khác trả `409 Conflict`. Transition thành công cập nhật
`orders.delivery_status`, `orders.updated_at` và thêm đúng một row append-only
vào `delivery_status_history`.

`changedBy` được lưu `null` trong MVP vì hệ thống chưa có authentication để xác
định actor đáng tin cậy. Client không được gửi trường này.

---

## 3. GET /deliveries

Trả mọi order có `orders.status = PAID`, bao gồm delivery đã `DELIVERED`. Order
được sắp xếp theo `createdAt`, rồi `orderId` tăng dần. Items sắp xếp theo
`productId`; history sắp xếp theo `changedAt`, rồi history id tăng dần.

### Response — `200 OK`

```json
[
  {
    "orderId": 42,
    "orderStatus": "PAID",
    "deliveryStatus": "PREPARING",
    "receiverName": "Nguyen Van A",
    "city": "Ho Chi Minh City",
    "totalAmount": 2190000,
    "createdAt": "2026-07-21T09:00:00Z",
    "items": [
      {
        "productId": 7,
        "productName": "Mechanical Keyboard",
        "quantity": 2
      }
    ],
    "history": [
      {
        "fromStatus": "NONE",
        "toStatus": "PREPARING",
        "changedAt": "2026-07-21T10:00:00Z",
        "changedBy": null
      }
    ]
  }
]
```

Không có order phù hợp trả `[]`.

| Field | Type | Quy tắc |
| --- | --- | --- |
| `orderId` | integer | `orders.id` |
| `orderStatus` | string enum | Luôn là `PAID` trong endpoint này |
| `deliveryStatus` | string enum | `NONE`, `PREPARING`, `SHIPPED`, `DELIVERED` |
| `receiverName` | string | Snapshot người nhận của order |
| `city` | string | Snapshot địa chỉ giao hàng ở mức thành phố |
| `totalAmount` | number | Số nguyên VND ở API |
| `createdAt` | ISO-8601 string | Thời điểm tạo order |
| `items` | array | Snapshot product và quantity của order |
| `history` | array | Các transition đã lưu, theo thứ tự thời gian |

---

## 4. PATCH /orders/{orderId}/delivery

### Request

```json
{
  "toStatus": "SHIPPED"
}
```

| Field | Type | Required | Quy tắc |
| --- | --- | --- | --- |
| `toStatus` | string enum | yes | Phải là trạng thái hợp lệ kế tiếp |

Client không gửi order status, delivery status hiện tại, inventory effect,
timestamp, note hoặc actor.

### Response — `200 OK`

Trả delivery order đã cập nhật theo cùng shape của một phần tử trong
`GET /deliveries`.

### Side effects

Với mọi transition hợp lệ, server cập nhật current status và thêm history gồm
`fromStatus`, `toStatus`, thời điểm server và `changedBy = null`.

Riêng `SHIPPED -> DELIVERED`, với mỗi order item server đồng thời:

- giảm `on_hand_stock` theo quantity;
- giảm `reserved_stock` theo quantity;
- ghi `stock_movements.type = DELIVERY_COMPLETED`;
- ghi movement `quantity = -orderItem.quantity`, `reference_type = ORDER` và
  `reference_id = orderId`.

---

## 5. Validation và lỗi

Error body:

```json
{
  "message": "Invalid delivery transition: NONE -> SHIPPED",
  "status": 409
}
```

| HTTP | Trường hợp |
| --- | --- |
| `400 Bad Request` | Body malformed; `toStatus` thiếu hoặc không thuộc enum |
| `404 Not Found` | Không có order mang `orderId` |
| `409 Conflict` | Order chưa `PAID`; bỏ bước, đi lùi hoặc lặp status; inventory không thể hoàn tất delivery |

Request bị từ chối không thay đổi order, history, inventory hoặc stock movement.

---

## 6. Atomicity và concurrency

Server khóa order trước khi đọc trạng thái và kiểm tra transition. Với
`DELIVERED`, order items được xử lý theo `productId` tăng dần và inventory chỉ
được giảm khi vẫn thỏa `0 <= reservedStock <= onHandStock`.

Cập nhật order, history, inventory và movements nằm trong một transaction. Hai
request đồng thời cho cùng order chỉ có tối đa một request thành công; stock
không được giảm hai lần. Bất kỳ lỗi nào rollback toàn bộ transaction.

---

## 7. Test cases tối thiểu

1. Danh sách chỉ chứa order `PAID`, nhưng chứa đủ mọi delivery status.
2. Items và history được trả đúng dữ liệu và thứ tự.
3. Ba transition hợp lệ cập nhật status và thêm đúng một history mỗi lần.
4. `DELIVERED` giảm đúng on-hand/reserved và ghi movement cho mọi item.
5. Order chưa paid, bỏ bước, đi lùi hoặc lặp status trả 409 và không đổi dữ liệu.
6. Body sai trả 400; order không tồn tại trả 404.
7. Inventory conflict rollback status, history, inventory và movement.
8. Hai request hoàn tất cùng order không double-decrement hoặc ghi audit trùng.

---

## 8. Version History

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 2026-07-21 | Chốt read model, lifecycle, payload, inventory effect và concurrency semantics |
