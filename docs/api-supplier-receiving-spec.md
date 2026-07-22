# ShopFlow — Supplier Receiving API Specification

**Phiên bản:** 1.0

**Ngày cập nhật:** 22/07/2026

**Liên quan:**

- [SF-7: Receive Supplier Stock](https://tuanwork.atlassian.net/browse/SF-7)
- [SF-55: Define Supplier Receiving API Contract](https://tuanwork.atlassian.net/browse/SF-55)
- [`SRS.md`](./SRS.md) — FR-06, BR-11, BR-13, NFR-01
- [`database-schema.md`](./database-schema.md) — `products`, `inventory_items`,
  `receiving_records` và `stock_movements`

---

## 1. Tổng quan

API cho phép nhân viên kho ghi nhận một lần nhập hàng cho một product đã tồn tại.
Một receipt thành công tăng on-hand stock, lưu receiving record và ghi movement
audit trong cùng một transaction.

| Thao tác | Endpoint |
| --- | --- |
| Ghi nhận nhập hàng | `POST /receivings` |

MVP không có supplier master data, lịch sử receiving độc lập, phân trang hoặc
authentication. Supplier chỉ là tên tự do; `createdBy` luôn là `null` cho tới
khi một feature authentication cung cấp actor đáng tin cậy.

---

## 2. POST /receivings

Tạo một receiving record cho đúng một product và tăng tồn kho vật lý.

### Request

```json
{
  "productId": 1,
  "quantity": 20,
  "supplierName": "Acme Distribution",
  "note": "Invoice INV-2026-001"
}
```

| Field | Type | Required | Quy tắc |
| --- | --- | --- | --- |
| `productId` | integer | yes | JSON integer dương, vừa `BIGINT`; không nhận string, số lẻ hoặc coercion. |
| `quantity` | integer | yes | JSON integer dương, từ `1` đến `2,147,483,647`; không nhận zero, số âm, string hoặc số lẻ. |
| `supplierName` | string/null | no | Có thể bỏ hoặc `null`; nếu có giá trị thì trim, không rỗng và tối đa 255 ký tự. |
| `note` | string/null | no | Có thể bỏ hoặc `null`; nếu có giá trị thì trim, không rỗng và tối đa 500 ký tự. |

Client chỉ gửi bốn field trên. Không gửi `createdBy`, stock fields, movement
type, timestamp hay receiving ID.

Product active và inactive đều nhận hàng được. `active` chỉ quyết định việc
hiển thị trong catalog, không ngăn hoạt động kho.

### Response — `201 Created`

```json
{
  "id": 42,
  "productId": 1,
  "productName": "Mechanical Keyboard",
  "quantity": 20,
  "supplierName": "Acme Distribution",
  "note": "Invoice INV-2026-001",
  "createdAt": "2026-07-22T10:15:30Z",
  "createdBy": null,
  "onHandStock": 30,
  "reservedStock": 3,
  "availableStock": 27
}
```

Các stock field là trạng thái sau receipt. `availableStock` luôn được tính:

```text
availableStock = onHandStock - reservedStock
```

### Side effects

Trong cùng một transaction, server:

1. Khóa product đang nhận hàng, kể cả product inactive.
2. Khóa inventory hiện tại; nếu chưa có thì tạo row `0/0`.
3. Tăng `on_hand_stock` đúng bằng `quantity`; không thay đổi `reserved_stock`.
4. Insert một `receiving_records` row với supplier/note đã trim và `created_by = NULL`.
5. Insert một `stock_movements` row với `type = STOCK_RECEIVED`, quantity dương,
   `reference_type = RECEIVING`, `reference_id = receiving_records.id` và
   `created_by = NULL`.

Nếu bất kỳ bước nào thất bại, toàn bộ thay đổi bị rollback: không có inventory
row mới, receiving record hay movement dở dang.

### Validation và lỗi

Error body dùng format chung của feature:

```json
{
  "message": "Invalid supplier receiving",
  "status": 400,
  "fieldErrors": {
    "quantity": "Quantity must be greater than zero"
  }
}
```

| HTTP | Trường hợp |
| --- | --- |
| `400 Bad Request` | Body malformed; field thiếu; sai JSON type; số lẻ; giá trị ngoài range; quantity không dương; supplier/note blank hoặc quá giới hạn. |
| `404 Not Found` | `productId` không tồn tại. |
| `409 Conflict` | Receipt làm on-hand vượt `INT` range hoặc transaction không thể hoàn tất do xung đột đồng thời. |

Mọi request bị từ chối không thay đổi inventory, không tạo receiving record và
không ghi stock movement.

---

## 3. Atomicity và concurrency

Receipt khóa product trước inventory, cùng thứ tự với inventory adjustment và
order reservation. Nhờ đó hai request đồng thời cho product chưa có inventory
không thể cùng tạo hai inventory row: request sau thấy row vừa tạo sau khi
request đầu tiên commit. Hai receipt hợp lệ cùng lúc đều được cộng đủ, trừ khi
tổng on-hand vượt giới hạn `INT`.

Route Warehouse hiện tại chỉ là UI workflow, không phải security boundary. Khi
authentication được triển khai, actor phải được lấy từ server-side security
context; client vẫn không bao giờ được quyền gửi `createdBy`.

---

## 4. Test cases tối thiểu

1. Nhập hàng cho product active tăng đúng on-hand và available stock.
2. Nhập hàng cho product inactive cũng thành công.
3. Product chưa có inventory tạo row `0/0` rồi cộng quantity atomically.
4. Receiving response chứa record, stock sau cập nhật và `createdBy = null`.
5. Movement có `STOCK_RECEIVED`, quantity dương, `RECEIVING` reference đúng ID.
6. Body malformed, type coercion, số lẻ, zero/âm, range sai và text sai bị từ chối
   mà không để lại side effect.
7. Product không tồn tại trả `404`; overflow trả `409`.
8. Receipt đồng thời cho inventory có sẵn và inventory còn thiếu không mất update.
9. Lỗi khi ghi audit làm rollback inventory và receiving record.

---

## 5. Version History

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 2026-07-22 | Chốt contract supplier receiving, validation, audit và concurrency semantics. |
