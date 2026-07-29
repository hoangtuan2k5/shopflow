# ShopFlow — Authentication API Specification

**Phiên bản:** 1.2

**Ngày cập nhật:** 29/07/2026

**Liên quan:**

- [SF-72: Log In to ShopFlow](https://tuanwork.atlassian.net/browse/SF-72)
- [SF-77: Define Authentication Rules and API Contract](https://tuanwork.atlassian.net/browse/SF-77)
- [`SRS.md`](./SRS.md) — FR-09, BR-16, NFR-09 đến NFR-12
- [`database-schema.md`](./database-schema.md) — bảng `users`

---

## 1. Tổng quan

Trước SF-72, ShopFlow không có xác thực: mọi endpoint đều mở và "vai trò" chỉ là
metadata của route ở frontend. Contract này định nghĩa lớp xác thực đầu tiên —
**ai đang gọi API** — và dừng ở đó.

Phân quyền theo vai trò (endpoint nào cho vai trò nào) **không thuộc** contract
này; nó sẽ được định nghĩa riêng cho [SF-73](https://tuanwork.atlassian.net/browse/SF-73).
SF-72 chỉ đưa Spring Security vào với cấu hình cho phép mọi request như hiện
trạng, cộng thêm ba endpoint dưới đây.

| Thao tác | Endpoint |
| --- | --- |
| Đăng nhập | `POST /auth/login` |
| Đăng xuất | `POST /auth/logout` |
| Đọc phiên hiện tại | `GET /auth/session` |

## 2. Quyết định thiết kế

### 2.1 Session cookie thay vì JWT

Chọn **session phía server, định danh bằng cookie `HttpOnly`**. Lý do:

| Tiêu chí | Session cookie | JWT trong localStorage |
| --- | --- | --- |
| XSS đọc được credential | Không (`HttpOnly`) | Có |
| Đăng xuất vô hiệu ngay | Có | Không, phải thêm blocklist |
| Cần refresh token | Không | Có |
| Frontend và API khác origin | Bất lợi | Thuận lợi |

Frontend và API của ShopFlow **cùng origin** (Caddy phục vụ SPA và proxy `/api`
trên cùng domain), nên lợi thế duy nhất của JWT không áp dụng, còn hai nhược
điểm về bảo mật thì áp dụng đầy đủ.

### 2.2 Nơi lưu session và giới hạn đã biết

MVP dùng **session in-memory của servlet container**. Chỉ có một container
backend nên không cần session store dùng chung.

Giới hạn phải nói rõ: **mọi phiên mất khi backend khởi động lại**, mà deploy lại
chạy trên mỗi lần push vào `main`. Người dùng phải đăng nhập lại sau deploy.
MVP chấp nhận điều này. Khi cần khắc phục, đường nâng cấp là Spring Session JDBC
lưu vào chính PostgreSQL đang có, không cần thêm hạ tầng.

### 2.3 Thuộc tính cookie và CSRF

| Thuộc tính | Giá trị | Lý do |
| --- | --- | --- |
| `HttpOnly` | bật | JavaScript không đọc được, XSS không lấy được phiên |
| `SameSite` | `Strict` | Cookie không đi kèm request từ site khác → chặn CSRF |
| `Secure` | bật ở production, tắt ở dev | Dev chạy HTTP trên localhost |
| `Path` | `/` | |

Phòng thủ CSRF gồm hai lớp:

1. `SameSite=Strict` — trình duyệt không gửi cookie phiên trong request khởi
   phát từ origin khác.
2. CSRF token của Spring Security cho các request thay đổi trạng thái, phát qua
   cookie `XSRF-TOKEN` và nhận lại ở header `X-XSRF-TOKEN`. Chi phí phía
   frontend gần bằng không vì axios đã hỗ trợ sẵn cặp tên này.

### 2.4 Băm mật khẩu

**BCrypt** (`BCryptPasswordEncoder`, strength mặc định 10). Không lưu mật khẩu
dạng đọc được ở bất kỳ đâu: không trong database, không trong log, không trong
response.

## 3. Mô hình tài khoản

Bảng `users` mới, migration `V3__create_users.sql`:

| Cột | Kiểu | Quy tắc |
| --- | --- | --- |
| `id` | `BIGSERIAL` | Khóa chính |
| `username` | `VARCHAR(100)` | `NOT NULL`, `UNIQUE`, lưu chữ thường |
| `password_hash` | `VARCHAR(255)` | `NOT NULL`, chuỗi BCrypt |
| `display_name` | `VARCHAR(255)` | `NOT NULL` |
| `role` | `VARCHAR(20)` | `NOT NULL`, `CHECK` theo enum ở mục 3.1 |
| `active` | `BOOLEAN` | `NOT NULL`, mặc định `TRUE` |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL` |

Một tài khoản gắn **đúng một** vai trò (BR-16). Nhiều vai trò cho một tài khoản
không thuộc phạm vi MVP.

### 3.1 Enum vai trò

| Giá trị | Tương ứng route frontend |
| --- | --- |
| `CUSTOMER` | `/customer` |
| `WAREHOUSE` | `/warehouse` |
| `SHOP_OWNER` | `/shop-owner` |

### 3.2 Tài khoản demo

Migration seed ba tài khoản, mỗi vai trò một tài khoản, để luồng demo hiện tại
tiếp tục chạy được sau khi bật xác thực.

Seed này nằm ở `db/migration` chứ **không** phải `db/dev` (location chỉ có ở
profile `dev`). Lý do: VPS production chính là môi trường demo, nếu chỉ seed ở
`dev` thì bản deploy sẽ không có tài khoản nào để đăng nhập.

**Cảnh báo bảo mật:** mật khẩu demo nằm trong repository nên là thông tin công
khai. Chúng chỉ được phép bảo vệ dữ liệu demo. Nếu hệ thống về sau chứa dữ liệu
thật, các tài khoản này BẮT BUỘC bị đổi mật khẩu hoặc vô hiệu hóa trước.

## 4. POST /auth/login

### Request

```json
{
  "username": "warehouse",
  "password": "..."
}
```

| Field | Bắt buộc | Quy tắc |
| --- | --- | --- |
| `username` | Có | Không rỗng sau khi trim, tối đa 100 ký tự, so khớp không phân biệt hoa thường |
| `password` | Có | Không rỗng, tối đa 200 ký tự, KHÔNG trim |

`password` không trim vì khoảng trắng có thể là một phần hợp lệ của mật khẩu.

### Response — `200 OK`

```json
{
  "userId": 2,
  "username": "warehouse",
  "displayName": "Nhân viên kho",
  "role": "WAREHOUSE"
}
```

Kèm `Set-Cookie` thiết lập phiên theo mục 2.3. Response **không bao giờ** chứa
mật khẩu hay hash.

### Lỗi

| Trường hợp | Status | Body |
| --- | --- | --- |
| `username` hoặc `password` rỗng, sai kiểu, body malformed | `400` | có `fieldErrors` |
| Tài khoản không tồn tại | `401` | thông báo chung |
| Sai mật khẩu | `401` | thông báo chung |
| Tài khoản `active = false` | `401` | thông báo chung |
| Có từ 5 lần đăng nhập sai trong 15 phút với cùng định danh | `429` | thông báo chung và header `Retry-After` |

Ba trường hợp `401` BẮT BUỘC trả **cùng một status và cùng một message**. Nếu
phân biệt được, kẻ tấn công dò ra tài khoản nào tồn tại (NFR-10).

Thời gian phản hồi cũng không nên tiết lộ: khi username không tồn tại, vẫn thực
hiện một phép so khớp BCrypt giả để tránh timing attack.

Sau 5 lần xác thực thất bại trong cửa sổ 15 phút, định danh đăng nhập đó bị giới
hạn tạm thời. `429` không cho biết định danh có tồn tại hay không; client dùng
`Retry-After` để biết khi nào có thể thử lại. Đăng nhập thành công trước ngưỡng
sẽ xoá bộ đếm. Bộ đếm hiện lưu trong một backend process, nên bị xoá khi deploy;
khi chạy nhiều backend phải thay bằng Redis có TTL. Giới hạn theo IP được đặt ở
edge sau khi chuỗi proxy đã tin cậy IP khách thật.

## 5. POST /auth/logout

Không có request body.

### Response — `204 No Content`

Phiên bị vô hiệu phía server và cookie bị xóa. Endpoint **idempotent**: gọi khi
chưa đăng nhập vẫn trả `204`, không trả lỗi.

## 6. GET /auth/session

Endpoint mà SPA gọi khi khởi động để biết có đang đăng nhập không.

### Response — `200 OK`, đã đăng nhập

```json
{
  "authenticated": true,
  "user": {
    "userId": 2,
    "username": "warehouse",
    "displayName": "Nhân viên kho",
    "role": "WAREHOUSE"
  }
}
```

### Response — `200 OK`, chưa đăng nhập

```json
{ "authenticated": false, "user": null }
```

Trạng thái ẩn danh là một câu trả lời hợp lệ, **không phải lỗi**. Trả `401` ở đây
sẽ buộc SPA phải bắt lỗi cho một luồng hoàn toàn bình thường.

## 7. Thời hạn phiên

| Thông số | Giá trị |
| --- | --- |
| Kiểu hết hạn | Idle timeout — tính từ request cuối cùng |
| Thời lượng | 8 giờ |
| Khi hết hạn | Request tiếp theo bị coi là ẩn danh |

Chọn 8 giờ để phủ một ca làm việc của nhân viên kho mà không giữ phiên vô hạn.

Khi phiên hết hạn, `GET /auth/session` trả `authenticated: false`; các endpoint
được bảo vệ (định nghĩa ở SF-73) sẽ trả `401`.

## 8. Error body

Dùng chung format của các module hiện có:

```json
{
  "message": "Invalid username or password.",
  "status": 401,
  "fieldErrors": {}
}
```

## 9. Ngoài phạm vi

Những phần sau KHÔNG thuộc MVP và không được ngầm hiểu là đã có:

- Đăng ký tự phục vụ, quên mật khẩu, đổi mật khẩu.
- Xác thực nhiều lớp, SSO, OAuth, đăng nhập mạng xã hội.
- Nhiều vai trò cho một tài khoản, phân quyền chi tiết theo từng thao tác.
- Khóa tài khoản cố định hoặc theo cấp số nhân.
- Ghi audit cho hành vi đăng nhập/đăng xuất.

## 10. Test cases tối thiểu

1. Đăng nhập đúng → `200`, body có `role`, có `Set-Cookie`, không có hash.
2. Sai mật khẩu, username không tồn tại, tài khoản `active = false` → cả ba trả
   cùng status `401` và cùng message.
3. `username` rỗng hoặc thiếu field → `400` kèm `fieldErrors`.
4. Mật khẩu có khoảng trắng đầu/cuối → so khớp đúng, không bị trim.
5. `GET /auth/session` khi chưa đăng nhập → `200` với `authenticated: false`.
6. `GET /auth/session` sau khi đăng nhập → `200` với đúng user và role.
7. `POST /auth/logout` → `204`, sau đó `GET /auth/session` trả `authenticated: false`.
8. `POST /auth/logout` khi chưa đăng nhập → `204`.
9. Đọc bản ghi `users` trong database → `password_hash` là chuỗi BCrypt, không
   phải plaintext.
10. Username so khớp không phân biệt hoa thường.
11. Lần sai thứ sáu của cùng username trong 15 phút → `429` kèm `Retry-After`.
12. Khi đủ 15 phút, định danh được thử lại; đăng nhập đúng trước ngưỡng xoá bộ đếm lỗi.
13. Sau khi bị giới hạn, định danh có thật và không tồn tại trả cùng body `429`.

## 11. Version History

| Phiên bản | Ngày | Thay đổi |
| --- | --- | --- |
| 1.2 | 29/07/2026 | Bổ sung kiểm thử hết hạn, xoá bộ đếm và chống dò tài khoản của rate limit. |
| 1.1 | 29/07/2026 | Thêm rate limit theo định danh cho đăng nhập (5 lần sai / 15 phút). |
| 1.0 | 26/07/2026 | Contract đầu tiên cho xác thực (SF-77). |
