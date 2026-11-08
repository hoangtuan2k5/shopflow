# ShopFlow — Customer Identity and Checkout Contract (SF-141)

**Phiên bản:** 1.1 — 06/11/2026

**Trạng thái:** SF-142–SF-148 đã thực hiện trong source; live acceptance và rollout
chưa hoàn tất. Xem [ma trận SF-149](qa-customer-authentication.md).

**Jira:** [SF-140](https://tuanwork.atlassian.net/browse/SF-140),
[SF-141](https://tuanwork.atlassian.net/browse/SF-141); triển khai SF-142–SF-149.

## 1. Phạm vi và baseline

Baseline source là `13b92154` trên `develop` (v0.12.1): username/password,
session in-memory, `SameSite=Strict`, login/session/logout, guest order/payment.
Tại thời điểm SF-141, các API mới chưa tồn tại và task đó chỉ sửa tài liệu.
SF-142–SF-148 sau đó bổ sung implementation; baseline trên là lịch sử, không
phải trạng thái source hiện tại hoặc bằng chứng production đã cập nhật.

Contract này có hiệu lực khi các task triển khai được nghiệm thu và cutover;
nó thay phần guest checkout và giới hạn auth cũ trong
[authentication](api-authentication-spec.md),
[authorization](api-authorization-spec.md),
[order](api-customer-order-spec.md), [payment](api-payment-simulation-spec.md).
Các quy tắc giá, snapshot, reserve/release, delivery và returns giữ nguyên.

Chủ repo đã chốt: email/password và Google; OTP xác minh đăng ký và reset,
không passwordless login/MFA; giữ demo; mọi role được mua đơn của chính mình.
Hai domain dùng chung DB/tài khoản/đơn, **cookie và giỏ riêng từng origin**.
Không thêm SSO, đồng bộ giỏ giữa domain, profile/address book/order history,
đổi email, thêm password cho Google-only hoặc thanh toán ngân hàng thật.

## 2. Giá trị và validation

| Giá trị | Contract |
| --- | --- |
| Email tài khoản | Trim ngoài, lowercase bằng `Locale.ROOT`, ASCII email tối đa 254 ký tự; validator server kiểm local/domain; không hỗ trợ quoted local part hoặc internationalized email trong đợt này |
| Alias email | Không xóa dấu chấm, không bỏ `+tag`; không suy diễn hai địa chỉ là một hộp thư |
| Tên hiển thị | Trim ngoài, 1–255 Unicode code point; escape khi render HTML/email |
| Password **mới** | Tối thiểu 8 Unicode code point; ít nhất một chữ hoa (`Lu`), chữ thường (`Ll`), chữ số ASCII `0–9`, ký tự dấu câu/ký hiệu Unicode (`P` hoặc `S`); khoảng trắng không tính là ký tự đặc biệt |
| Password và BCrypt | Không trim/normalize; tối đa 72 byte UTF-8, từ chối quá giới hạn, không truncate; BCrypt cost 10 theo encoder hiện có |
| Password khi login | Giữ validation legacy: nonblank, tối đa 200 UTF-16 code unit; không áp composition/minimum mới cho demo. Không đưa chuỗi quá 72 byte vào BCrypt; trả rejection chung, không lỗi 500 |
| OTP | Chuỗi đúng 6 chữ số ASCII, giữ số 0 đầu; CSPRNG; **10 phút** kể từ phát hành, `now >= expires_at` là hết hạn |
| Thời gian | UTC, `TIMESTAMPTZ`, clock server; UI countdown không quyết định mã còn hạn |
| `challengeId` | UUID ngẫu nhiên, không phải user ID; gắn session trình duyệt, email và purpose phía server; không đủ để login/reset nếu thiếu OTP |
| `returnTo` | Mặc định `/customer`; đường dẫn tương đối nội bộ, bắt đầu đúng một `/`, chỉ `/customer` và query `checkout=1` hoặc đường vận hành mà role được tới; từ chối scheme/host, `//`, backslash, CR/LF và biến thể percent-encoded tương ứng |

Ví dụ password mới hợp lệ `Abcdef1!`; `abcdef1!`, `ABCDEF1!`, `Abcdefg!`,
`Abcdef12` hoặc chuỗi 7 ký tự đều không đạt. Test riêng Unicode và 72/73 byte.
Trường sai kiểu JSON/unknown field trả 400; không nhận role/active/hash/user ID
từ client trong signup/reset/link. Login `username` được mở rộng max 254;
định danh chứa `@` lookup email chuẩn hóa, còn lại lookup username legacy.
Username kỹ thuật của account mới không được dùng làm alias login để né limit.

## 3. Session, CSRF và hợp đồng lỗi chung

Giữ HttpOnly session, idle timeout 8 giờ, session store in-memory (SF-104 riêng).
Đổi cookie phiên sang **SameSite=Lax** cho callback Google GET; Secure ở
production, Path `/`, không đặt Domain dùng chung. Hai root domain không chia
sẻ cookie. Luôn rotate session sau login/link thành công; không đặt token Google
vào cookie JavaScript đọc được, localStorage, query hoặc response JSON.

`GET /auth/session` vẫn trả shape legacy; đồng thời materialize cookie
`XSRF-TOKEN` ngay ở lần GET đầu, cả guest. Sau login/logout, client gọi lại GET
này lấy token mới **trước** write. Dùng `csrf.spa()` và cookie/header thô
`X-XSRF-TOKEN`, không tái dùng handler XOR cũ (SF-101).

Giữ exemption duy nhất cho `POST /auth/login`. Registration, verify, resend,
reset, Google link và mọi business write yêu cầu CSRF, kể cả guest. Google
authorization/callback dùng GET + state/nonce session-bound, không tắt CSRF toàn
cục. Từ chối Origin ngoài allowlist của môi trường; không mở credentialed CORS
cho origin tùy ý. Local cùng origin qua Vite proxy được khai báo riêng.

Lỗi auth dùng shape sẵn có (không thêm `code` vào toàn bộ API):

```json
{
  "message": "Invalid or expired code",
  "status": 400,
  "fieldErrors": {}
}
```

| Trường hợp | HTTP / body |
| --- | --- |
| JSON/field sai | 400, `message: Invalid credentials request`, `fieldErrors` trỏ đúng input; malformed JSON dùng `Malformed request body` |
| Password sai, unknown, inactive, Google-only hoặc email chưa được xác minh | 401, `Invalid username or password`, `fieldErrors: {}`; không phân biệt nguyên nhân |
| OTP sai/expired/consumed/superseded, purpose/session sai, challenge không tồn tại | 400, `Invalid or expired code`, `fieldErrors: {}` |
| Rate/cooldown/budget | 429, `Too many authentication attempts`, `fieldErrors: {}`, `Retry-After` giây nguyên dương làm tròn lên |
| Thiếu/sai CSRF hoặc Origin bị cấm | 403 trước side effect; auth endpoints dùng shape trên với `Forbidden request`, không giả thành sai OTP |
| Cần login vào order/payment hoặc link proof đã mất | Order/payment 401 không redirect HTML; link thiếu proof trả 400 như mã không hợp lệ |
| Cấu hình mail thiếu/toàn dịch vụ không sẵn sàng, không phụ thuộc account tồn tại | 503, `Authentication service unavailable`, `fieldErrors: {}` |

Lỗi business order/payment giữ shape cũ; xem mục 8. Với request sai nhiều lớp,
CSRF/Origin chạy trước authentication, rồi structural validation, rồi ownership
và nghiệp vụ. Các assertion 401/404/409 giả định JSON và CSRF hợp lệ.

Mọi response auth đặt `Cache-Control: no-store`; không log OTP, password,
challengeId, Google token, state, cookie, Authorization hoặc full email/body.
Không dùng `401` từ login sai để gây redirect loop trong interceptor frontend.

## 4. Endpoints email và password

Các URL trong bảng là **backend mappings**. Trình duyệt gọi thêm `/api` qua
Caddy; response JSON không chứa password/hash/Google subject/verifier.

| Method/path | Body | Thành công / side effect |
| --- | --- | --- |
| `POST /auth/registrations` | `{ "email": "buyer@example.com", "password": "Abcdef1!", "displayName": "Khách hàng" }` | 202 ChallengeResponse; giữ pending credential hash, **chưa tạo user hoặc session login** |
| `POST /auth/registrations/verify` | `{ "challengeId": "UUID", "otp": "012345" }` | 204; consume REGISTER, atomically tạo active CUSTOMER có email đã xác minh; không auto-login |
| `POST /auth/otp/resend` | `{ "challengeId": "UUID" }` | 202 ChallengeResponse mới, đúng purpose/email/session cũ; vô hiệu generation trước; chỉ ISSUED/FAILED chưa hết hạn, recheck eligibility |
| `POST /auth/password/forgot` | `{ "email": "buyer@example.com" }` | 202 ChallengeResponse với purpose RESET_PASSWORD; chỉ account active, có email đã xác minh và local hash được gửi mã |
| `POST /auth/password/reset` | `{ "challengeId": "UUID", "otp": "012345", "newPassword": "Newpass2!" }` | 204; verify OTP + consume + update hash + tăng credential version trong **một transaction** |
| `POST /auth/login` | `{ "username": "buyer@example.com", "password": "Abcdef1!" }` hoặc demo username | 200 AuthenticatedUser và rotate session; Location nếu còn intent OAuth hợp lệ |
| `POST /auth/logout` | Không body | 204 idempotent; invalidate session và proof Google chưa hoàn tất |
| `GET /auth/session` | Không body | 200 SessionResponse; bootstrap XSRF cookie |

Không thêm reset-verify endpoint/token/session quyền hạn riêng. UI có thể nhập
OTP trước, nhưng chỉ endpoint reset kết hợp mới xác thực mã và đổi password;
không báo OTP đã đúng trước response thành công. Confirm password là validation
UI, không gửi hai bản password về server.

ChallengeResponse (cùng shape cho account có/không đủ điều kiện):

```json
{
  "challengeId": "77646dc8-5cab-42c9-9c5f-99f49123c445",
  "message": "If eligible, a code will be sent to the supplied email",
  "expiresInSeconds": 600,
  "resendAfterSeconds": 60
}
```

202 chỉ xác nhận nhận yêu cầu, không khẳng định email tồn tại/gửi thành công.
Registration cho email đã có user, forgot cho unknown/inactive/Google-only đều
trả cùng 202 và challenge ngẫu nhiên (synthetic, không gửi mail). Không tự đổi
password/role/tên của account hiện có. Verify synthetic luôn trả lỗi OTP chung.
Mã chỉ dùng từ session đã tạo challenge: reload không lưu challenge/password
trong storage; mất flow thì request lại theo rate limit. Hai flow cùng email và
purpose chỉ generation mới nhất dùng được; khác purpose không thay thế nhau.

AuthenticatedUser giữ **bốn field cũ**, không bắt frontend đổi toàn bộ fixtures:

```json
{ "userId": 21, "username": "u_b796e77d-63a1-4549-9725-7a64c29f4793", "displayName": "Khách hàng", "role": "CUSTOMER" }
```

Username mới do server sinh `u_` + UUID (38 ký tự), không phải địa chỉ để login.
Legacy `customer`/`warehouse`/`owner`, IDs/hashes/roles không đổi. SessionResponse
vẫn `{ "authenticated": true, "user": AuthenticatedUser }` hoặc
`{ "authenticated": false, "user": null }`; anonymous GET không trả 401.

Response 200 của login/link giữ nguyên JSON nhưng trả header `Location` là
returnTo đã validate/lưu server nếu có intent OAuth còn hạn. Frontend đọc
header, refresh session/XSRF rồi router.replace tới đó; HTTP200 không tự
redirect. Không có saved intent thì login dùng redirect query đã validate hoặc
homePath theo role. Không chép returnTo/state từ callback query chưa xác thực.

Sau reset, không auto-login; gửi thông báo đổi password sau commit, không kèm
password. Email thông báo lỗi không rollback password đã đổi, không tạo queue
hoặc retry tự động. Mọi outstanding RESET_PASSWORD challenge mất hiệu lực.

## 5. OTP, giới hạn và transaction

| Control | Giá trị đích |
| --- | --- |
| Thời hạn OTP REGISTER và RESET_PASSWORD | 600 giây từ generation mới; không kéo dài khi verify sai |
| Verify attempts | Tối đa 5 lần sai/generation; thêm budget 5 lần sai/15 phút/email/purpose, không reset khi resend; đúng mã trước ngưỡng vẫn cần còn budget |
| Resend | Chờ đủ 60 giây kể từ lần request gửi trước; tối đa 3 lần gửi/15 phút/email/purpose, tính cả initial, failed, synthetic và resend |
| Nguồn gửi | 10 request issue/resend trong 15 phút/IP đã xác minh; không tin raw X-Forwarded-For từ client |
| Nguồn verify | 30 request verify/reset trong 15 phút/IP đã xác minh, kể cả sai session, expired hoặc challenge không tồn tại |
| Ngân sách phát mã | 90 request issue/resend được nhận/ngày UTC toàn app, chung hai domain, tính cả synthetic/failed; 429 khi hết budget. Đây là upper bound provider attempt, policy app không phải cam kết quota nhà cung cấp |
| Login / link password | Giữ 5 lần thất bại/15 phút/định danh canonical của SF-105; giữ chỗ nguyên tử trước BCrypt, clear sau thành công; email case/space/internal username không tạo alias né limit |

OTP counters và mail budget nằm trong DB, không mất khi restart. Login limiter
legacy vẫn in-memory một process, giới hạn cũ được ghi nhận; không đổi sang
Redis hoặc Spring Session JDBC trong scope này. IP lấy qua Caddy/trusted proxy
chain được pin; origin private, header spoof test bắt buộc. Khi chưa cấu hình
được chain tin cậy, dùng IP peer (quota có thể chia sẻ), không tin header tùy ý.
Các cửa sổ15 phút là fixed window tính từ request đầu sau expiry, không phải
rolling window. Mọi quota slot đã giữ không hoàn khi gửi thất bại/synthetic;
request đã bị429 trước reserve không làm tăng các counter khác một phần.

1. Lock/UPSERT các rate bucket theo thứ tự cố định, kiểm/giữ quota kể cả
   synthetic trước side effect; không dựa vào Java `check then increment`.
2. Lock challenge slot `(email, purpose)`; generation mới có UUID và OTP mới,
   session binding mới, `PENDING_SEND`; commit. Pending REGISTER thật lưu BCrypt
   hash và display name của request này, không user active. Resend sao chép
   pending payload của generation cũ còn hạn, không nhận password mới qua
   resend; FAILED vẫn giữ payload để thử lại. CONSUMED/expired/sai session hoặc
   UUID không còn là generation hiện tại trả400 chung, phải khởi động flow mới.
3. Gọi Resend ngoài DB transaction/lock, tổng timeout 3 giây, không retry mù;
   idempotency key là challenge UUID. Gửi text và HTML với sender đã cấu hình.
4. Compare-and-set đúng generation từ PENDING_SEND sang ISSUED khi API nhận;
   timeout/4xx/5xx chuyển FAILED. Generation bị supersede trong lúc HTTP chạy
   không được kích hoạt lại; thư đến muộn có thể chứa mã không còn dùng được.
5. Vẫn trả 202 như synthetic khi delivery lỗi theo từng recipient/account;
   không rò enumeration qua 503 riêng cho account có thật. Áp dụng floor 4 giây
   cho request issue/resend hợp lệ (cả synthetic) với timeout gửi trên; test
   không có fast path bỏ qua hash/quota/floor. Không cam kết constant time mạng.
6. Verify lấy đúng challenge/session/purpose, trạng thái ISSUED và `now <
   expires_at`; compare HMAC constant-time. Sai mã tăng attempts + email fail
   budget trong transaction rồi trả lỗi **sau commit**, không rollback counters.
   Đạt lần sai thứ 5 trả 429, các lần sai trước trả 400. Trường hợp replay/sai
   session/expired trả 400 chung; request rác vẫn chịu IP limit. Slot hết hạn
   chuyển EXPIRED và clear verifier/pending payload khi được xử lý.
7. Đúng OTP: REGISTER lock unique email rồi insert user/consume atomically;
   RESET lock user/challenge theo thứ tự chung đã chọn, check active/local hash
   rồi update/consume/increment version atomically. Chỉ một concurrent request
   thắng; replay không sửa user. Collision user được tạo từ luồng khác trả 400
   chung, không gắn pending password vào account đó.

Lock order chung cho verify/reset/signup/Google: rate buckets (sort key),
PostgreSQL transaction advisory lock theo email chuẩn hóa, user, challenge.
Email lock dùng `pg_advisory_xact_lock(hashtextextended('shopflow.email:' ||
normalized_email, 0))` để serialize cả khi chưa có user; hash collision chỉ làm
serialize thêm, không được dùng làm equality/identity. Không giữ advisory lock
qua HTTP Resend. Bước đọc
challenge ban đầu để tìm email không giữ lock; re-read dưới transaction sau khi
lấy lock identity. Vẫn có DB unique email; insert race thua unique phải reload và xử lý collision,
không retry ghi mù hoặc trả 500. Rehearsal concurrency trên PostgreSQL.
Xóa pending credential/verifier ngay khi consume; FAILED giữ pending signup
payload tới resend hoặc expiry, verifier bị xóa. Payload expired được clear
khi slot được truy cập; sau24 giờ đủ điều kiện cleanup opportunistic, không
cam kết xóa đúng hạn khi không có traffic. Không tạo
user từ dữ liệu expired. PENDING_SEND trong deadline gửi không nhận resend
đồng thời: trả429 với Retry-After dương theo resend_at/deadline. PENDING_SEND
còn sót sau deadline3 giây (process chết) được coi FAILED khi truy cập; vẫn
phải đủ cooldown60 giây mới resend. Không tạo thêm HTTP send khi chưa đủ cooldown.

Verifier là HMAC-SHA-256 bằng **OTP_HMAC_SECRET** riêng (ít nhất 32 byte ngẫu
nhiên, base64), nội dung gồm challenge UUID, email chuẩn hóa, purpose và OTP;
DB không chứa OTP plaintext hoặc hash trần 6 số dễ dò offline. Không dùng lại
Resend/Google secret. Secret mới chỉ là yêu cầu cấu hình cho SF-142/143; **chưa
được tạo trong SF-141**. Xoay secret làm challenge đang chờ mất hiệu lực, không
đổi password hay account. Không ghi `.env`/secret value vào tài liệu.

## 6. Google OIDC và liên kết tài khoản

Dùng OAuth2 Client/OIDC của Spring Security theo BOM Boot 4.0.6; xác minh ID
token bằng thư viện (signature/JWKS, issuer, audience/client ID, expiry, nonce).
State entropy tối thiểu 128 bit, one-time và gắn session; timeout flow 5 phút;
PKCE S256 do client tạo/kiểm, callback authorization code chỉ đổi một lần.
Scopes chỉ `openid email profile`, không Gmail/Drive, offline refresh hoặc
persist access/refresh token. Client Secret chỉ ở backend.

| Endpoint | Contract |
| --- | --- |
| `GET /oauth2/authorization/google?returnTo=/customer%3Fcheckout%3D1` | Public, validate returnTo, tạo state/nonce/PKCE/server session; 302 tới Google |
| `GET /login/oauth2/code/google?code=...&state=...` | Public protocol callback; validate flow/token, xử lý bảng dưới; không coi có `code` là login thành công |
| `POST /auth/google/link` body `{ "password": "..." }` | Public + CSRF + pending link proof trong session; verify password **của account mà proof trỏ tới**, không nhận email/user ID từ body; 200 AuthenticatedUser + Location returnTo đã lưu, consume proof/link atomically và rotate session |

| Kết quả sau callback hợp lệ | Xử lý |
| --- | --- |
| Đã có `(issuer, sub)` | Lookup user cũ, phải active; login cùng userId, không đổi role/email/order theo claims. Subject là identity, không dùng email làm khóa |
| Subject mới, email verified chưa có user | Tạo active Google-only CUSTOMER với username kỹ thuật và hash null; link subject atomically. Nếu có pending REGISTER cùng email, vô hiệu challenge và xóa pending hash trong cùng transaction |
| Subject mới, email trùng local account active | Không merge/login. Giữ pending link proof server-side tối đa 5 phút: subject + email + target userId + auth_version + returnTo, gắn session. 303 về `/login?google=link_required`; UI nhập password tài khoản cũ, gọi POST link |
| Email trùng account inactive hoặc Google-only mang subject khác | Từ chối chung; không link, không thay password/active/role |
| Thiếu/false email_verified, email không hợp lệ hoặc token/state/nonce sai | Từ chối; không tạo user/link/session authenticated |

Với returning subject, email claim đổi không đổi email lưu trong ShopFlow;
đổi email account là ngoài scope. Link proof chỉ tạo sau callback OIDC hợp lệ,
không từ email client gửi. Không cấp session login cho pending link; không dùng
forgot OTP để tự gắn password vào Google-only. Password link chịu rate limit
login; sai password trả 401 chung, không mất giỏ; proof hết hạn trả 400 chung.
UI `/login` được phép hiện link form trong đúng flow này, không để guard tự
đẩy đi vì một session khác đã đăng nhập. Link thành công là account switch:
xóa PII/order/payment cache người trước rồi restore session + XSRF mới.

Trước khi ghi link, lock user, recheck active/auth_version/email khớp proof;
reset password giữa callback và link làm proof cũ vô hiệu. Unique issuer/sub
và tối đa một Google identity/user; hai callback không tạo hai user. Chỉ giữ
pending claims cần thiết server-side, không lưu token Google. Callback login
thành công 303 tới returnTo đã lưu; link form giữ returnTo server-side.
Lỗi callback/cancel/timeout 303 `/login?google=failed`, thông báo chung;
invalid returnTo đầu vào trả 400 trước redirect. Không đặt provider error/token
vào URL frontend; không tự gửi lại request tạo order sau login.
Giữ riêng intent returnTo an toàn trong server session tối đa5 phút sau cancel/
link-required, để login/link trả Location mà không cần lộ proof ra browser.
Nếu state/session đã mất thì fallback `/customer`, giỏ persisted vẫn còn để
khách bấm checkout lại; không tuyên bố khôi phục intent khi không còn proof.

### Hai domain và proxy

Hai redirect URI production phải khớp chính xác:

```text
https://shopflow.hoangtuan.dev/api/login/oauth2/code/google
https://shopflow.phamthuy.dev/api/login/oauth2/code/google
```

Caddy hiện strip `/api` bằng `handle_path`: callback backend là
`/login/oauth2/code/google`. Dùng **map allowlist cố định** external Host → public
origin, append `/api/login/oauth2/code/google`; không suy ra callback từ Host/
forwarded headers tùy ý, không đặt một callback cố định cho cả hai domain.
Public start URL cũng có `/api`; flow luôn quay lại origin khởi đầu. Caddy phải
overwrite forwarded headers không tin cậy; scheme production cố định HTTPS.
SF-146 phải chứng minh callback vẫn mang session/state với SameSite=Lax qua
proxy thật; localhost dev đăng ký URI riêng, không thêm vào production allowlist.

## 7. Vô hiệu session và dữ liệu browser

Principal chứa stable `userId` và `auth_version` nội bộ, không expose version
trong JSON. Với mỗi authenticated request (gồm `/auth/session`, ops, order,
payment), kiểm DB user active + version; mismatch invalidate session và xử lý
như guest. GET session trả 200 anonymous, endpoint protected trả 401. Password
reset tăng version trong transaction; request được authorize **sau** reset
commit không dùng session cũ. Không hứa hủy request đã authorize trước commit.
Không clear counter login chỉ vì có ai request forgot, không khóa account vĩnh viễn.

Giỏ chưa đặt lưu ID + quantity bằng Pinia/browser storage trong từng origin,
không lưu password/OTP/link proof/shipping PII/orderRef. Rehydrate từng sản phẩm
bằng API hiện có; không tin giá/tồn kho từ storage. JSON sai/storage unavailable
fallback RAM, không crash; số nguyên ID/quantity phải hợp lệ theo order contract,
không cắt/ép số. Persisted JSON tối đa 64 KiB; vượt giới hạn thì giữ giỏ RAM và
báo không thể lưu qua reload/OAuth, không âm thầm cắt item. Đây là giới hạn
storage client, không thêm giới hạn số item vào API order đang có.

Guest tới checkout → lưu giỏ → `/login?redirect=/customer%3Fcheckout%3D1`.
Signup/verify xong quay login (giữ intent); password hoặc Google/link thành công
về checkout, **không auto-submit**. Reload, cancel, auth error hoặc 401 giữa form
giữ giỏ, clear password/OTP và dữ liệu phiên riêng tư. Logout/account switch giữ
giỏ chưa đặt nhưng xóa checkout PII, created order/orderRef/payment state và
query cache của account cũ. Tạo order/payment timeout không retry POST mù.

## 8. Đặt hàng, payment và quyền

| Endpoint | Guest | CUSTOMER / WAREHOUSE / SHOP_OWNER, gồm demo |
| --- | --- | --- |
| Catalog/detail, giỏ chưa đặt, chat theo quota cũ | Được | Được |
| `POST /orders` | 401 | Được; server gán `account_user_id = principal.userId` |
| `POST /orders/{orderRef}/payments` | 401 | Chỉ khi owner là chính userId đó |
| Endpoint vận hành | Giữ 401 | Giữ ma trận role cũ; không cấp CUSTOMER quyền vận hành |

Order request/response không thêm owner field; từ chối body cố gán
`accountUserId`/`userId`/`role`. `customer.email` vẫn là snapshot tùy chọn và có
thể khác email account; không quyết định ownership. `customer_id` vẫn trỏ
`customers`, không ghi user ID vào cột này. Order mới gắn account trong cùng
transaction snapshot/reserve; user bị vô hiệu/không tồn tại khi ghi thì rollback.

Payment giữ `orderRef` opaque; SELECT FOR UPDATE lấy owner cùng status/method/
amount. Sau structural validation, **kiểm owner trước** conflict/status/payment
lookup và trước mọi write/release. Wrong-owner, reference không tồn tại/sai
format hoặc legacy owner null đều 404 `Order not found`, kể cả với SHOP_OWNER.
Đúng owner nhưng sai trạng thái/duplicate payment giữ 409; malformed body giữ
400; unauthorized/ownership rejection không tạo payment/movement/đổi reserved.
Không thêm payment retry/refund/cancel hoặc API claim legacy order.

Mọi legacy order giữ null owner, snapshot/payment/reservation không đổi.
Storefront payment cho legacy bị chặn; trước rollout thống kê PENDING_PAYMENT
owner null và báo owner về reservation còn giữ. Không backfill theo email,
không tự hủy/expire/release. Inventory lock order và JPA/JDBC transaction hiện
có phải giữ; request đặt hàng không login bị chặn trước lock/reserve.

## 9. Cấu hình và checklist triển khai

| Env backend | Mục đích |
| --- | --- |
| `RESEND_API_KEY` | Quyền gửi qua API Resend, không phải SMTP password |
| `MAIL_FROM` | `ShopFlow <shopflow@hoangtuan.dev>`; domain sender phải được Resend xác minh |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | OAuth Web application; same client hỗ trợ cả hai redirect URI |
| `OTP_HMAC_SECRET` | Verifier OTP riêng, yêu cầu mới chưa được cấp ở task tài liệu |

Workspace và production `.env` riêng, mode600; Compose phải forward rõ các
biến vào backend khi triển khai, không copy đè DB/R2/Qdrant config. Google
support/contact là `hoangchieunguyentuan@gmail.com`; đó không phải sender OTP.
Thử key/secret API sơ bộ trước đây không thay thế đăng nhập Google hoàn chỉnh,
gửi Inbox, wiring container hay integration trong ứng dụng. CI dùng fake mail/
OIDC, không cần key thật. Thiếu config mail chỉ chặn mail endpoints, không làm
catalog/demo password login chết; thiếu config Google disable riêng start/link,
không fallback sang provider/client khác.

Migration chỉ thêm sau V6 theo HEAD thực tế, không sửa V1–V6/Flyway repair.
Rehearsal trên PostgreSQL dùng một lần có dữ liệu baseline, validate checksum,
counts/FK/demo/snapshot/reserve trước/sau; backup có thể restore trước cutover.
Rollback dùng bản code hiểu schema mới và vẫn bảo vệ owner: **không rollback
về bản permitAll payment**, không drop schema/volume để bỏ migration. Khi chưa
có bản rollback tương thích thì maintenance chặn order/payment, giữ dữ liệu,
fix-forward có owner approval. SF-104/132/136/139 không được coi là hoàn tất.

## 10. Ma trận kiểm chứng cần thực hiện ở SF-142–SF-149

SF-141 chỉ kiểm tính nhất quán tài liệu; bảng này là test plan, **chưa có PASS
runtime**. Không regenerate `api-contract.json` ở task docs; khi implement chỉ
review diff routes/schema được mô tả ở đây.

| ID | Given / When | Then / bằng chứng cần có |
| --- | --- | --- |
| CI-01 | Signup valid; chưa verify; sau đó OTP đúng | Chưa có active user/login; verify 204 tạo đúng một CUSTOMER; login mới được |
| CI-02 | Email case/space variants; concurrent signup và Google | Một account/email; không gắn pending password vào Google-only hoặc account cũ |
| CI-03 | Password 7/8 ký tự, thiếu từng nhóm, Unicode 72/73 byte | Validation giống backend/frontend; không truncate; demo login không bị policy mới chặn |
| CI-04 | OTP bắt đầu0; clock 9m59s và đúng10m | Trước expiry được, tại/sau expiry 400; không dựa clock browser |
| CI-05 | Wrong purpose/session/code, 5 sai, resend, replay, concurrent reset | Counters commit, limit429; generation cũ không sống lại; đúng một update/consume |
| CI-06 | Unknown/inactive/Google-only email và provider timeout/4xx/5xx | 202 cùng shape/floor; không gửi cho synthetic, không lộ account qua status; quota không reset khi restart |
| CI-07 | Two old sessions; reset thành công | Password cũ sai; hai session cũ trở thành anonymous/401 sau commit; password mới login được |
| CI-08 | Valid/invalid OIDC state, nonce, PKCE, signature, issuer, audience, expiry | Chỉ hợp lệ tạo principal nội bộ/session; fake provider cho negative CI |
| CI-09 | Google local collision; wrong password; reset giữa callback/link | Không auto-merge; proof ràng buộc target/session/version; chỉ proof đúng được link |
| CI-10 | Google thật trên từng domain: login → GET session → write → logout | Cookie Lax/Secure/HttpOnly, callback đúng `/api`, CSRF write đầu thành công; không dùng mock làm bằng chứng live |
| CI-11 | Guest thêm giỏ → signup/password/Google/link/cancel/reload/401 | Cart quantity giữ trong origin, không auto-submit; switch account xóa PII/orderRef; storage hỏng vẫn dùng được |
| CI-12 | A tạo order; B hoặc guest pay, kể cả B là owner/kho/demo | owner từ principal; rejection không đổi DB/reservation; A vẫn payment đúng invariant |
| CI-13 | Legacy null owner, forged owner JSON, duplicate/concurrent payment | Legacy404 và giữ dữ liệu; owner field400; đúng owner conflict409; không double release |
| CI-14 | Fresh browser GET session, echo cookie token thô; bỏ/sửa token | Bootstrap cookie rồi write thành công; thiếu/sai403 trước side effect; test không `.with(csrf())` |
| CI-15 | Baseline V1–V6 DB → migration mới; rehearse rollback tương thích | Checksum cũ/counts/snapshot/demo đúng, owner FK đúng; không mở lại guest payment |

Lệnh dự kiến: `bash backend/mvnw -f backend/pom.xml -B spotless:check clean verify`,
`npm --prefix frontend run lint:ci`, `npm --prefix frontend run build`,
`CI=1 npm --prefix frontend run test:e2e`; thêm focused PostgreSQL/concurrency/
provider/cookie integration. Ghi lần chạy mới, flaky/skipped và môi trường thật.

## 11. Nguồn thiết kế

- [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html): code flow và proxy/redirect; chọn dependency theo BOM repo, không nâng version chỉ vì trang docs mới.
- [Google OIDC](https://developers.google.com/identity/openid-connect/openid-connect): claim validation và stable subject.
- [Resend send email](https://resend.com/docs/api-reference/emails/send-email): HTTPS, sender, idempotency; quota app ở mục 5 là quyết định kỹ thuật của ShopFlow.
- [OWASP recovery](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html): one-time code, anti-enumeration, reset/session invalidation.
- [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): BCrypt byte limit. Policy 8 ký tự + composition là quyết định chủ repo, không trình bày như khuyến nghị tối ưu của OWASP.

## 12. Lịch sử

| Version | Date | Thay đổi |
| --- | --- | --- |
| 1.0 | 04/11/2026 | Contract SF-141 theo quyết định owner; chuẩn bị triển khai, chưa có runtime change |

## Account self-service extension (SF-150)

Owner đã duyệt [contract self-service](api-account-self-service-spec.md) sau v0.13.0: menu/logout, một địa chỉ mặc định, đổi local password và thiết lập password Google-only qua authenticated SET_PASSWORD. Các giới hạn ngoài phạm vi ở mục1 là lịch sử của đợt trước; public forgot/reset và ownership cũ không thay đổi. SF-151–SF-156 đã được triển khai trên nhánh tính năng; chưa deploy production.

Bằng chứng account self-service: [QA SF-156](qa-account-self-service.md). V8 bổ sung SET_PASSWORD + issued_auth_version; V9 lưu một địa chỉ riêng theo user_id, không backfill đơn cũ.
