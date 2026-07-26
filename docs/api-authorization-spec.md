# ShopFlow — Authorization API Specification

**Phiên bản:** 1.0

**Ngày cập nhật:** 26/07/2026

**Liên quan:**

- [SF-73: Restrict Access by Role](https://tuanwork.atlassian.net/browse/SF-73)
- [SF-81: Define Role Permission Matrix and Authorization Contract](https://tuanwork.atlassian.net/browse/SF-81)
- [`api-authentication-spec.md`](./api-authentication-spec.md) — phiên và vai trò
- [`SRS.md`](./SRS.md) — FR-10, BR-17, BR-18

---

## 1. Tổng quan

SF-72 trả lời "ai đang gọi". Tài liệu này trả lời "người đó được làm gì".

Nguyên tắc: **đóng mặc định**. Mọi endpoint yêu cầu vai trò, trừ những endpoint
được liệt kê công khai ở mục 3.

| Mã lỗi | Khi nào |
| --- | --- |
| `401` | Không có phiên hợp lệ mà endpoint yêu cầu xác thực |
| `403` | Có phiên hợp lệ nhưng vai trò không đủ quyền |

Request bị từ chối BẮT BUỘC không thay đổi bất kỳ dữ liệu nào.

## 2. Bốn nhóm người dùng

### 2.1 Guest — khách vãng lai, không tài khoản

Guest là người dùng hợp lệ, không phải người dùng hạng hai. Guest **được**:

- xem danh sách và chi tiết sản phẩm;
- thêm sản phẩm vào giỏ (giỏ nằm ở trình duyệt, không có API);
- tạo đơn hàng;
- thanh toán cho chính đơn vừa tạo, bằng `orderRef` nhận được (xem mục 5);
- tra cứu đơn bằng mã đơn nếu hệ thống có hỗ trợ.

Guest **không được**: xem lịch sử đơn hàng, quản lý tài khoản, lưu địa chỉ giao
hàng, quản lý thông tin cá nhân.

### 2.2 Customer — khách đã đăng nhập

Có toàn bộ quyền của Guest, cộng thêm: quản lý tài khoản, xem lịch sử đơn hàng,
quản lý địa chỉ giao hàng, theo dõi toàn bộ đơn của mình.

**Trạng thái hiện tại:** không endpoint nào trong số các quyền bổ sung này tồn
tại. Đơn hàng hiện là guest order — bảng `orders` chỉ lưu snapshot tên, email,
điện thoại và **không có khóa ngoại tới tài khoản**. Vì vậy vai trò `CUSTOMER`
hôm nay chỉ là một danh tính đăng nhập được, chưa mở thêm khả năng nào. Nối đơn
hàng với tài khoản là việc riêng, cần migration và thay đổi `OrderService`.

### 2.3 Warehouse — nhân viên kho

Chỉ vận hành hàng hoá:

xem đơn đã xác nhận · kiểm tra tồn kho · nhập kho · xuất kho · picking · packing
· xác nhận đã đóng gói · xác nhận đã xuất kho · nhập lại hàng hoàn trả · cập
nhật số lượng tồn kho.

Warehouse **KHÔNG** được: quản lý sản phẩm, quản lý khách hàng, quản lý tài
khoản người dùng, quản lý giá bán, xác nhận hoặc huỷ đơn hàng, hoàn tiền, xử lý
khiếu nại, xem doanh thu hoặc lợi nhuận, thay đổi cấu hình hệ thống.

### 2.4 Shop Owner — chủ shop

Toàn bộ quyền của Warehouse, cộng quản trị: sản phẩm (CRUD) · danh mục · giá bán
· khách hàng · tài khoản người dùng · phân quyền · quản lý đơn hàng · xác nhận
đơn · từ chối hoặc huỷ đơn · sửa đơn trước khi xuất kho · xử lý đổi trả · hoàn
tiền · dashboard · doanh thu và báo cáo · cấu hình cửa hàng.

## 3. Ma trận quyền theo endpoint

`✓` được phép, `—` bị từ chối.

| Endpoint | Guest | CUSTOMER | WAREHOUSE | SHOP_OWNER |
| --- | :---: | :---: | :---: | :---: |
| `GET /products` | ✓ | ✓ | ✓ | ✓ |
| `GET /products/{id}` | ✓ | ✓ | ✓ | ✓ |
| `POST /orders` | ✓ | ✓ | ✓ | ✓ |
| `POST /orders/{orderRef}/payments` | ✓ | ✓ | ✓ | ✓ |
| `POST /auth/login` | ✓ | ✓ | ✓ | ✓ |
| `POST /auth/logout` | ✓ | ✓ | ✓ | ✓ |
| `GET /auth/session` | ✓ | ✓ | ✓ | ✓ |
| `GET /inventory` | — | — | ✓ | ✓ |
| `POST /inventory/{productId}/adjustments` | — | — | ✓ | ✓ |
| `PUT /inventory/{productId}/threshold` | — | — | ✓ | ✓ |
| `POST /receivings` | — | — | ✓ | ✓ |
| `GET /deliveries` | — | — | ✓ | ✓ |
| `PATCH /orders/{orderId}/delivery` | — | — | ✓ | ✓ |
| `GET /returns` | — | — | ✓ | ✓ |
| `GET /returns/orders` | — | — | — | ✓ |
| `POST /returns` | — | — | — | ✓ |
| `PATCH /returns/{returnId}` | — | — | một phần | ✓ |

### 3.1 Ba ô cần giải thích

**`PUT /inventory/{productId}/threshold` — cho cả kho.** Ngưỡng cảnh báo nằm
giữa "cập nhật tồn kho" (kho được làm) và "thay đổi cấu hình hệ thống" (kho
không được làm). Chọn cho kho vì người biết ngưỡng đúng là người trông kho, và
vì hôm nay chủ shop chưa có đường vào màn hình tồn kho.

**`POST /returns` và `GET /returns/orders` — chỉ chủ shop.** Mở một yêu cầu đổi
trả là quyết định thương mại, thuộc mục "xử lý đổi/trả" của chủ shop. Khách
không thể tự mở vì đơn guest không gắn với tài khoản nào.

**`PATCH /returns/{returnId}` — phân quyền phụ thuộc nội dung.** Một endpoint
mang ba chuyển trạng thái thuộc hai vai trò khác nhau:

| `toStatus` | WAREHOUSE | SHOP_OWNER | Lý do |
| --- | :---: | :---: | --- |
| `APPROVED` | — | ✓ | Duyệt đổi trả là quyết định thương mại |
| `REJECTED` | — | ✓ | Từ chối khiếu nại là quyết định thương mại |
| `RESTOCKED` | ✓ | ✓ | Đúng nghĩa "nhập lại hàng hoàn trả" của kho |

Quy tắc URL không diễn đạt được điều này. Kiểm tra BẮT BUỘC nằm trong service,
đọc `toStatus` của request. Đây là điểm dễ sai nhất: gắn
`hasRole('WAREHOUSE')` ở mức phương thức là vô tình trao quyền duyệt hoàn tiền
cho nhân viên kho.

## 4. CSRF

Từ khi có endpoint yêu cầu phiên, CSRF được bật lại cho mọi request thay đổi
trạng thái. Hai lớp:

1. Cookie phiên đã đặt `SameSite=Strict` nên không đi kèm request từ origin khác.
2. CSRF token của Spring Security, phát qua cookie `XSRF-TOKEN`, nhận lại ở
   header `X-XSRF-TOKEN`. Axios hỗ trợ sẵn cặp tên này nên chi phí frontend gần
   bằng không.

Ba endpoint công khai không dùng phiên — `POST /orders` và
`POST /orders/{orderRef}/payments` — vẫn cần token vì trình duyệt của guest cũng
có cookie CSRF. `POST /auth/login` được miễn: người gọi chưa có phiên để bảo vệ.

## 5. Vì sao thanh toán không nằm trong ma trận vai trò

Guest phải thanh toán được, nên endpoint không thể yêu cầu vai trò. Việc kiểm
soát nằm ở chỗ khác: đơn hàng được định danh bằng `orderRef` không đoán được
thay vì id tuần tự (xem [SF-85](https://tuanwork.atlassian.net/browse/SF-85) và
[`api-payment-simulation-spec.md`](./api-payment-simulation-spec.md)). Ai giữ
tham chiếu thì trả được tiền cho đơn đó — đúng bằng khả năng mà người đặt đơn
cần có.

## 6. Đường kỹ thuật không thuộc nghiệp vụ

| Đường dẫn | Truy cập | Ghi chú |
| --- | --- | --- |
| `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | Công khai | Docker healthcheck và CI smoke test cần |
| `/actuator/info` | Đóng | `management.info.env.enabled: true` sẽ công bố mọi property `info.*` khi có |
| `/v3/api-docs`, `/swagger-ui.html` | Chỉ dev và test | Ở staging tắt bằng property, không bằng luật bảo mật: endpoint đã tắt thì không thể cấu hình nhầm thành mở |

## 7. Quyền trong mô hình RBAC CHƯA có endpoint

Danh sách này tồn tại để không ai đọc ma trận rồi tưởng các quyền dưới đây đã
được triển khai. **Chúng chưa có endpoint nào.**

**Phía Warehouse**

- Xem đơn đã xác nhận — chưa có API danh sách đơn; `GET /deliveries` chỉ liệt kê
  đơn đã thanh toán, không phải "đã xác nhận".
- Picking, packing, xác nhận đã đóng gói, xác nhận đã xuất kho — cả bốn hiện gộp
  vào một `PATCH /orders/{orderId}/delivery` với ba bước
  `PREPARING`/`SHIPPED`/`DELIVERED`.
- Xuất kho — chỉ diễn đạt được bằng `delta` âm trên endpoint điều chỉnh tồn kho.

**Phía Shop Owner**

Quản lý sản phẩm (catalog hiện chỉ đọc) · danh mục · giá bán · quản lý khách
hàng · quản lý tài khoản người dùng và phân quyền · xác nhận, từ chối, huỷ đơn
(`OrderStatus.CANCELLED` có trong enum nhưng không endpoint nào chạm tới được) ·
sửa đơn trước khi xuất kho · đổi hàng · hoàn tiền · dashboard · báo cáo doanh
thu · cấu hình cửa hàng.

Tổng cộng khoảng 13 quyền chưa có endpoint và 4 quyền chỉ có bản thay thế thô
hơn. Ma trận ở mục 3 phủ 17 endpoint đang tồn tại, không phải toàn bộ mô hình
tổ chức.

## 8. Rủi ro đã biết, chấp nhận trong MVP

**`GET /deliveries` trả `totalAmount` và thông tin người nhận cho nhân viên
kho.** Quy tắc nói kho không được xem doanh thu. Tổng tiền từng đơn không phải
báo cáo doanh thu, nhưng là nguyên liệu để dựng ra, và là dữ liệu cá nhân của
khách. Cách xử lý đúng là cắt `totalAmount` khỏi projection dành cho kho chứ
không phải chặn cả endpoint. Chưa làm trong SF-73.

## 9. Test cases tối thiểu

1. Với mỗi endpoint không công khai: ẩn danh trả `401`.
2. Với mỗi endpoint không công khai: sai vai trò trả `403`.
3. Với mỗi endpoint: đúng vai trò thành công.
4. Sau một request bị chặn, dữ liệu liên quan không đổi.
5. Guest xem catalog, tạo đơn và thanh toán bằng `orderRef` — cả ba thành công.
6. WAREHOUSE chuyển return sang `RESTOCKED` thành công; sang `APPROVED` hoặc
   `REJECTED` trả `403` và trạng thái return không đổi.
7. SHOP_OWNER thực hiện được cả ba chuyển trạng thái của return.
8. Request thay đổi trạng thái thiếu CSRF token bị từ chối.
9. `/actuator/health` truy cập được khi ẩn danh.

## 10. Version History

| Phiên bản | Ngày | Thay đổi |
| --- | --- | --- |
| 1.0 | 26/07/2026 | Ma trận quyền đầu tiên, ranh giới Guest/Customer/Warehouse/Shop Owner (SF-81). |
