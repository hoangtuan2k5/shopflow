# ShopFlow — Payment Simulation API Specification

**Phiên bản:** 1.3 (payment ownership SF-148)

**Ngày cập nhật:** 06/11/2026

> SF-148 đã thực hiện login + ownership trong source trên develop. Production
> chỉ đổi sau release được duyệt. `orderRef` vẫn opaque nhưng không đủ quyền
> thanh toán; xem [Customer Identity Contract](api-customer-identity-spec.md).

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

`orderRef` là UUID ngẫu nhiên giữ public API tách khỏi ID tuần tự nội bộ.
Từ SF-148, caller còn phải đăng nhập và là chính `orders.account_user_id`.
Server lấy user ID từ principal, không từ JSON/email hoặc quyền SHOP_OWNER.
Guest có CSRF hợp lệ vẫn401. Sau structural validation và SELECT FOR UPDATE,
owner được kiểm trước status/duplicate và trước payment/status/reservation write.

Wrong-owner, reference sai/không tồn tại và legacy owner null cùng trả404
`Order not found`, không đổi dữ liệu. Đúng owner nhưng order không eligible hoặc
đã có payment vẫn409. Legacy PENDING_PAYMENT giữ nguyên reservation; không
backfill owner theo snapshot hoặc tự hủy/release.

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
| `404 Not Found` | Reference sai/không tồn tại, wrong-owner hoặc legacy owner null |
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
| 1.3 | 2026-11-06 | Triển khai login/ownership SF-148 trong source; giữ legacy order null owner, không auto-release. |
| 1.2 | 2026-11-04 | Thêm delta đích login/ownership và legacy guest policy SF-141; chưa triển khai. |
| 1.1 | 2026-07-26 | Đổi định danh đường thanh toán từ `orderId` sang `orderRef` để chặn dò id |
| 1.0 | 2026-07-21 | Chốt payment states, endpoint, payload, side effects và error semantics |

## 8. Delta đích SF-141 — payment ownership

- Giữ URL `POST /orders/{orderRef}/payments`, payload, response, payment mô
  phỏng CARD và transaction reserve/release. OrderRef vẫn khó đoán nhưng
  **không còn đủ quyền thanh toán** nếu thiếu đúng account.
- Cookie/CSRF/Origin hợp lệ là prerequisite. Thiếu session trả 401; thiếu/sai
  CSRF hoặc Origin sai trả 403, đều trước side effect.
- Sau structural validation, lấy owner cùng SELECT FOR UPDATE của order.
  Kiểm `account_user_id = principal.userId` trước status/duplicate attempt và
  mọi write. Áp dụng cả CUSTOMER/WAREHOUSE/SHOP_OWNER/demo, không bypass theo role.
- Wrong-owner, legacy owner null, reference sai/không tồn tại đều 404 với
  body `{"message":"Order not found","status":404}`. Đúng owner nhưng order
  không eligible hoặc duplicate payment giữ 409; body malformed giữ 400.
- Legacy guest PENDING_PAYMENT giữ nguyên dữ liệu/reservation, chặn storefront
  payment; thống kê trước rollout, không tự gán owner theo email, hủy, expire
  hoặc release. Không tạo API claim đơn cũ hay staff payment bypass.
- Test rejection phải assert payment/order/stock/movement không đổi, gồm
  account A/B với role khác nhau; correct-owner concurrent payment vẫn chỉ
  có một attempt. Xem CI-12/13/15 trong contract SF-141.
