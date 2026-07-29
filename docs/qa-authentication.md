# Authentication QA Checklist

**Ngày kiểm tra:** 29/07/2026

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
| 10 | Lần sai thứ sáu theo cùng username, không phân biệt hoa thường, trả `429` kèm `Retry-After` | `limitsRepeatedFailuresForTheSameUsername` | Pass |
| 11 | Hết đúng cửa sổ 15 phút thì định danh được thử lại | `allowsAttemptsWhenTheFailureWindowExpires` | Pass |
| 12 | Khi đã bị giới hạn, tài khoản có thật và định danh không tồn tại trả cùng body `429` | `limitsKnownAndUnknownAccountsWithoutRevealingWhichExists` | Pass |
| 13 | Login đúng qua API xoá số lần sai trước đó | `successfulLoginClearsPreviousFailures` | Pass |
| 14 | Năm lượt đang xác thực giữ đủ năm chỗ; lượt thứ sáu bị chặn | `reservesOnlyFiveAttemptsBeforeAuthenticationCompletes` | Pass |

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
# Tests run: 116, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS

cd frontend && npx playwright test
# 28 passed
```

## Giới hạn đã biết

- Phiên lưu trong bộ nhớ tiến trình: mỗi lần deploy là mọi người phải đăng nhập
  lại. Đường nâng cấp là Spring Session JDBC trên chính PostgreSQL đang có.
- Rate limit đăng nhập cũng lưu trong bộ nhớ một backend process và reset khi deploy; khi chạy
  nhiều backend, thay bằng Redis có TTL. Rate limit theo IP thuộc edge sau khi proxy tin cậy IP khách.
- Mật khẩu tài khoản demo nằm trong migration nên là thông tin công khai.
