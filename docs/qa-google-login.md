# Kiểm chứng đăng nhập Google — SF-146

Ngày 2026-11-06, nhánh `feature/SF-146-google-customer-login`, nền develop
`27ab56d` sau PR #148. Worktree riêng `/home/ubuntu/workspace/shopflow-sf146`;
giữ nguyên các worktree triển khai SF-142–145 làm bản đối chiếu.

## Phạm vi

Spring Security OAuth2/OIDC theo BOM Boot4.0.6 (Security7.0.5), Authorization
Code + PKCE S256, scopes openid/email/profile. Google subject map về user nội
bộ; session API vẫn có đúng payload cũ. Email trùng local account không tự gộp:
proof server-side có hạn5 phút, nhập mật khẩu hiện tại và đồng ý liên kết.

Không có migration mới, không đổi role/password/order owner của account cũ.
Google-only không có local password. Signup chưa hoàn tất bị expire và xóa
pending hash khi Google tạo account. Username demo và reset OTP giữ nguyên.

## Môi trường và lệnh

Java21, Maven wrapper, Testcontainers PostgreSQL18.4; các test migration và H2
cũ vẫn chạy. Key/secret trong test là giả; không nạp `.env` workspace/production.

| Kiểm tra | Lệnh/phạm vi | Kết quả |
| --- | --- | --- |
| Backend đầy đủ | `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify` | 231 qua, 0 failure/error/skip; đóng gói JAR thành công |
| Frontend build | `npm --prefix frontend run build` | Qua; dependency còn warning PURE annotation đã có |
| Frontend lint | `npm --prefix frontend run lint:ci` | Qua, 0 warning/error |
| Caddy | `caddy:2.11.4-alpine caddy adapt --config /etc/caddy/Caddyfile --adapter caddyfile` | Qua; assert Host, scheme HTTPS và xóa Forwarded trong JSON đã adapt |
| Diff | `git diff --check` | Qua |

## OIDC và tài khoản

12 GoogleLoginTests dùng HTTP provider loopback có RSA2048/JWKS riêng. Test
không mock một principal đã xác thực: Spring thực sự đổi code và kiểm ID token.
Provider giả kiểm code một lần, redirect URI và SHA256(code_verifier) so với
code_challenge. Client chỉ xin ba scopes; không gửi client secret trong start URL.

- New/returning subject dùng cùng user ID. Email claim thay đổi không đổi email
  đã lưu. Google-only hash null; response session không lộ Google token/subject.
- Invalid signature, issuer, audience, nonce, expiry, email/email_verified và
  state bị từ chối trước tạo account. Wrong-session và flow quá300 giây bị chặn.
- Hai Host production sinh đúng HTTPS callback với `/api`; forwarded host/scheme
  giả không đổi callback. Host lạ, returnTo ngoài whitelist, callback đổi origin
  bị từ chối. State và S256 challenge được tạo; không persist authorized clients.
- Local collision chỉ tạo pending proof, session vẫn anonymous. Sai mật khẩu401;
  đúng mật khẩu link cùng user/role, rotate session và consume proof. Replay,
  cross-session, expiry, reset auth_version và inactive giữa callback/link đều
  không link. Body thừa và thiếu CSRF/Origin hợp lệ bị từ chối.
- Link dùng chung login failure budget:5 lần sai rồi429 + Retry-After. Callback
  đồng thời cùng subject chỉ tạo một account; hai subject tranh link cùng user
  chỉ một200, request còn lại400. Inactive và Google-only subject khác bị từ chối.
- Google tạo account làm pending REGISTER thành EXPIRED, verifier/hash/name null;
  không thừa kế mật khẩu do người khác đăng ký trước.
- Callback cancellation chỉ đưa marker chung về frontend, giữ returnTo hợp lệ
  trong server session; password login sau đó trả Location theo intent. Không
  phản chiếu provider error_description vào URL.
- Login đổi sang AccountPrincipal và rotate session; GET session cấp XSRF cookie
  mới; logout đầu tiên dùng chính cookie/header thật từ response thành công.
  Test nhóm Google không dùng helper mock CSRF để tránh thay cookie repository.

GoogleDisabledTests chứng minh thiếu credentials trả503 trước tạo session hoặc
lookup provider. AuthControllerTests và bộ đăng ký/reset đầy đủ vẫn qua.

Snapshot API chỉ thêm `/auth/google/link` và schema `GoogleLinkRequest`; đã so
sánh mọi path/schema cũ giữ nguyên trước cập nhật snapshot và chạy lại suite.

## Browser và giới hạn nghiệm thu

`npm --prefix frontend run test:e2e -- --trace on`:82 test qua, không retry,
chạy sau backend để tránh thay đổi mạng Docker giữa lượt browser. Năm test mới
phủ URL start/returnTo an toàn, link khi có phiên cũ, sai mật khẩu, proof hết hạn,
Location của password fallback, không lưu secret và giao diện Việt trên mobile.

Python Playwright với helper `webapp-testing/with_server.py` kiểm form thật ở
desktop1280 và mobile390; đã xem trực tiếp ảnh `/tmp/sf146-desktop-link.png` và
`/tmp/sf146-mobile-link.png`, không tràn ngang. Session API được mock. Helper
đã dừng Vite sau kiểm tra, không dừng dịch vụ workspace khác.

Test UI dùng API mock; kết quả OIDC ở trên là filter/service/database với provider loopback.
Không coi cả hai loại này là đăng nhập Google thật hoặc kiểm cookie qua proxy
public. Chưa đổi Google Console, DNS, gửi thư thật hay triển khai production.

Live acceptance còn cần chủ tài khoản thử trên từng public domain: consent,
callback/session, first CSRF write, logout, cancellation và collision/link;
kiểm tra cookie Secure/HttpOnly/Lax thực tế. Hai domain dùng chung database,
không có cookie SSO. Cart persistence thuộc SF-147, owner checkout thuộc SF-148.

## Account navigation and Google button follow-up

The Google login link uses the official local PNG from
https://developers.google.com/static/identity/images/g-logo.png on a white button,
with its original aspect ratio retained. Branding reference:
https://developers.google.com/identity/branding-guidelines.

The storefront link shows the authenticated display name rather than Workspace.
Its label has at most24 grapheme clusters (23 plus an ellipsis for long names);
the complete name remains in the title and accessible label. This only limits
presentation, not the stored account name. Staff headers reuse the same label.
Responsive checks cover320,390,768,1024 and1280px with a long Vietnamese name and
a family emoji; no horizontal overflow. Existing role destinations are retained.

`npm --prefix frontend run test:e2e`:99 passed after the follow-up; build and
lint:ci passed. Screenshots of the Google button and mobile/desktop account labels
were inspected. Session APIs in these UI tests are mocked; backend OAuth behavior
is unchanged. These local UI changes have not been deployed by release PR155.
