# ShopFlow Software Requirements Specification

**Phiên bản:** 1.3

**Ngày cập nhật:** 17/07/2026

**Trạng thái:** Baseline yêu cầu MVP

**Phạm vi:** Epic [SF-1](https://tuanwork.atlassian.net/browse/SF-1)

**Tài liệu liên quan:**

- [../README.md](../README.md) - roadmap, công nghệ, môi trường local và CI.
- [../CONTRIBUTING.md](../CONTRIBUTING.md) - quy ước Git, Jira và Pull Request.
- [database-schema.md](./database-schema.md) - thiết kế dữ liệu vật lý.
- [database-schema.sql](./database-schema.sql) - DDL PostgreSQL tham chiếu.
- [api-product-catalog-spec.md](./api-product-catalog-spec.md) - API contract của Product Catalog.
- [api-customer-order-spec.md](./api-customer-order-spec.md) - API contract tạo Customer Order.

---

## 1. Mục đích tài liệu

Tài liệu này định nghĩa yêu cầu phần mềm cho ShopFlow, một hệ thống MVP mô phỏng
quy trình bán hàng và quản lý tồn kho của shop online quy mô nhỏ. SRS là baseline
chung để Business Analyst, backend, frontend và QA thống nhất phạm vi, hành vi
nghiệp vụ, ràng buộc chất lượng và điều kiện nghiệm thu.

SRS mô tả hệ thống phải làm gì và các ràng buộc cần đạt. Roadmap, sprint, lựa
chọn công nghệ, CI, Git/Jira workflow và thiết kế dữ liệu vật lý nằm trong các
tài liệu liên quan, không phải requirement của sản phẩm.

### 1.1 Đối tượng đọc

- Chủ shop và người duyệt nghiệp vụ.
- Business Analyst và Product Owner.
- Backend, frontend và DevOps.
- QA và người thực hiện demo/nghiệm thu.

### 1.2 Quy ước yêu cầu

- BẮT BUỘC chỉ hành vi hoặc ràng buộc cần có để nghiệm thu MVP.
- KHÔNG chỉ hành vi bị cấm hoặc nằm ngoài phạm vi.
- Mỗi yêu cầu chức năng có mã FR-xx, mỗi quy tắc nghiệp vụ có mã BR-xx và mỗi
  yêu cầu chất lượng có mã NFR-xx.
- Mỗi yêu cầu dữ liệu có mã DR-xx và mỗi yêu cầu giao diện có mã IR-xx.
- Mỗi điều kiện nghiệm thu có mã AC-xx.
- Tên trạng thái nghiệp vụ dùng UPPER_SNAKE_CASE; tên hiển thị có thể được dịch
  trên giao diện.

---

## 2. Tổng quan sản phẩm

### 2.1 Mục tiêu

ShopFlow cho phép khách hàng xem sản phẩm và tạo đơn; mô phỏng kết quả thanh
toán; cho phép chủ shop và nhân viên kho theo dõi giao hàng, quản lý tồn kho,
nhập hàng, xử lý hàng hoàn và nhận biết sản phẩm sắp hết hàng.

Giá trị chính của MVP là kiểm soát chính xác on-hand, reserved và available
stock, qua đó giảm nguy cơ nhận đơn vượt quá lượng hàng có thể bán.

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
- Authentication và authorization production.
- Loyalty, promotion, cart phức tạp, thuế và báo cáo doanh thu nâng cao.
- Điều phối tồn kho phân tán, queue hoặc mục tiêu hiệu năng được phê duyệt cho
  tải tranh chấp cao. Dù vậy, các request tạo order đồng thời vẫn phải bảo toàn
  invariant tồn kho theo NFR-03.
- Email, SMS hoặc push notification cho low-stock alert.
- Multi-currency.

---

## 3. Stakeholder và actor

| Actor         | Mục tiêu                      | Khả năng nghiệp vụ trong MVP                                                 |
| ------------- | ----------------------------- | ---------------------------------------------------------------------------- |
| Khách hàng    | Chọn sản phẩm và đặt hàng     | Xem catalog/chi tiết, tạo order, thực hiện payment mô phỏng                  |
| Nhân viên kho | Duy trì tồn kho và xử lý hàng | Xem/điều chỉnh stock, nhập hàng, cập nhật delivery, xử lý return             |
| Chủ shop      | Theo dõi hoạt động bán hàng   | Theo dõi order/inventory, cập nhật delivery, xử lý return và low-stock alert |
| Hệ thống      | Bảo đảm tính nhất quán        | Tính stock, validate transition, lưu snapshot và audit movement              |

Các actor xác định quyền nghiệp vụ của MVP, không tuyên bố có cơ chế
authentication hoặc authorization production.

---

## 4. Thuật ngữ nghiệp vụ

| Thuật ngữ       | Định nghĩa                                                                                       |
| --------------- | ------------------------------------------------------------------------------------------------ |
| Product         | Sản phẩm shop đang hoặc đã từng kinh doanh                                                       |
| Customer        | Khách hàng đặt mua sản phẩm; có thể là profile lưu trong hệ thống hoặc guest snapshot trên order |
| Order           | Giao dịch đặt hàng gồm thông tin khách, địa chỉ giao và một hoặc nhiều item                      |
| OrderItem       | Snapshot sản phẩm, đơn giá và số lượng tại thời điểm tạo order                                   |
| Payment         | Một lần thử thanh toán mô phỏng cho order                                                        |
| Delivery status | Trạng thái xử lý giao hàng, độc lập với trạng thái payment/order                                 |
| On-hand stock   | Số lượng vật lý đang được ghi nhận trong kho                                                     |
| Reserved stock  | Phần on-hand đã giữ cho order chưa hoàn tất giao hàng                                            |
| Available stock | Số lượng có thể nhận order mới: on_hand_stock - reserved_stock                                   |
| Stock movement  | Bản ghi audit giải thích một thay đổi tồn kho                                                    |
| Return request  | Yêu cầu trả một phần hoặc toàn bộ item của order đã giao                                         |
| Snapshot        | Bản sao dữ liệu tại thời điểm giao dịch, không đổi theo master data về sau                       |
| Low stock       | Trạng thái khi available stock nhỏ hơn hoặc bằng ngưỡng của product                              |

---

## 5. Yêu cầu chức năng

### 5.1 FR-01 - Product Catalog

Nguồn: [SF-2](https://tuanwork.atlassian.net/browse/SF-2),
[SF-36](https://tuanwork.atlassian.net/browse/SF-36),
[SF-47](https://tuanwork.atlassian.net/browse/SF-47).

- **FR-01.1:** Hệ thống BẮT BUỘC chỉ cung cấp product đang active trên catalog
  công khai.
- **FR-01.2:** Danh sách product BẮT BUỘC cung cấp name, price và stockStatus.
- **FR-01.3:** Chi tiết product BẮT BUỘC cung cấp name, description, price và
  stockStatus.
- **FR-01.4:** stockStatus BẮT BUỘC là IN_STOCK khi available stock lớn hơn 0;
  các trường hợp còn lại là OUT_OF_STOCK.
- **FR-01.5:** Product không có dữ liệu tồn kho BẮT BUỘC được xem là
  OUT_OF_STOCK.
- **FR-01.6:** Product không tồn tại hoặc inactive KHÔNG được cung cấp qua
  catalog công khai.
- **FR-01.7:** Hệ thống BẮT BUỘC cho phép customer lọc catalog đã tải theo tên
  product. Thao tác lọc KHÔNG được làm thay đổi product hoặc các item đã chọn.

API contract chuẩn cho yêu cầu này nằm trong
[api-product-catalog-spec.md](./api-product-catalog-spec.md).

### 5.2 FR-02 - Tạo order

Nguồn: [SF-3](https://tuanwork.atlassian.net/browse/SF-3),
[SF-11](https://tuanwork.atlassian.net/browse/SF-11),
[SF-12](https://tuanwork.atlassian.net/browse/SF-12),
[SF-35](https://tuanwork.atlassian.net/browse/SF-35),
[SF-43](https://tuanwork.atlassian.net/browse/SF-43),
[SF-44](https://tuanwork.atlassian.net/browse/SF-44).

- **FR-02.1:** Khách hàng BẮT BUỘC cung cấp tên khách hàng, tên và số điện thoại
  người nhận, địa chỉ, thành phố và danh sách item không rỗng. Mỗi item BẮT
  BUỘC có product identifier và quantity là số nguyên dương. Email, số điện
  thoại khách hàng và district là optional.
- **FR-02.2:** Một request chỉ được có tối đa một item cho mỗi product. Product
  xuất hiện lặp lại BẮT BUỘC bị từ chối, không được tự gộp quantity.
- **FR-02.3:** Trước khi tạo order, hệ thống BẮT BUỘC xác minh từng product tồn
  tại, đang active và có available stock theo dữ liệu hiện tại.
- **FR-02.4:** Nếu bất kỳ product nào không tồn tại, inactive hoặc thiếu stock,
  hệ thống KHÔNG được tạo order, order item, reservation hoặc stock movement cho
  request đó.
- **FR-02.5:** Khi tất cả item hợp lệ, hệ thống BẮT BUỘC atomically tạo order ở
  PENDING_PAYMENT và reserve đúng quantity cho từng product.
- **FR-02.6:** Hệ thống BẮT BUỘC snapshot tên product, đơn giá, thông tin khách
  hàng và địa chỉ giao tại thời điểm tạo order.
- **FR-02.7:** totalAmount BẮT BUỘC bằng tổng unitPrice × quantity của các order
  item.
- **FR-02.8:** Kết quả tạo thành công BẮT BUỘC trả tối thiểu order identifier và
  status để tiếp tục payment.
- **FR-02.9:** Thông báo từ chối BẮT BUỘC cho biết product nào không tồn tại,
  inactive hoặc không đáp ứng quantity yêu cầu.
- **FR-02.10:** MVP chỉ chấp nhận `paymentMethod = CARD`. Request dùng `COD`
  BẮT BUỘC bị từ chối vì COD workflow chưa thuộc baseline.

API contract chuẩn cho yêu cầu này nằm trong
[api-customer-order-spec.md](./api-customer-order-spec.md).

### 5.3 FR-03 - Payment mô phỏng

Nguồn: [SF-4](https://tuanwork.atlassian.net/browse/SF-4),
[SF-40](https://tuanwork.atlassian.net/browse/SF-40),
[SF-41](https://tuanwork.atlassian.net/browse/SF-41),
[SF-42](https://tuanwork.atlassian.net/browse/SF-42).

- **FR-03.1:** Hệ thống BẮT BUỘC chỉ nhận kết quả payment cho order đang ở
  PENDING_PAYMENT.
- **FR-03.2:** MVP chỉ cho phép tối đa một payment attempt mô phỏng cho mỗi
  order. Khi bắt đầu attempt, hệ thống BẮT BUỘC lưu attempt với amount bằng
  totalAmount của order.
- **FR-03.3:** Kết quả mô phỏng thành công BẮT BUỘC chuyển Payment thành
  SUCCESS, Order thành PAID và giữ nguyên reserved stock.
- **FR-03.4:** Kết quả thất bại hoặc hết hạn BẮT BUỘC chuyển Payment thành FAILED
  hoặc EXPIRED, Order thành PAYMENT_FAILED và release toàn bộ reservation của
  order.
- **FR-03.5:** Hệ thống BẮT BUỘC từ chối payment lặp lại cho order không còn ở
  PENDING_PAYMENT.
- **FR-03.6:** Kết quả payment hoàn toàn do hệ thống/demo input mô phỏng; hệ
  thống KHÔNG gọi payment gateway thật.

Luồng nghiệm thu bắt buộc là payment mô phỏng bằng CARD hoàn tất trước delivery.
COD chỉ còn là giá trị được schema vật lý dự phòng; API và acceptance baseline của
MVP không nhận COD.

### 5.4 FR-04 - Cập nhật delivery status

Nguồn: [SF-5](https://tuanwork.atlassian.net/browse/SF-5),
[SF-49](https://tuanwork.atlassian.net/browse/SF-49).

- **FR-04.1:** Chỉ order PAID mới được chuyển delivery status từ NONE sang
  PREPARING.
- **FR-04.2:** Transition hợp lệ BẮT BUỘC theo thứ tự NONE -> PREPARING ->
  SHIPPED -> DELIVERED; hệ thống KHÔNG cho phép bỏ qua bước hoặc đi lùi trong
  MVP.
- **FR-04.3:** Mỗi transition BẮT BUỘC cập nhật status hiện tại và thêm audit
  history gồm trạng thái trước, trạng thái sau, thời điểm và người thực hiện khi
  có.
- **FR-04.4:** Khi chuyển sang DELIVERED, hệ thống BẮT BUỘC giảm cả on-hand và
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
  thành giá trị độc lập.
- **FR-05.3:** Nhân viên kho được phép điều chỉnh on-hand stock thủ công bằng
  delta là số nguyên khác 0, kèm một lý do không rỗng.
- **FR-05.4:** Điều chỉnh KHÔNG được làm on-hand âm hoặc làm reserved lớn hơn
  on-hand.
- **FR-05.5:** Mọi điều chỉnh thành công BẮT BUỘC atomically cập nhật inventory
  theo delta, ghi manual-adjustment stock movement với cùng delta và lưu lý do
  vào note của movement.
- **FR-05.6:** Nếu product chưa có dữ liệu tồn kho, manual adjustment có delta
  dương BẮT BUỘC tạo dữ liệu tồn kho với on-hand và reserved bằng 0 trước khi
  áp dụng delta trong cùng thao tác. Delta âm bị từ chối theo FR-05.4.

### 5.6 FR-06 - Nhập hàng từ supplier

Nguồn: [SF-7](https://tuanwork.atlassian.net/browse/SF-7).

- **FR-06.1:** Nhân viên kho được phép tạo receiving record cho product đã tồn
  tại.
- **FR-06.2:** Quantity nhập BẮT BUỘC là số nguyên dương; giá trị không hợp lệ
  phải bị từ chối mà không thay đổi stock.
- **FR-06.3:** Nhập hàng thành công BẮT BUỘC atomically tăng on-hand stock, lưu
  receiving record và ghi stock movement. Nếu product chưa có dữ liệu tồn kho,
  thao tác này BẮT BUỘC tạo dữ liệu tồn kho với on-hand và reserved bằng 0 trước
  khi tăng on-hand.
- **FR-06.4:** Supplier chỉ là tên tự do trong MVP; hệ thống không quản lý
  supplier master data.

### 5.7 FR-07 - Xử lý return

Nguồn: [SF-8](https://tuanwork.atlassian.net/browse/SF-8),
[SF-17](https://tuanwork.atlassian.net/browse/SF-17),
[SF-18](https://tuanwork.atlassian.net/browse/SF-18).

- **FR-07.1:** Return chỉ được tạo cho order có delivery status DELIVERED.
- **FR-07.2:** Return BẮT BUỘC chỉ tham chiếu order item thuộc chính order đó và
  có quantity là số nguyên dương.
- **FR-07.3:** Tổng quantity của return item thuộc return request có status
  REQUESTED, APPROVED hoặc RESTOCKED của một order item KHÔNG được vượt quantity
  đã mua; REJECTED không tính vào tổng này. Các request return đồng thời BẮT
  BUỘC cũng bảo toàn quy tắc này. Request làm vượt tổng BẮT BUỘC bị từ chối toàn
  bộ mà không thay đổi dữ liệu return.
- **FR-07.4:** Return request mới BẮT BUỘC có status REQUESTED.
- **FR-07.5:** Chủ shop hoặc nhân viên kho được phép chuyển REQUESTED sang
  APPROVED hoặc REJECTED.
- **FR-07.6:** Khi duyệt, người xử lý BẮT BUỘC xác định hàng có thể nhập lại kho
  hay không bằng restockable; return giữ status APPROVED sau quyết định này.
- **FR-07.7:** Chỉ nhân viên kho được xác nhận nhập lại kho cho return APPROVED
  và restockable. Khi xác nhận, hệ thống BẮT BUỘC atomically tăng on-hand stock
  của product tương ứng theo quantity của từng return item, ghi một stock
  movement cho từng item và chuyển return request sang RESTOCKED.
- **FR-07.8:** Return bị từ chối hoặc APPROVED nhưng không restockable KHÔNG được
  thay đổi inventory.
- **FR-07.9:** MVP KHÔNG thực hiện refund hoặc gọi payment gateway.

APPROVED với restockable = false là trạng thái kết thúc nghiệp vụ mà không nhập
kho. APPROVED với restockable = true chỉ chuyển sang RESTOCKED khi nhân viên kho
xác nhận đã nhập lại kho.

### 5.8 FR-08 - Low-stock alert

Nguồn: [SF-9](https://tuanwork.atlassian.net/browse/SF-9).

- **FR-08.1:** Mỗi product có thể cấu hình lowStockThreshold là số nguyên không
  âm; giá trị null nghĩa là không theo dõi cảnh báo.
- **FR-08.2:** Product có lowStockThreshold khác null BẮT BUỘC được đánh dấu
  LOW_STOCK khi available stock nhỏ hơn hoặc bằng threshold.
- **FR-08.3:** Với product có lowStockThreshold khác null, dấu hiệu LOW_STOCK
  BẮT BUỘC được gỡ khi available stock tăng vượt threshold.
- **FR-08.4:** Trạng thái low stock được suy ra từ dữ liệu hiện tại; MVP không
  yêu cầu lưu một cờ độc lập hoặc gửi notification ra ngoài hệ thống.

---

## 6. Quy tắc nghiệp vụ

| Mã    | Quy tắc                                                                                                      |
| ----- | ------------------------------------------------------------------------------------------------------------ |
| BR-01 | available_stock = on_hand_stock - reserved_stock                                                             |
| BR-02 | On-hand và reserved là số nguyên không âm; reserved không lớn hơn on-hand                                    |
| BR-03 | Order nhiều item được xử lý atomically; thiếu stock ở một item thì từ chối toàn bộ order                     |
| BR-04 | Tạo order thành công làm tăng reserved, không làm giảm on-hand                                               |
| BR-05 | Payment thất bại/hết hạn làm giảm reserved đúng quantity đã giữ                                              |
| BR-06 | Delivery chỉ bắt đầu sau payment mô phỏng thành công và phải đi đúng thứ tự                                  |
| BR-07 | Khi delivered, on-hand và reserved cùng giảm theo quantity đã giao                                           |
| BR-08 | Return chỉ hợp lệ sau khi delivery đã DELIVERED                                                              |
| BR-09 | Chỉ return đã duyệt và restockable mới làm tăng on-hand                                                      |
| BR-10 | Tổng return REQUESTED, APPROVED hoặc RESTOCKED của một order item không vượt lượng đã mua                    |
| BR-11 | Receiving quantity phải là số nguyên dương                                                                   |
| BR-12 | Manual adjustment dùng delta nguyên khác 0, có lý do lưu vào movement note và không phá vỡ invariant tồn kho |
| BR-13 | Mọi nghiệp vụ thay đổi stock phải có stock movement tương ứng                                                |
| BR-14 | Low stock được xác định từ available stock và threshold của product                                          |
| BR-15 | Thông tin product, giá, khách hàng và địa chỉ trên order là snapshot lịch sử                                 |

---

## 7. Lifecycle trạng thái

### 7.1 Order

| Trạng thái hiện tại | Sự kiện                | Trạng thái mới  | Ảnh hưởng inventory |
| ------------------- | ---------------------- | --------------- | ------------------- |
| Chưa tồn tại        | Tạo order hợp lệ       | PENDING_PAYMENT | Reserved tăng       |
| PENDING_PAYMENT     | Payment success        | PAID            | Không đổi           |
| PENDING_PAYMENT     | Payment failed/expired | PAYMENT_FAILED  | Reserved giảm       |

Cancellation chưa có user story và không thuộc acceptance baseline của SRS này.
Delivery lifecycle được quản lý riêng, vì vậy PAID không đồng nghĩa với đã giao
hàng.

### 7.2 Payment

| Trạng thái hiện tại | Kết quả mô phỏng | Trạng thái mới | Trạng thái Order |
| ------------------- | ---------------- | -------------- | ---------------- |
| Chưa tồn tại        | Bắt đầu attempt  | PENDING        | PENDING_PAYMENT  |
| PENDING             | Thành công       | SUCCESS        | PAID             |
| PENDING             | Bị từ chối       | FAILED         | PAYMENT_FAILED   |
| PENDING             | Hết hạn          | EXPIRED        | PAYMENT_FAILED   |

SUCCESS, FAILED và EXPIRED là trạng thái kết thúc của payment attempt. Retry sau
khi order đã PAYMENT_FAILED chưa có user story và không thuộc acceptance
baseline.

### 7.3 Delivery

| Trạng thái hiện tại | Điều kiện                         | Trạng thái mới |
| ------------------- | --------------------------------- | -------------- |
| NONE                | Order đã PAID và bắt đầu chuẩn bị | PREPARING      |
| PREPARING           | Bàn giao cho shipper mô phỏng     | SHIPPED        |
| SHIPPED             | Khách nhận hàng                   | DELIVERED      |

Không có tích hợp shipping provider. Hệ thống lưu lịch sử thay đổi delivery
status để kiểm tra lại.

### 7.4 Return request

| Trạng thái hiện tại | Quyết định                                              | Trạng thái mới        | Ảnh hưởng inventory |
| ------------------- | ------------------------------------------------------- | --------------------- | ------------------- |
| Chưa tồn tại        | Tạo return hợp lệ                                       | REQUESTED             | Không đổi           |
| REQUESTED           | Từ chối                                                 | REJECTED              | Không đổi           |
| REQUESTED           | Duyệt sau kiểm tra                                      | APPROVED              | Chưa đổi            |
| APPROVED            | Nhân viên kho xác nhận nhập lại kho, restockable = true | RESTOCKED             | On-hand tăng        |
| APPROVED            | restockable = false                                     | Kết thúc tại APPROVED | Không đổi           |

### 7.5 Ảnh hưởng inventory theo nghiệp vụ

| Sự kiện nghiệp vụ         |     On-hand |  Reserved | Audit                        |
| ------------------------- | ----------: | --------: | ---------------------------- |
| Reserve order             |           0 | +quantity | Ghi nhận giữ hàng            |
| Payment failed/expired    |           0 | -quantity | Ghi nhận giải phóng giữ hàng |
| Receive stock             |   +quantity |         0 | Ghi nhận nhập hàng           |
| Manual adjustment         | +/-quantity |         0 | Ghi lý do điều chỉnh         |
| Return restock (mỗi item) |   +quantity |         0 | Ghi nhận hàng hoàn nhập kho  |
| Complete delivery         |   -quantity | -quantity | Ghi nhận đã giao             |

---

## 8. Yêu cầu dữ liệu

### 8.1 Thông tin nghiệp vụ

| Thông tin             | Dữ liệu chính                                                                                            | Ghi chú                                     |
| --------------------- | -------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| Product               | Identifier, name, description, price, active, low-stock threshold                                        | Giá hiện tại; order giữ snapshot riêng      |
| Customer              | Identifier, name, phone, email                                                                           | Optional trong MVP; hỗ trợ guest order      |
| Order                 | Identifier, customer/address snapshot, status, delivery status, payment method, total amount, timestamps | Aggregate trung tâm                         |
| OrderItem             | Order, product, product name, unit price, quantity                                                       | Name và price là snapshot                   |
| Payment               | Order, method, status, amount, paid time, failure reason                                                 | MVP có tối đa một attempt; không retry      |
| Inventory             | Product, on-hand stock, reserved stock, updated time                                                     | Một product có tối đa một dữ liệu tồn kho   |
| StockMovement         | Product, type, quantity, reference, note, actor, time                                                    | Audit tồn kho; note bắt buộc cho adjustment |
| ReceivingRecord       | Product, quantity, supplier name, note, actor, time                                                      | Một lần nhập hàng                           |
| ReturnRequest         | Order, status, reason, restockable, timestamps                                                           | Header của yêu cầu return                   |
| ReturnRequestItem     | Return request, order item, quantity                                                                     | Cho phép partial return                     |
| DeliveryStatusHistory | Order, from status, to status, actor, time, note                                                         | Audit delivery                              |

### 8.2 Quan hệ nghiệp vụ

- Product có tối đa một dữ liệu tồn kho, nhiều order item, stock movement và
  receiving record.
- Customer có thể có nhiều order; liên kết từ Order là optional.
- Order có một hoặc nhiều order item, có tối đa một payment attempt, và có thể
  có nhiều delivery-history entry và return request.
- Schema vật lý cho phép nhiều payment row để mở rộng sau này; MVP chỉ tạo tối đa
  một row và không hỗ trợ retry sau PAYMENT_FAILED.
- Return request có một hoặc nhiều return item; return item tham chiếu order
  item.

### 8.3 Ràng buộc dữ liệu

- **DR-01:** Giá và amount BẮT BUỘC là số nguyên không âm theo đơn vị VND; request
  có phần lẻ phải bị từ chối.
- **DR-02:** On-hand stock, reserved stock và low-stock threshold BẮT BUỘC là
  số nguyên không âm. Quantity của order, receiving và return BẮT BUỘC là số
  nguyên dương; manual adjustment dùng delta nguyên khác 0. Stock movement dùng
  delta nguyên khác 0 với dấu phản ánh thay đổi inventory ở §7.5.
- **DR-03:** Timestamp nghiệp vụ BẮT BUỘC có timezone.
- **DR-04:** MVP dùng duy nhất currency `VND`; mọi price, amount và cách hiển thị
  tiền trong baseline đều được hiểu theo đồng Việt Nam.
- **DR-05:** Product đã được tham chiếu bởi order hoặc stock movement KHÔNG được
  hard-delete.
- **DR-06:** MVP không cung cấp thao tác hard-delete order, order item, payment,
  stock movement, receiving record, return hoặc delivery history; các audit record
  append-only phải được giữ nguyên.
- **DR-07:** Available stock và stockStatus BẮT BUỘC là dữ liệu tính toán, không
  phải source of truth độc lập.

Chi tiết cột, index, constraint và DDL nằm trong
[database-schema.md](./database-schema.md).

---

## 9. Giao diện ngoài hệ thống

### 9.1 Giao diện người dùng

- Customer: catalog, product detail, checkout/order form và payment simulation.
- Warehouse: inventory list, adjustment, receiving, delivery update và return.
- Shop owner: theo dõi order/inventory và low-stock indicator.
- **IR-01:** Mọi form BẮT BUỘC chỉ rõ field hoặc business error làm request bị từ
  chối và giữ lại dữ liệu hợp lệ để người dùng sửa rồi gửi lại.
- **IR-02:** Product out-of-stock và low-stock BẮT BUỘC có nhãn trạng thái bằng
  text; không chỉ dùng màu hoặc icon.
- **IR-04:** Giao diện customer KHÔNG được hiển thị điều hướng workflow của
  Warehouse hoặc Shop owner và BẮT BUỘC hiển thị số lượng item đã chọn trước
  checkout.

### 9.2 API

- **IR-03:** Hệ thống BẮT BUỘC cung cấp REST API được mô tả bằng OpenAPI.
- SRS này là nguồn chuẩn cho behavior ở cấp sản phẩm. API contract hiện có cho
  [Product Catalog](./api-product-catalog-spec.md) và
  [Customer Order](./api-customer-order-spec.md),
  [Payment Simulation](./api-payment-simulation-spec.md) và
  [Inventory Management](./api-inventory-management-spec.md) quy định request,
  response và error semantics của bốn flow đó.
- Với các flow chưa có API contract được liên kết, SRS chỉ quy định behavior và
  điều kiện nghiệm thu; URL, payload, mã lỗi và error semantics cụ thể không
  thuộc baseline của tài liệu này.

### 9.3 Dịch vụ bên ngoài

MVP không phụ thuộc payment gateway, shipping provider, notification provider
hoặc supplier system. Mọi kết quả payment/delivery đều được nhập hoặc mô phỏng
trong ShopFlow.

---

## 10. Yêu cầu chất lượng

### 10.1 Tính nhất quán và toàn vẹn

- **NFR-01:** Các flow tạo order/reserve, payment failure/release,
  delivery/completion, receiving, adjustment và return/restock BẮT BUỘC áp dụng
  toàn bộ thay đổi trạng thái nghiệp vụ và audit liên quan, hoặc không áp dụng
  thay đổi nào.
- **NFR-02:** Hệ thống BẮT BUỘC từ chối mọi thay đổi làm on-hand hoặc reserved
  âm, hoặc làm reserved lớn hơn on-hand.
- **NFR-03:** Các request tạo order đồng thời tranh cùng product KHÔNG được làm
  reserved lớn hơn on-hand. Request không còn đủ stock tại thời điểm cấp phát
  cuối cùng BẮT BUỘC bị từ chối mà không để lại order, reservation hoặc audit
  change một phần.
- **NFR-04:** Hệ thống BẮT BUỘC từ chối transition order, payment, delivery và
  return không hợp lệ mà không thay đổi dữ liệu.

### 10.2 Auditability

- **NFR-05:** Stock movement và delivery status history BẮT BUỘC append-only ở
  cấp nghiệp vụ.
- **NFR-06:** Audit record BẮT BUỘC có thời điểm, loại thay đổi và reference tới
  nguồn khi có thể; khi hành động do actor đã xác định thực hiện, record BẮT BUỘC
  lưu actor đó.

### 10.3 Bảo mật và dữ liệu cá nhân

- **NFR-07:** Thông tin tên, email, phone và địa chỉ chỉ được dùng cho flow order
  và KHÔNG được ghi toàn bộ vào application log thông thường.
- **NFR-08:** Credential vận hành KHÔNG được nhúng trong source, client bundle
  hoặc application log.

### 10.4 Hiệu năng và khả dụng

MVP chưa có SLA, throughput hoặc latency target được stakeholder phê duyệt.
Baseline này không tuyên bố đạt mục tiêu performance production.

---

## 11. Điều kiện nghiệm thu tổng hợp

| Mã    | Scenario                                                                        | Kết quả mong đợi                                                               |
| ----- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| AC-01 | Mở catalog khi có product active/inactive                                       | Chỉ product active xuất hiện; stock status đúng                                |
| AC-02 | Tạo order với tất cả item đủ stock                                              | Order PENDING_PAYMENT; snapshot, total và reservation đúng                     |
| AC-03 | Một item trong order thiếu stock                                                | Từ chối toàn bộ; không có order hoặc stock change một phần                     |
| AC-04 | Payment mô phỏng thành công                                                     | Payment SUCCESS, Order PAID, reserved giữ nguyên                               |
| AC-05 | Payment thất bại/hết hạn                                                        | Order PAYMENT_FAILED, reservation được release và có movement                  |
| AC-06 | Delivery đi đúng tuần tự                                                        | Status và history được cập nhật; delivered giảm on-hand/reserved               |
| AC-07 | Delivery bỏ bước hoặc order chưa paid                                           | Từ chối, không thay đổi status/history/stock                                   |
| AC-08 | Manual adjustment với delta nguyên khác 0                                       | On-hand/available đổi đúng delta; StockMovement.note lưu lý do                 |
| AC-09 | Receiving quantity là số nguyên dương                                           | On-hand tăng, receiving record và movement được lưu                            |
| AC-10 | Receiving quantity không phải số nguyên dương                                   | Từ chối và inventory không đổi                                                 |
| AC-11 | Nhân viên kho xác nhận nhập kho cho return nhiều product, APPROVED, restockable | Return RESTOCKED; mỗi product tăng đúng và có một movement/item                |
| AC-12 | Return vượt quantity đã mua hoặc sai order item                                 | Từ chối và inventory không đổi                                                 |
| AC-13 | Return rejected/non-restockable                                                 | Trạng thái đúng và inventory không đổi                                         |
| AC-14 | Available stock đi qua low-stock threshold                                      | Indicator xuất hiện hoặc được gỡ đúng                                          |
| AC-15 | Hai order đồng thời tranh lượng stock cuối                                      | Không over-reserve; request bị từ chối không tạo thay đổi một phần             |
| AC-16 | Order có cùng product ở nhiều item                                              | Từ chối; không gộp quantity hoặc thay đổi dữ liệu                              |
| AC-17 | Order tham chiếu product không tồn tại/inactive                                 | Từ chối; không tạo order hoặc reservation                                      |
| AC-18 | Duyệt return restockable trước khi nhập kho                                     | Return APPROVED; inventory chưa thay đổi                                       |
| AC-19 | Nhiều return, kể cả đồng thời, vượt tổng lượng đã mua                           | Từ chối toàn bộ request vượt; tổng REQUESTED/APPROVED/RESTOCKED không vượt mua |
| AC-20 | Nhập hàng cho product chưa có dữ liệu tồn kho                                   | Tạo inventory từ 0/0, tăng on-hand đúng và ghi receiving/movement              |
| AC-21 | Adjustment dương cho product chưa có dữ liệu tồn kho                            | Tạo inventory từ 0/0, áp dụng delta và lưu movement note                       |
| AC-22 | Tạo order với `paymentMethod=COD`                                               | Từ chối 400; không tạo order, reservation hoặc movement                        |
| AC-23 | Hiển thị price/amount trong catalog và order                                    | Dùng VND, hiển thị không có phần lẻ và không tự làm tròn giá trị               |
| AC-24 | Customer tìm product, chọn item rồi tiếp tục duyệt catalog                      | Kết quả lọc đúng theo tên; selection và số lượng item đã chọn được giữ nguyên  |

---

## Phụ lục A - Ma trận truy vết Jira

| Jira                                           | Requirement                                             | Nội dung                                 |
| ---------------------------------------------- | ------------------------------------------------------- | ---------------------------------------- |
| SF-1                                           | Toàn bộ SRS, DR-01 đến DR-07, IR-01 đến IR-03           | Epic Online Shop Sales and Inventory MVP |
| SF-10                                          | §4, §7, §8                                              | Core Domain Model                        |
| SF-2, SF-36, SF-37, SF-38, SF-39               | FR-01, IR-02, IR-03                                     | Product Catalog                          |
| SF-3, SF-11, SF-12, SF-13, SF-35, SF-43, SF-44 | FR-02, BR-01 đến BR-04, BR-15, NFR-01 đến NFR-03, IR-01 | Create Customer Order                    |
| SF-4, SF-40, SF-41, SF-42                      | FR-03, BR-05, NFR-01, NFR-04                            | Payment Simulation                       |
| SF-5, SF-49                                    | FR-04, BR-06, BR-07, NFR-01, NFR-04 đến NFR-06          | Delivery Status                          |
| SF-6, SF-14, SF-15, SF-16                      | FR-05, BR-01, BR-02, BR-12, BR-13, NFR-01 đến NFR-02    | Inventory Management                     |
| SF-7                                           | FR-06, BR-11, BR-13, NFR-01                             | Supplier Receiving                       |
| SF-8, SF-17, SF-18, SF-19                      | FR-07, BR-08 đến BR-10, BR-13, NFR-01, NFR-04           | Customer Return                          |
| SF-9                                           | FR-08, BR-14                                            | Low-stock Alert                          |
| SF-47                                          | FR-01.7, IR-04, AC-24                                   | Customer Storefront UX                   |

---

## Phụ lục B - Giả định và giới hạn

- Customer profile là optional; guest order dùng snapshot trên Order.
- Cancellation, refund, payment gateway và COD workflow riêng không thuộc
  acceptance baseline.
- Low-stock alert là indicator trong hệ thống, không phải outbound notification.
- Yêu cầu performance định lượng sẽ được bổ sung khi stakeholder phê duyệt mục
  tiêu đo được.

---

## Lịch sử phiên bản

| Phiên bản | Ngày       | Thay đổi                                                                                                                 |
| --------- | ---------- | ------------------------------------------------------------------------------------------------------------------------ |
| 1.3       | 17/07/2026 | Chuyển currency baseline từ USD sang VND; quy định giá/amount là số nguyên và giữ payment schema tương thích             |
| 1.2       | 17/07/2026 | Chốt CARD-only/USD, mã hóa yêu cầu dữ liệu/giao diện và hòa giải payment schema với MVP                                  |
| 1.1       | 16/07/2026 | Tách roadmap, quy trình phát triển, stack và thiết kế vật lý khỏi SRS; làm rõ validation, lifecycle và integrity tồn kho |
| 1.0       | 21/06/2026 | Baseline SRS từ Epic SF-1, Jira backlog, README và database schema                                                       |
