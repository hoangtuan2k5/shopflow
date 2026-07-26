# Authentication QA Checklist

**Ngày kiểm tra:** 26/07/2026

**Scope:** `POST /auth/login`, `POST /auth/logout`, `GET /auth/session`, lưu mật
khẩu và màn hình đăng nhập.

## Backend scenarios

| # | Scenario | Evidence | Kết quả |
| --- | --- | --- | --- |
| 1 | Đăng nhập đúng trả vai trò và tên hiển thị, thiết lập phiên, response không chứa hash | `AuthControllerTests.signsInAnActiveAccountAndReturnsItsRole` | Pass |
| 2 | Sai mật khẩu, định danh không tồn tại và tài khoản bị khóa trả **cùng** status, message và `fieldErrors` rỗng | `rejectsWrongPasswordUnknownAccountAndDeactivatedAccountIdentically` | Pass |
| 3 | Thiếu hoặc sai kiểu định danh trả `400` kèm `fieldErrors` | `rejectsMalformedCredentialsRequests` | Pass |
| 4 | Mật khẩu giữ nguyên khoảng trắng đầu cuối; định danh không phân biệt hoa thường | `keepsSurroundingWhitespaceInPasswordsAndIgnoresUsernameCase` | Pass |
| 5 | Đọc phiên khi chưa đăng nhập trả `200` với `authenticated: false`, không phải lỗi | `reportsAnonymousSessionWithoutFailing` | Pass |
| 6 | Phiên tồn tại qua nhiều request và bị vô hiệu sau đăng xuất | `carriesTheSignedInAccountAcrossRequestsUntilLogout` | Pass |
| 7 | Đăng xuất khi chưa đăng nhập vẫn trả `204` | `treatsLogoutWithoutASessionAsSuccess` | Pass |
| 8 | Mật khẩu tài khoản demo lưu dạng BCrypt, không phải plaintext | `storesSeededDemoPasswordsOnlyAsBcryptHashes` | Pass |
| 9 | Ba tài khoản demo đăng nhập được với đúng vai trò | `signsInEachSeededDemoAccountWithItsDocumentedRole` | Pass |

Scenario 2 là điểm dễ bỏ sót: nếu ba trường hợp trả lỗi khác nhau thì kẻ tấn
công dò được tài khoản nào có thật. Ngoài nội dung lỗi, thời gian phản hồi cũng
đồng nhất vì tài khoản bị khóa đi cùng nhánh xử lý với tài khoản không tồn tại,
và `DaoAuthenticationProvider` vẫn chạy một phép so khớp giả.

## Browser scenarios

| Scenario | Evidence | Kết quả |
| --- | --- | --- |
| Đăng nhập thành công vào đúng khu vực theo vai trò và hiện tên người dùng | `login.spec.ts` — signing in | Pass |
| Sai thông tin: giữ lại định danh, xoá mật khẩu, hiện một thông báo chung | `login.spec.ts` — rejected credentials | Pass |
| Bỏ trống: chặn tại client, không request nào rời trình duyệt | `login.spec.ts` — empty credentials | Pass |
| Phiên sống qua reload; đăng xuất đưa về trang đăng nhập | `login.spec.ts` — existing session | Pass |
| Đã đăng nhập mở `/login` thì bị đưa về khu vực của mình | `login.spec.ts` — already signed in | Pass |
| Form đăng nhập hiển thị đủ tiếng Việt | `login.spec.ts` — Vietnamese | Pass |

## Commands và kết quả

```bash
cd backend && bash ./mvnw -B spotless:check clean verify
# Tests run: 107, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS

cd frontend && npx playwright test
# 28 passed
```

## Giới hạn đã biết

- Phiên lưu trong bộ nhớ tiến trình: mỗi lần deploy là mọi người phải đăng nhập
  lại. Đường nâng cấp là Spring Session JDBC trên chính PostgreSQL đang có.
- Endpoint đăng nhập chưa có giới hạn brute-force.
- Mật khẩu tài khoản demo nằm trong migration nên là thông tin công khai.
