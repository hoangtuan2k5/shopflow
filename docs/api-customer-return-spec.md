# ShopFlow — Customer Return API Specification

**Phiên bản:** 1.0

**Ngày cập nhật:** 26/07/2026

**Liên quan:**

- [SF-8: Process Customer Return](https://tuanwork.atlassian.net/browse/SF-8)
- [SF-18: Define Return Lifecycle Rules and API Contract](https://tuanwork.atlassian.net/browse/SF-18)
- [`SRS.md`](./SRS.md) — FR-07, BR-08 đến BR-10, BR-13, NFR-01
- [`database-schema.md`](./database-schema.md) — `orders`, `order_items`,
  `return_requests`, `return_request_items`, `inventory_items` và `stock_movements`

---

## 1. Tổng quan

API cho phép chủ shop và nhân viên kho xử lý return cho order đã giao. Return
request tham chiếu từng order item (partial return) và đi qua lifecycle:

```text
REQUESTED -> APPROVED -> RESTOCKED
REQUESTED -> REJECTED
```

`APPROVED` với `restockable = false` là trạng thái kết thúc nghiệp vụ mà không
nhập kho. Chỉ return `APPROVED` và `restockable = true` mới được nhân viên kho
xác nhận `RESTOCKED`; khi đó on-hand stock tăng theo từng return item và mỗi
item ghi một `RETURN_RESTOCK` movement (BR-09, BR-13).

| Thao tác | Endpoint |
| --- | --- |
| Xem danh sách return request | `GET /returns` |
| Xem order đủ điều kiện return | `GET /returns/orders` |
| Tạo return request | `POST /returns` |
| Chuyển trạng thái return | `PATCH /returns/{returnId}` |

MVP không refund, không gọi payment gateway (FR-07.9), không phân trang và
không authentication. Việc chỉ nhân viên kho xác nhận restock là UI boundary
của MVP, không phải authorization claim được backend enforce; `created_by`
của movement luôn là `null` cho tới khi có feature authentication.

## 2. GET /returns

Trả về mọi return request, mới nhất trước (`created_at DESC, id DESC`), kèm
items. Response `200 OK`:

```json
[
  {
    "id": 12,
    "orderId": 42,
    "status": "APPROVED",
    "reason": "Khách đổi ý",
    "restockable": true,
    "createdAt": "2026-07-26T09:00:00Z",
    "updatedAt": "2026-07-26T09:30:00Z",
    "items": [
      {
        "orderItemId": 7,
        "productId": 3,
        "productName": "Áo thun cotton",
        "quantity": 2
      }
    ]
  }
]
```

`restockable` chỉ có ý nghĩa sau khi duyệt; giá trị mặc định trước đó là
`false`.

## 3. GET /returns/orders

Read model cho Return Management UI khi tạo return: các order có
`delivery_status = DELIVERED`, mới nhất trước, kèm số lượng còn được return
cho từng order item. Response `200 OK`:

```json
[
  {
    "orderId": 42,
    "receiverName": "Nguyễn Văn A",
    "city": "Hà Nội",
    "totalAmount": 750000,
    "createdAt": "2026-07-20T08:00:00Z",
    "items": [
      {
        "orderItemId": 7,
        "productId": 3,
        "productName": "Áo thun cotton",
        "quantity": 3,
        "returnedQuantity": 2,
        "returnableQuantity": 1
      }
    ]
  }
]
```

- `returnedQuantity` là tổng quantity thuộc return request `REQUESTED`,
  `APPROVED` hoặc `RESTOCKED`; `REJECTED` không tính (FR-07.3).
- `returnableQuantity = quantity - returnedQuantity`.

## 4. POST /returns

Tạo một return request status `REQUESTED` cho một order đã `DELIVERED`.

### Request

```json
{
  "orderId": 42,
  "reason": "Khách đổi ý",
  "items": [{ "orderItemId": 7, "quantity": 2 }]
}
```

| Field | Bắt buộc | Quy tắc |
| --- | --- | --- |
| `orderId` | Có | Số nguyên dương trong dải 64-bit. |
| `reason` | Không | Nullable. Khi gửi phải có nội dung khác whitespace, tối đa 500 ký tự sau khi trim. |
| `items` | Có | Danh sách không rỗng, không lặp `orderItemId`. |
| `items[].orderItemId` | Có | Order item phải thuộc chính order đó (FR-07.2). |
| `items[].quantity` | Có | Số nguyên dương trong dải 32-bit (FR-07.2). |

### Response — `201 Created`

Body là return request vừa tạo, cùng shape với phần tử của `GET /returns`,
`status = REQUESTED`. Tạo return không thay đổi inventory.

## 5. PATCH /returns/{returnId}

Chuyển trạng thái return theo lifecycle. Request:

```json
{ "toStatus": "APPROVED", "restockable": true }
```

| Transition | Điều kiện | Ảnh hưởng |
| --- | --- | --- |
| `REQUESTED → APPROVED` | `restockable` bắt buộc (`true`/`false`) (FR-07.6) | Không đổi inventory |
| `REQUESTED → REJECTED` | Không gửi `restockable` | Không đổi inventory (FR-07.8) |
| `APPROVED → RESTOCKED` | Return phải `restockable = true`; không gửi `restockable` (FR-07.7) | On-hand tăng theo từng item, ghi movement |

Response `200 OK` với return request sau transition. Mọi transition khác
(kể cả `toStatus = REQUESTED`, lặp lại transition, hoặc restock return không
restockable) trả về `409` và không thay đổi dữ liệu.

## 6. Validation và lỗi

Error body dùng chung format với các feature khác:

```json
{ "message": "Invalid customer return", "status": 400, "fieldErrors": { "items": "..." } }
```

| Trường hợp | Status | Ghi chú |
| --- | --- | --- |
| Body malformed, field sai kiểu, `quantity` ≤ 0 hoặc fractional, `items` rỗng | `400` | `fieldErrors` theo field |
| `reason`/`restockable` vi phạm quy tắc transition | `400` | |
| Order item không thuộc order hoặc `orderItemId` lặp | `400` | |
| Order hoặc return không tồn tại | `404` | |
| Order chưa `DELIVERED` (BR-08) | `409` | |
| Tổng return vượt quantity đã mua (FR-07.3, BR-10) | `409` | Từ chối toàn bộ request |
| Transition không hợp lệ theo lifecycle | `409` | |
| Concurrent update không thể hoàn tất an toàn | `409` | |

## 7. Atomicity và concurrency

- `POST /returns` khóa order row (`SELECT ... FOR UPDATE`) để các request return
  đồng thời trên cùng order được tuần tự hóa; tổng quantity của return
  `REQUESTED`/`APPROVED`/`RESTOCKED` không bao giờ vượt lượng đã mua (FR-07.3).
- `PATCH /returns/{returnId}` khóa return row; transition lặp lại từ hai client
  chỉ có một bên thắng, bên còn lại nhận `409`.
- Xác nhận `RESTOCKED` chạy trong một transaction: với từng return item —
  tăng `on_hand_stock` của product tương ứng (khóa inventory row, guard
  overflow) và ghi một `stock_movements` row:

  - `type = RETURN_RESTOCK`
  - `quantity = +item quantity`
  - `reference_type = RETURN`
  - `reference_id = return_requests.id`
  - `created_by = null`

  Sau đó chuyển return sang `RESTOCKED`. Nếu bất kỳ bước nào thất bại, toàn bộ
  inventory, movement và status rollback.
- Return `REJECTED` hoặc `APPROVED` không restockable không tạo movement và
  không đổi inventory (FR-07.8, BR-09).

## 8. Schema decision

Schema hiện có (`return_requests`, `return_request_items`, `inventory_items`,
`stock_movements`) đã đủ; SF-8 không cần migration mới.

## 9. Test cases tối thiểu

1. Tạo return hợp lệ cho order `DELIVERED` → `201`, status `REQUESTED`, inventory không đổi.
2. Tạo return cho order chưa `DELIVERED` → `409`, không ghi dữ liệu.
3. Tạo return với order item không thuộc order → `400`.
4. Tổng quantity vượt lượng đã mua (tính cả return trước đó, trừ `REJECTED`) → `409`.
5. Duyệt với `restockable` → `200`, status `APPROVED`, inventory không đổi.
6. Từ chối → `200`, status `REJECTED`, không movement.
7. Xác nhận restock return restockable → `200`, on-hand tăng theo từng item, mỗi item một `RETURN_RESTOCK` movement với reference `RETURN`.
8. Xác nhận restock return không restockable hoặc transition sai lifecycle → `409`, không side effect.
9. Hai request đồng thời tranh phần quantity còn lại → đúng một bên thành công.
10. Hai xác nhận restock đồng thời → chỉ một movement set được ghi.

## 10. Version History

| Phiên bản | Ngày | Thay đổi |
| --- | --- | --- |
| 1.0 | 26/07/2026 | Contract đầu tiên cho customer return (SF-18). |
