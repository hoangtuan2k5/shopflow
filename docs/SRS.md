# ShopFlow Software Requirements Specification

**Phiên bản:** 1.0

**Ngày lập:** 21/06/2026

**Trạng thái:** Baseline cho MVP

**Phạm vi:** Epic [SF-1](https://tuanwork.atlassian.net/browse/SF-1)

**Tài liệu liên quan:**

- [`../README.md`](../README.md) - phạm vi dự án, roadmap và công nghệ hiện tại.
- [`database-schema.md`](./database-schema.md) - mô hình dữ liệu và ràng buộc vật lý.
- [`database-schema.sql`](./database-schema.sql) - DDL PostgreSQL tham chiếu.
- [`api-product-catalog-spec.md`](./api-product-catalog-spec.md) - API contract của Product Catalog.

---

## 1. Mục đích tài liệu

Tài liệu này định nghĩa yêu cầu phần mềm cho ShopFlow, một hệ thống MVP mô phỏng
quy trình bán hàng và quản lý tồn kho của shop online quy mô nhỏ. SRS là baseline
chung để Business Analyst, backend, frontend và QA thống nhất phạm vi, thuật ngữ,
quy tắc nghiệp vụ và điều kiện nghiệm thu.

SRS mô tả yêu cầu ở cấp dự án. Các Jira issue là đơn vị lập kế hoạch và truy vết,
không phải cấu trúc của tài liệu này.

### 1.1 Đối tượng đọc

- Chủ shop và người duyệt nghiệp vụ.
- Business Analyst và Product Owner.
- Backend, frontend và DevOps.
- QA và người thực hiện demo/nghiệm thu.

### 1.2 Quy ước yêu cầu

- `BẮT BUỘC` chỉ hành vi cần có để nghiệm thu MVP.
- `KHÔNG` chỉ hành vi bị cấm hoặc nằm ngoài phạm vi.
- Mỗi yêu cầu chức năng có mã `FR-xx`; mỗi quy tắc nghiệp vụ có mã `BR-xx`.
- Tên trạng thái kỹ thuật dùng `UPPER_SNAKE_CASE`; tên hiển thị có thể được dịch
  trên giao diện.

---

## 2. Tổng quan sản phẩm

### 2.1 Mục tiêu

ShopFlow cho phép khách hàng xem sản phẩm và tạo đơn; mô phỏng kết quả thanh
toán; cho phép chủ shop và nhân viên kho theo dõi giao hàng, quản lý tồn kho,
nhập hàng, xử lý hàng hoàn và nhận biết sản phẩm sắp hết hàng.

Giá trị chính của MVP là kiểm soát chính xác `on-hand`, `reserved` và
`available stock`, qua đó giảm nguy cơ nhận đơn vượt quá lượng hàng có thể bán.

### 2.2 Phạm vi chức năng

**Trong phạm vi:**

- Product Catalog và chi tiết sản phẩm.
- Tạo order theo nguyên tắc tất cả hoặc không tạo.
- Payment success/failure/expiry ở mức mô phỏng.
- Delivery status và lịch sử thay đổi trạng thái.
- Xem tồn kho và điều chỉnh tồn kho thủ công.
- Nhập hàng từ supplier.
- Xử lý return và restock có điều kiện.
- Đánh dấu low stock theo ngưỡng của từng product.
- Audit cơ bản cho thay đổi inventory và delivery status.

**Ngoài phạm vi:**

- Payment gateway, đối soát, refund hoặc kế toán thật.
- Shipping provider, tracking API hoặc tính cước vận chuyển thật.
- Authentication và phân quyền chi tiết; các role trong MVP dùng để phân luồng
  giao diện và nghiệp vụ.
- Loyalty, promotion, cart phức tạp, thuế và báo cáo doanh thu nâng cao.
- Xử lý phân tán hoặc bảo đảm chống tranh chấp tồn kho ở tải đồng thời cao.
- Email, SMS hoặc push notification cho low-stock alert.

### 2.3 Kế hoạch phát hành

| Giai đoạn | Thời gian | Phạm vi |
| --- | --- | --- |
| Sprint 1 | 18/05/2026 - 13/06/2026 | Foundation MVP, domain model, catalog, order creation, payment simulation, inventory baseline, monorepo và CI |
| Sprint 2 | 15/06/2026 - 29/06/2026 | Delivery status, supplier receiving, customer return và low-stock alert |

Jira có thể chuyển issue chưa hoàn thành sang sprint sau. Nhãn `sprint-1` trong
issue thể hiện nguồn gốc phạm vi, không thay thế lịch sprint hiện tại trong
README.

---

## 3. Stakeholder và actor

| Actor | Mục tiêu | Quyền nghiệp vụ trong MVP |
| --- | --- | --- |
| Khách hàng | Chọn sản phẩm và đặt hàng | Xem catalog/chi tiết, tạo order, thực hiện payment mô phỏng |
| Nhân viên kho | Duy trì tồn kho và xử lý hàng | Xem/điều chỉnh stock, nhập hàng, cập nhật delivery, xử lý return |
| Chủ shop | Theo dõi hoạt động bán hàng | Theo dõi order/inventory, cập nhật delivery, xử lý return và low-stock alert |
| Hệ thống | Bảo đảm tính nhất quán | Tính stock, validate transition, lưu snapshot và audit movement |

Role chưa được bảo vệ bằng authentication thật trong MVP. Việc một màn hình gắn
với role không được hiểu là đã có cơ chế authorization production.

---

## 4. Thuật ngữ nghiệp vụ

| Thuật ngữ | Định nghĩa |
| --- | --- |
| Product | Sản phẩm shop đang hoặc đã từng kinh doanh |
| Order | Giao dịch đặt hàng gồm thông tin khách, địa chỉ giao và một hoặc nhiều item |
| OrderItem | Snapshot sản phẩm, đơn giá và số lượng tại thời điểm tạo order |
| Payment | Một lần thử thanh toán mô phỏng cho order |
| Delivery status | Trạng thái xử lý giao hàng, độc lập với trạng thái payment/order |
| On-hand stock | Số lượng vật lý đang được ghi nhận trong kho |
| Reserved stock | Phần on-hand đã giữ cho order chưa hoàn tất giao hàng |
| Available stock | Số lượng có thể nhận order mới: `on_hand_stock - reserved_stock` |
| Stock movement | Bản ghi append-only giải thích một thay đổi tồn kho |
| Return request | Yêu cầu trả một phần hoặc toàn bộ item của order đã giao |
| Snapshot | Bản sao dữ liệu tại thời điểm giao dịch, không đổi theo master data về sau |
| Low stock | Trạng thái khi available stock nhỏ hơn hoặc bằng ngưỡng của product |

---

## 5. Yêu cầu chức năng

### 5.1 FR-01 - Product Catalog

Nguồn: [SF-2](https://tuanwork.atlassian.net/browse/SF-2),
[SF-36](https://tuanwork.atlassian.net/browse/SF-36).

- **FR-01.1:** Hệ thống BẮT BUỘC trả về danh sách product có `active = true`.
- **FR-01.2:** Danh sách BẮT BUỘC hiển thị `name`, `price` và `stockStatus`.
- **FR-01.3:** Chi tiết product BẮT BUỘC có `name`, `description`, `price` và
  `stockStatus`.
- **FR-01.4:** `stockStatus` BẮT BUỘC là `IN_STOCK` khi available stock lớn hơn
  0; các trường hợp còn lại là `OUT_OF_STOCK`.
- **FR-01.5:** Product chưa có `InventoryItem` BẮT BUỘC được xem là
  `OUT_OF_STOCK`.
- **FR-01.6:** Product không tồn tại hoặc inactive KHÔNG được trả về ở API chi
  tiết công khai.

API contract chuẩn cho yêu cầu này nằm trong
[`api-product-catalog-spec.md`](./api-product-catalog-spec.md).

### 5.2 FR-02 - Tạo order

Nguồn: [SF-3](https://tuanwork.atlassian.net/browse/SF-3),
[SF-11](https://tuanwork.atlassian.net/browse/SF-11),
[SF-12](https://tuanwork.atlassian.net/browse/SF-12),
[SF-35](https://tuanwork.atlassian.net/browse/SF-35),
[SF-43](https://tuanwork.atlassian.net/browse/SF-43).

- **FR-02.1:** Khách hàng BẮT BUỘC cung cấp tên khách hàng, tên và số điện thoại
  người nhận, địa chỉ, thành phố và ít nhất một item có quantity lớn hơn 0.
  Email, số điện thoại khách hàng và district là optional trong physical schema.
- **FR-02.2:** Trước khi tạo order, hệ thống BẮT BUỘC kiểm tra available stock
  cho từng item theo dữ liệu hiện tại.
- **FR-02.3:** Nếu bất kỳ item nào thiếu stock, hệ thống KHÔNG được tạo order,
  order item, reservation hoặc stock movement cho request đó.
- **FR-02.4:** Khi tất cả item hợp lệ, hệ thống BẮT BUỘC tạo order ở
  `PENDING_PAYMENT` và reserve đúng quantity cho từng product trong cùng một
  transaction.
- **FR-02.5:** Hệ thống BẮT BUỘC snapshot tên product, đơn giá, thông tin khách
  hàng và địa chỉ giao tại thời điểm tạo order.
- **FR-02.6:** `total_amount` BẮT BUỘC bằng tổng `unit_price * quantity` của các
  order item.
- **FR-02.7:** Kết quả tạo thành công BẮT BUỘC trả tối thiểu order identifier và
  status để tiếp tục payment.
- **FR-02.8:** Thông báo thiếu stock BẮT BUỘC cho biết product nào không đáp ứng
  quantity yêu cầu.

### 5.3 FR-03 - Payment mô phỏng

Nguồn: [SF-4](https://tuanwork.atlassian.net/browse/SF-4),
[SF-40](https://tuanwork.atlassian.net/browse/SF-40),
[SF-41](https://tuanwork.atlassian.net/browse/SF-41).

- **FR-03.1:** Hệ thống BẮT BUỘC chỉ nhận kết quả payment cho order đang ở
  `PENDING_PAYMENT`.
- **FR-03.2:** Mỗi lần thử payment BẮT BUỘC được ghi thành một Payment record với
  amount bằng `total_amount` của order; không ghi đè lịch sử attempt.
- **FR-03.3:** Kết quả mô phỏng thành công BẮT BUỘC chuyển Payment thành
  `SUCCESS`, Order thành `PAID` và giữ nguyên reserved stock.
- **FR-03.4:** Kết quả thất bại hoặc hết hạn BẮT BUỘC chuyển Payment thành
  `FAILED` hoặc `EXPIRED`, Order thành `PAYMENT_FAILED` và release toàn bộ
  reservation của order.
- **FR-03.5:** Hệ thống BẮT BUỘC từ chối payment lặp lại cho order không còn ở
  `PENDING_PAYMENT`.
- **FR-03.6:** Kết quả payment hoàn toàn do hệ thống/demo input mô phỏng; hệ
  thống KHÔNG gọi payment gateway thật.

Luồng nghiệm thu bắt buộc là payment mô phỏng hoàn tất trước delivery. Cột
`payment_method` và giá trị `COD` trong database là khả năng lưu trữ dự phòng;
quy tắc COD chưa có acceptance criteria trong Jira và không thuộc baseline này.

### 5.4 FR-04 - Cập nhật delivery status

Nguồn: [SF-5](https://tuanwork.atlassian.net/browse/SF-5).

- **FR-04.1:** Chỉ order `PAID` mới được chuyển delivery status từ `NONE` sang
  `PREPARING`.
- **FR-04.2:** Transition hợp lệ BẮT BUỘC theo thứ tự `NONE -> PREPARING ->
  SHIPPED -> DELIVERED`; hệ thống KHÔNG cho phép bỏ qua bước hoặc đi lùi trong
  MVP.
- **FR-04.3:** Mỗi transition BẮT BUỘC cập nhật status hiện tại và thêm một bản
  ghi lịch sử gồm trạng thái trước, trạng thái sau, thời điểm và người thực hiện
  nếu có.
- **FR-04.4:** Khi chuyển sang `DELIVERED`, hệ thống BẮT BUỘC giảm cả on-hand và
  reserved stock theo quantity của order, đồng thời ghi stock movement.
- **FR-04.5:** Transition không hợp lệ BẮT BUỘC bị từ chối và không thay đổi dữ
  liệu.

### 5.5 FR-05 - Quản lý inventory

Nguồn: [SF-6](https://tuanwork.atlassian.net/browse/SF-6),
[SF-14](https://tuanwork.atlassian.net/browse/SF-14),
[SF-15](https://tuanwork.atlassian.net/browse/SF-15).

- **FR-05.1:** Màn hình inventory BẮT BUỘC hiển thị product, on-hand, reserved
  và available stock.
- **FR-05.2:** Available stock BẮT BUỘC được tính tại thời điểm đọc, không lưu
  thành cột độc lập.
- **FR-05.3:** Nhân viên kho được phép điều chỉnh on-hand stock thủ công với một
  lý do không rỗng.
- **FR-05.4:** Điều chỉnh KHÔNG được làm on-hand âm hoặc làm reserved lớn hơn
  on-hand.
- **FR-05.5:** Mọi điều chỉnh thành công BẮT BUỘC cập nhật InventoryItem và thêm
  `MANUAL_ADJUSTMENT` StockMovement trong cùng transaction.

### 5.6 FR-06 - Nhập hàng từ supplier

Nguồn: [SF-7](https://tuanwork.atlassian.net/browse/SF-7).

- **FR-06.1:** Nhân viên kho được phép tạo receiving record cho product đã tồn
  tại.
- **FR-06.2:** Quantity nhập BẮT BUỘC lớn hơn 0; giá trị không hợp lệ phải bị từ
  chối mà không thay đổi stock.
- **FR-06.3:** Nhập hàng thành công BẮT BUỘC tăng on-hand stock, lưu receiving
  record và ghi `STOCK_RECEIVED` StockMovement trong cùng transaction.
- **FR-06.4:** Supplier chỉ là tên tự do trong MVP; hệ thống không quản lý
  supplier master data.

### 5.7 FR-07 - Xử lý return

Nguồn: [SF-8](https://tuanwork.atlassian.net/browse/SF-8),
[SF-17](https://tuanwork.atlassian.net/browse/SF-17),
[SF-18](https://tuanwork.atlassian.net/browse/SF-18).

- **FR-07.1:** Return chỉ được tạo cho order có delivery status `DELIVERED`.
- **FR-07.2:** Return BẮT BUỘC chỉ tham chiếu order item thuộc chính order đó và
  có quantity lớn hơn 0.
- **FR-07.3:** Tổng quantity return không bị từ chối của một order item KHÔNG
  được vượt quantity đã mua.
- **FR-07.4:** Return request mới BẮT BUỘC có status `REQUESTED`.
- **FR-07.5:** Chủ shop hoặc nhân viên kho được phép chuyển `REQUESTED` sang
  `APPROVED` hoặc `REJECTED`.
- **FR-07.6:** Khi duyệt, người xử lý BẮT BUỘC xác định hàng có thể nhập lại kho
  hay không bằng `restockable`.
- **FR-07.7:** Return được duyệt và restockable BẮT BUỘC tăng on-hand stock theo
  quantity, ghi `RETURN_RESTOCK` StockMovement và chuyển sang `RESTOCKED`.
- **FR-07.8:** Return bị từ chối hoặc không restockable KHÔNG được thay đổi
  inventory.
- **FR-07.9:** MVP KHÔNG thực hiện refund hoặc gọi payment gateway.

Trong schema hiện tại, `APPROVED` với `restockable = false` là trạng thái kết
thúc nghiệp vụ mà không nhập kho; `APPROVED` với `restockable = true` phải tiếp
tục sang `RESTOCKED`.

### 5.8 FR-08 - Low-stock alert

Nguồn: [SF-9](https://tuanwork.atlassian.net/browse/SF-9).

- **FR-08.1:** Mỗi product có thể cấu hình `low_stock_threshold` không âm; giá
  trị null nghĩa là không theo dõi cảnh báo.
- **FR-08.2:** Product BẮT BUỘC được đánh dấu `LOW_STOCK` khi available stock
  nhỏ hơn hoặc bằng threshold.
- **FR-08.3:** Dấu hiệu `LOW_STOCK` BẮT BUỘC được gỡ khi available stock tăng
  vượt threshold.
- **FR-08.4:** Trạng thái low stock được suy ra từ dữ liệu hiện tại; MVP không
  yêu cầu lưu một cờ độc lập hoặc gửi notification ra ngoài hệ thống.

---

## 6. Quy tắc nghiệp vụ

| Mã | Quy tắc |
| --- | --- |
| BR-01 | `available_stock = on_hand_stock - reserved_stock` |
| BR-02 | On-hand và reserved không âm; reserved không lớn hơn on-hand |
| BR-03 | Order nhiều item được xử lý atomically; thiếu stock ở một item thì từ chối toàn bộ order |
| BR-04 | Tạo order thành công làm tăng reserved, không làm giảm on-hand |
| BR-05 | Payment thất bại/hết hạn làm giảm reserved đúng quantity đã giữ |
| BR-06 | Delivery chỉ bắt đầu sau payment mô phỏng thành công và phải đi đúng thứ tự |
| BR-07 | Khi delivered, on-hand và reserved cùng giảm theo quantity đã giao |
| BR-08 | Return chỉ hợp lệ sau khi delivery đã `DELIVERED` |
| BR-09 | Chỉ return đã duyệt và restockable mới làm tăng on-hand |
| BR-10 | Tổng lượng return hợp lệ của một order item không vượt lượng đã mua |
| BR-11 | Receiving quantity phải lớn hơn 0 |
| BR-12 | Manual adjustment phải có lý do và không phá vỡ invariant tồn kho |
| BR-13 | Mọi nghiệp vụ thay đổi stock phải có StockMovement tương ứng |
| BR-14 | Low stock được xác định từ available stock và threshold của product |
| BR-15 | Thông tin product, giá, khách hàng và địa chỉ trên order là snapshot lịch sử |

---

## 7. Lifecycle trạng thái

### 7.1 Order

| Trạng thái hiện tại | Sự kiện | Trạng thái mới | Ảnh hưởng inventory |
| --- | --- | --- | --- |
| Chưa tồn tại | Tạo order hợp lệ | `PENDING_PAYMENT` | Reserved tăng |
| `PENDING_PAYMENT` | Payment success | `PAID` | Không đổi |
| `PENDING_PAYMENT` | Payment failed/expired | `PAYMENT_FAILED` | Reserved giảm |

`CANCELLED` tồn tại trong physical schema để dự phòng, nhưng cancellation chưa
có user story và không thuộc acceptance baseline của SRS này. Delivery lifecycle
được quản lý riêng, vì vậy `PAID` không đồng nghĩa với đã giao hàng.

### 7.2 Payment

| Trạng thái hiện tại | Kết quả mô phỏng | Trạng thái mới | Trạng thái Order |
| --- | --- | --- | --- |
| Chưa tồn tại | Bắt đầu attempt | `PENDING` | `PENDING_PAYMENT` |
| `PENDING` | Thành công | `SUCCESS` | `PAID` |
| `PENDING` | Bị từ chối | `FAILED` | `PAYMENT_FAILED` |
| `PENDING` | Hết hạn | `EXPIRED` | `PAYMENT_FAILED` |

`SUCCESS`, `FAILED` và `EXPIRED` là trạng thái kết thúc của một attempt. Physical
schema hỗ trợ nhiều Payment record cho một order, nhưng retry sau khi order đã
`PAYMENT_FAILED` chưa có user story và không thuộc acceptance baseline.

### 7.3 Delivery

| Trạng thái hiện tại | Điều kiện | Trạng thái mới |
| --- | --- | --- |
| `NONE` | Order đã `PAID` và bắt đầu chuẩn bị | `PREPARING` |
| `PREPARING` | Bàn giao cho shipper mô phỏng | `SHIPPED` |
| `SHIPPED` | Khách nhận hàng | `DELIVERED` |

Không có tích hợp shipping provider. Current status nằm trên Order; lịch sử nằm
trong DeliveryStatusHistory, không có Delivery aggregate riêng trong MVP.

### 7.4 Return request

| Trạng thái hiện tại | Quyết định | Trạng thái mới | Ảnh hưởng inventory |
| --- | --- | --- | --- |
| Chưa tồn tại | Tạo return hợp lệ | `REQUESTED` | Không đổi |
| `REQUESTED` | Từ chối | `REJECTED` | Không đổi |
| `REQUESTED` | Duyệt sau kiểm tra | `APPROVED` | Chưa đổi |
| `APPROVED` | `restockable = true`, nhập kho | `RESTOCKED` | On-hand tăng |
| `APPROVED` | `restockable = false` | Kết thúc tại `APPROVED` | Không đổi |

### 7.5 Inventory movement

| Movement type | On-hand | Reserved | Nguồn |
| --- | ---: | ---: | --- |
| `ORDER_RESERVED` | 0 | `+quantity` | Order |
| `PAYMENT_FAILED_RELEASE` | 0 | `-quantity` | Order/Payment |
| `STOCK_RECEIVED` | `+quantity` | 0 | Receiving |
| `MANUAL_ADJUSTMENT` | `+/-quantity` | 0 | Adjustment |
| `RETURN_RESTOCK` | `+quantity` | 0 | Return |
| `DELIVERY_COMPLETED` | `-quantity` | `-quantity` | Order/Delivery |

Với mọi type trừ `MANUAL_ADJUSTMENT`, `quantity` là độ lớn dương và type quyết
định chiều tác động. Với manual adjustment, quantity có dấu để thể hiện tăng
hoặc giảm. Quy ước này loại bỏ cách hiểu khác nhau giữa service và audit log.

---

## 8. Mô hình dữ liệu nghiệp vụ

### 8.1 Entity và field chính

| Entity | Field chính | Ghi chú |
| --- | --- | --- |
| Product | `id`, `name`, `description`, `price`, `active`, `low_stock_threshold`, timestamps | Giá hiện tại; order giữ snapshot riêng |
| Customer | `id`, `name`, `phone`, `email`, `created_at` | Optional trong MVP; hỗ trợ guest order |
| Order | `id`, `customer_id`, customer/address snapshot, `status`, `delivery_status`, `payment_method`, `total_amount`, timestamps | Aggregate trung tâm |
| OrderItem | `id`, `order_id`, `product_id`, `product_name`, `unit_price`, `quantity` | Product name và price là snapshot |
| Payment | `id`, `order_id`, `method`, `status`, `amount`, `paid_at`, `failed_reason`, `created_at` | Một order có thể có nhiều attempt |
| InventoryItem | `id`, `product_id`, `on_hand_stock`, `reserved_stock`, `updated_at` | Một product có tối đa một inventory record |
| StockMovement | `id`, `product_id`, `type`, `quantity`, reference, note, actor, `created_at` | Append-only audit record |
| ReceivingRecord | `id`, `product_id`, `quantity`, `supplier_name`, note, actor, `created_at` | Một lần nhập hàng |
| ReturnRequest | `id`, `order_id`, `status`, `reason`, `restockable`, timestamps | Header của yêu cầu return |
| ReturnRequestItem | `id`, `return_request_id`, `order_item_id`, `quantity` | Cho phép partial return |
| DeliveryStatusHistory | `id`, `order_id`, `from_status`, `to_status`, actor, time, note | Append-only delivery audit |

`CustomerSnapshot` và `ShippingAddress` là value object logic được lưu trực tiếp
trên Order trong physical schema. `available_stock` và `stockStatus` là dữ liệu
tính toán, không phải source-of-truth columns.

### 8.2 Quan hệ

```text
Product  1 -> 0..1 InventoryItem
Product  1 -> 0..n OrderItem
Product  1 -> 0..n StockMovement
Product  1 -> 0..n ReceivingRecord
Customer 1 -> 0..n Order (liên kết optional từ Order)
Order    1 -> 1..n OrderItem
Order    1 -> 0..n Payment
Order    1 -> 0..n DeliveryStatusHistory
Order    1 -> 0..n ReturnRequest
ReturnRequest 1 -> 1..n ReturnRequestItem
OrderItem     1 -> 0..n ReturnRequestItem
```

### 8.3 Ràng buộc dữ liệu

- Tiền dùng decimal chính xác; physical schema dùng `NUMERIC(12,2)`.
- MVP dùng một currency duy nhất cho Product, Order và Payment; không hỗ trợ đổi
  currency. Mã currency chưa được xác định trong Jira và chưa được lưu trong
  schema, nên phải được stakeholder xác nhận trước khi chốt cách hiển thị tiền.
- Datetime dùng timezone; physical schema dùng `TIMESTAMPTZ`.
- Identifier vật lý theo schema hiện tại là `BIGSERIAL`. Mô tả UUID trong
  SF-12 là đề xuất cũ và không phải baseline triển khai hiện tại.
- Product đã được tham chiếu bởi order hoặc movement không được hard-delete.
- Việc xóa dữ liệu giao dịch không phải use case của MVP; audit record phải được
  giữ nguyên.
- Order và OrderItem phải được tạo cùng transaction với reservation và movement.
- Thay đổi stock phải cập nhật InventoryItem và StockMovement cùng transaction.

Chi tiết cột, index, CHECK và foreign key nằm trong
[`database-schema.md`](./database-schema.md).

---

## 9. Giao diện ngoài hệ thống

### 9.1 Giao diện người dùng

- Customer: catalog, product detail, checkout/order form và payment simulation.
- Warehouse: inventory list, adjustment, receiving, delivery update và return.
- Shop owner: theo dõi order/inventory và low-stock indicator.
- Mọi form BẮT BUỘC hiển thị lỗi validation đủ để người dùng sửa input.
- Product out-of-stock và low-stock BẮT BUỘC có dấu hiệu trực quan rõ ràng.

### 9.2 API

- Backend cung cấp REST API và OpenAPI document tại `/v3/api-docs`.
- Swagger UI phục vụ phát triển tại `/swagger-ui.html`.
- Product Catalog dùng contract đã định nghĩa trong tài liệu API liên quan.
- Endpoint chi tiết cho order, payment, inventory, delivery, receiving và return
  được version hóa trong OpenAPI khi từng flow được triển khai; SRS quy định
  hành vi, không tự đặt URL chưa có contract.

### 9.3 Database

- PostgreSQL là database cho dev/staging.
- H2 in-memory được dùng cho automated test và phải chạy cùng Flyway migration.
- Flyway là nguồn quản lý thay đổi schema; ứng dụng không tự tạo schema ở runtime.

### 9.4 Dịch vụ bên ngoài

MVP không phụ thuộc payment gateway, shipping provider, notification provider
hoặc supplier system. Mọi kết quả payment/delivery đều được nhập hoặc mô phỏng
trong ShopFlow.

---

## 10. Yêu cầu phi chức năng

### 10.1 Tính nhất quán và toàn vẹn

- **NFR-01:** Các flow tạo order/reserve, payment failure/release,
  delivery/completion, receiving, adjustment và return/restock BẮT BUỘC dùng
  transaction để trạng thái nghiệp vụ và inventory không cập nhật một phần.
- **NFR-02:** Constraint database BẮT BUỘC bảo vệ các invariant có thể biểu diễn
  trực tiếp, gồm quantity hợp lệ và `reserved_stock <= on_hand_stock`.
- **NFR-03:** Validation transition BẮT BUỘC thực hiện tại application layer;
  CHECK constraint chỉ giới hạn tập giá trị, không thay thế state machine.

### 10.2 Auditability

- **NFR-04:** StockMovement và DeliveryStatusHistory là append-only ở cấp nghiệp
  vụ.
- **NFR-05:** Audit record BẮT BUỘC có thời điểm, type/status và reference tới
  nguồn khi có thể; thao tác người dùng nên ghi actor.

### 10.3 Bảo mật và dữ liệu cá nhân

- **NFR-06:** Credential database KHÔNG được hardcode trong source; staging lấy
  từ biến môi trường.
- **NFR-07:** Thông tin tên, email, phone và địa chỉ chỉ được dùng cho flow order
  và KHÔNG được ghi toàn bộ vào application log thông thường.
- **NFR-08:** MVP chưa tuyên bố có authentication/authorization production; môi
  trường public KHÔNG được dựa vào role placeholder như một kiểm soát bảo mật.

### 10.4 Khả năng bảo trì và kiểm thử

- **NFR-09:** Backend dùng Java 21, Spring Boot 4, Maven, Flyway và OpenAPI theo
  repository hiện tại.
- **NFR-10:** Frontend dùng Vue 3, Vite và TypeScript strict theo repository hiện
  tại.
- **NFR-11:** CI BẮT BUỘC chạy backend format/build/test và frontend lint/build
  trước khi merge.
- **NFR-12:** Mỗi flow chức năng BẮT BUỘC có automated test hoặc checklist QA
  bao phủ happy path và validation path đã nêu trong Jira.

### 10.5 Hiệu năng và khả dụng

MVP chưa có SLA, throughput hoặc latency target được stakeholder phê duyệt.
Không được tuyên bố đạt mục tiêu performance production nếu chưa có requirement
và phép đo riêng.

---

## 11. Điều kiện nghiệm thu tổng hợp

| Mã | Scenario | Kết quả mong đợi |
| --- | --- | --- |
| AC-01 | Mở catalog khi có product active/inactive | Chỉ product active xuất hiện; stock status đúng |
| AC-02 | Tạo order với tất cả item đủ stock | Order `PENDING_PAYMENT`; snapshot, total và reservation đúng |
| AC-03 | Một item trong order thiếu stock | Từ chối toàn bộ; không có order hoặc stock change một phần |
| AC-04 | Payment mô phỏng thành công | Payment `SUCCESS`, Order `PAID`, reserved giữ nguyên |
| AC-05 | Payment thất bại/hết hạn | Order `PAYMENT_FAILED`, reservation được release và có movement |
| AC-06 | Delivery đi đúng tuần tự | Status và history được cập nhật; delivered giảm on-hand/reserved |
| AC-07 | Delivery bỏ bước hoặc order chưa paid | Từ chối, không thay đổi status/history/stock |
| AC-08 | Manual adjustment hợp lệ | On-hand/available đúng và có audit note/movement |
| AC-09 | Receiving quantity dương | On-hand tăng, receiving record và movement được lưu |
| AC-10 | Receiving quantity không dương | Từ chối và inventory không đổi |
| AC-11 | Return hợp lệ, restockable | Return `RESTOCKED`, on-hand tăng đúng và có movement |
| AC-12 | Return vượt quantity đã mua hoặc sai order item | Từ chối và inventory không đổi |
| AC-13 | Return rejected/non-restockable | Trạng thái đúng và inventory không đổi |
| AC-14 | Available stock đi qua low-stock threshold | Indicator xuất hiện hoặc được gỡ đúng |

---

## 12. Ma trận truy vết Jira

| Jira | Requirement | Nội dung |
| --- | --- | --- |
| SF-1 | Toàn bộ SRS | Epic Online Shop Sales and Inventory MVP |
| SF-2, SF-36, SF-37, SF-38, SF-39 | FR-01 | Product Catalog |
| SF-3, SF-11, SF-12, SF-13, SF-35, SF-43 | FR-02, BR-01 đến BR-04, BR-15 | Create Customer Order |
| SF-4, SF-40, SF-41, SF-42 | FR-03, BR-05 | Payment Simulation |
| SF-5 | FR-04, BR-06, BR-07 | Delivery Status |
| SF-6, SF-14, SF-15, SF-16 | FR-05, BR-01, BR-02, BR-12, BR-13 | Inventory Management |
| SF-7 | FR-06, BR-11, BR-13 | Supplier Receiving |
| SF-8, SF-17, SF-18, SF-19 | FR-07, BR-08 đến BR-10, BR-13 | Customer Return |
| SF-9 | FR-08, BR-14 | Low-stock Alert |
| SF-20, SF-21, SF-22, SF-23, SF-24, SF-25, SF-26, SF-27, SF-28 | NFR-06, NFR-09 đến NFR-11 | Monorepo, backend/frontend foundation và CI |

Các tham chiếu FR-10, FR-11 và FR-12 trong database documentation không có story
tương ứng trong bản Jira XML dùng để lập SRS. Dashboard, inventory movement
history độc lập và product management vì vậy chưa được đưa thành acceptance
baseline; schema chỉ giữ dữ liệu hỗ trợ mở rộng sau này.

---

## 13. Giả định và giới hạn đã biết

- Jira export được tạo ngày 21/06/2026 và có thể thay đổi sau thời điểm lập SRS.
- Race condition khi nhiều order đồng thời tranh cùng stock là known limitation
  của MVP; transaction vẫn bắt buộc để tránh update một phần trong một request.
- Customer profile là optional; guest order dùng snapshot trên Order.
- Không có Delivery entity riêng; current status nằm trên Order và history nằm
  trong DeliveryStatusHistory.
- `CARD`/`COD` tồn tại trong schema, nhưng chỉ payment result simulation trước
  delivery là luồng bắt buộc. COD cần requirement riêng trước khi triển khai.
- `CANCELLED` tồn tại trong schema nhưng cancellation flow chưa thuộc backlog
  nghiệm thu hiện tại.
- Return không bao gồm refund.
- Low-stock alert là indicator trong hệ thống, không phải outbound notification.
- MVP là single-currency, nhưng currency code còn cần stakeholder xác nhận.

---

## 14. Quyết định hòa giải nguồn

| Chủ đề | Khác biệt nguồn | Baseline của SRS |
| --- | --- | --- |
| Vị trí SRS | Database doc cũ trỏ `../SRS.md` | `docs/SRS.md` |
| Sprint | Jira còn nhãn/lịch Sprint 1 cũ và issue carry-over | Lịch trong README hiện tại |
| Backend | Jira bootstrap ghi Spring Boot 3.3+ | Spring Boot 4 theo repository hiện tại |
| Order ID | SF-12 đề xuất UUID | `BIGSERIAL` theo Flyway/database schema hiện tại |
| Payment | Schema cho phép `CARD`, `COD`; Jira chỉ định nghĩa simulation result | Simulation success/failure/expiry là baseline; COD deferred |
| Delivery | SF-5 gọi delivery status, schema lưu trên Order và history | Không tạo Delivery aggregate riêng trong MVP |
| StockMovement quantity | Database doc cho phép cách hiểu dấu chưa thống nhất | Positive magnitude theo type; manual adjustment dùng signed quantity |
| Return không restock | Schema không có status `CLOSED` | `APPROVED` + `restockable=false` là terminal trong MVP |

Khi Jira, SRS và implementation tiếp tục thay đổi, yêu cầu nghiệp vụ phải được
cập nhật tại SRS trước hoặc cùng Pull Request với code/schema liên quan.

---

## 15. Lịch sử phiên bản

| Phiên bản | Ngày | Thay đổi |
| --- | --- | --- |
| 1.0 | 21/06/2026 | Baseline SRS từ Epic SF-1, Jira backlog, README và database schema |
