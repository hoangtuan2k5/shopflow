# ShopFlow — Payment Simulation API Specification

**Phiên bản:** 1.1

**Ngày cập nhật:** 26/07/2026

**Liên quan:**

- [SF-4: Simulate Order Payment](https://tuanwork.atlassian.net/browse/SF-4)
- [SF-40: Define Payment Simulation States](https://tuanwork.atlassian.net/browse/SF-40)
- [`SRS.md`](./SRS.md) — FR-03, BR-04, BR-05
- [`database-schema.md`](./database-schema.md) — `orders`, `payments`, inventory và stock movements

---

## 1. Tổng quan

API nhận một kết quả thanh toán do người dùng demo chọn cho order đang
`PENDING_PAYMENT`. Hệ thống tạo đúng một payment attempt bằng phương thức và số
tiền đã lưu trên order, rồi xử lý attempt thành trạng thái kết thúc trong cùng
transaction.

| Thuộc tính | Giá trị |
| --- | --- |
| Endpoint | `POST /orders/{orderRef}/payments` |
| Content-Type | `application/json` |
| Currency | `VND` |
| Phạm vi | Mô phỏng CARD; không gọi payment gateway thật |

MVP không hỗ trợ retry, refund, webhook, polling, COD hoặc nhiều payment attempt.

### Vì sao định danh bằng `orderRef` chứ không phải `orderId`

Thanh toán mở cho khách vãng lai nên không thể chặn bằng vai trò. Nếu đường dẫn
mang `orderId` — một `BIGSERIAL` tăng dần — thì bất kỳ ai cũng dò được id của
người khác và:

- gửi `FAILED` để ép đơn của họ sang `PAYMENT_FAILED`, giải phóng tồn kho đang giữ;
- gửi `SUCCESS` để đánh dấu đơn của họ đã thanh toán mà không trả tiền.

`orderRef` là một UUID ngẫu nhiên gắn với order lúc tạo và trả về trong response
của `POST /orders`. Nó hoạt động như một capability: chỉ người nhận được tham
chiếu mới thanh toán được cho order đó. Order vẫn được tham chiếu nội bộ bằng
`orderId`; chỉ đường thanh toán dùng `orderRef`.

`orderRef` không tồn tại hoặc sai định dạng đều trả `404`, không phân biệt.

---

## 2. Request

### Thành công

```json
{
  "result": "SUCCESS"
}
```

### Thất bại

```json
{
  "result": "FAILED",
  "failureReason": "Declined by simulation"
}
```

### Hết hạn

```json
{
  "result": "EXPIRED",
  "failureReason": "Expired by simulation"
}
```

| Field | Type | Required | Quy tắc |
| --- | --- | --- | --- |
| `result` | string enum | yes | `SUCCESS`, `FAILED` hoặc `EXPIRED` |
| `failureReason` | string | với FAILED/EXPIRED | Non-blank, tối đa 500 ký tự; không được gửi với SUCCESS |

Client không gửi `method`, `amount`, payment/order status hoặc timestamp. Server
lấy `method` và `amount` từ order đã khóa.

---

## 3. Response thành công — `200 OK`

```json
{
  "id": 7,
  "orderId": 42,
  "method": "CARD",
  "status": "SUCCESS",
  "amount": 2190000,
  "paidAt": "2026-07-21T09:00:00Z",
  "failedReason": null,
  "createdAt": "2026-07-21T09:00:00Z",
  "orderStatus": "PAID"
}
```

`status` là kết quả kết thúc tương ứng: `SUCCESS`, `FAILED` hoặc `EXPIRED`.
`paidAt` chỉ có giá trị với `SUCCESS`; `failedReason` chỉ có giá trị với
`FAILED`/`EXPIRED`. Các amount JSON là số nguyên VND dù schema vật lý dùng
`NUMERIC(12,2)`.

### Side effects

| Result | Payment | Order | Inventory |
| --- | --- | --- | --- |
| `SUCCESS` | `SUCCESS`, ghi `paidAt` | `PAID` | Giữ nguyên reservation |
| `FAILED` | `FAILED`, ghi reason | `PAYMENT_FAILED` | Release toàn bộ reservation |
| `EXPIRED` | `EXPIRED`, ghi reason | `PAYMENT_FAILED` | Release toàn bộ reservation |

Mỗi item được release tạo một movement `PAYMENT_FAILED_RELEASE`, quantity âm,
`reference_type = ORDER`, `reference_id = orderId`. `on_hand_stock` không đổi.

---

## 4. Validation và lỗi

Error body tối thiểu:

```json
{
  "message": "Invalid payment request",
  "status": 400
}
```

| HTTP | Trường hợp |
| --- | --- |
| `400 Bad Request` | Body malformed; result thiếu/không hợp lệ; failureReason sai quy tắc |
| `404 Not Found` | Không có order mang `orderRef` |
| `409 Conflict` | Order không còn `PENDING_PAYMENT` hoặc đã có payment attempt |

Request bị từ chối không tạo payment và không đổi order, inventory hay movement.

---

## 5. Atomicity và concurrency

Server khóa order trước khi kiểm tra trạng thái và payment attempt. Việc tạo
`PENDING`, xử lý kết quả, đổi order, release inventory và ghi movements nằm trong
một transaction. Hai request đồng thời cho cùng order chỉ có tối đa một request
thành công và tối đa một payment row được tạo.

---

## 6. Test cases tối thiểu

1. `SUCCESS` tạo một payment, chuyển order sang `PAID`, không đổi reservation.
2. `FAILED`/`EXPIRED` chuyển order sang `PAYMENT_FAILED`, release đủ mọi item và
   ghi movement tương ứng.
3. Order không tồn tại trả 404 và không ghi dữ liệu.
7. Gọi endpoint bằng `orderId` tuần tự thay vì `orderRef` trả 404; order, payment và
   reservation đều không đổi.
4. Order sai trạng thái hoặc payment lặp lại trả 409 và không ghi thêm attempt.
5. Body sai trả 400.
6. Hai request đồng thời chỉ tạo tối đa một attempt.

---

## 7. Version History

| Version | Date | Changes |
| --- | --- | --- |
| 1.1 | 2026-07-26 | Đổi định danh đường thanh toán từ `orderId` sang `orderRef` để chặn dò id |
| 1.0 | 2026-07-21 | Chốt payment states, endpoint, payload, side effects và error semantics |
