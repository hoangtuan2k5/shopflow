# ShopFlow — Account Self-Service Contract (SF-150)

**Trạng thái:** đã triển khai SF-151–SF-155 trên nhánh tính năng; kiểm chứng và giới hạn rollout trong [QA SF-156](qa-account-self-service.md). Chưa triển khai production.

**Baseline:** v0.13.0, main `ebb5a0d`. Mở rộng có chủ đích giới hạn profile/Google-only password trong [contract cũ](api-customer-identity-spec.md); không thay public forgot-password hay sửa đơn cũ.

## Quyết định đã được owner duyệt

1. Mỗi account có một địa chỉ giao hàng mặc định, có thể lưu/sửa/xóa. Chưa làm sổ nhiều địa chỉ. Owner đã duyệt phương án này trước implementation.
2. Menu gồm tên tài khoản; Thông tin giao hàng; Đổi mật khẩu nếu có local password hoặc Thiết lập mật khẩu nếu Google-only; Đăng xuất. Giữ lối vào khu vực làm việc cho staff/owner.
3. Dùng tên/email đang có để hiển thị; không thêm đổi email, chỉnh tên hay lịch sử đơn hàng vào scope.
4. Tài khoản email đã xác minh được quản lý thông tin riêng. Account username-only dùng chung (3 demo hiện có) không đổi credential hay lưu địa chỉ cá nhân qua self-service; vẫn login, vận hành và checkout nhập tay như cũ. Capabilities do backend quyết định, không hardcode username ở frontend.
5. Checkout tự điền dữ liệu đã lưu; sửa tại checkout chỉ áp dụng cho đơn đang đặt. Không tự cập nhật profile, không sửa snapshot của đơn cũ, không tự submit/retry khi quay lại từ đăng nhập.
6. Đổi hoặc thiết lập mật khẩu thành công thì thu hồi mọi session cũ, gồm session hiện tại, yêu cầu đăng nhập lại; giữ giỏ ID/quantity nhưng xóa PII/private cache.
7. Hai domain dùng chung account và profile server; session/cookie/giỏ vẫn riêng từng origin. Không thêm SSO, Google scopes, dịch vụ mail hay biến môi trường mới.
8. Đề xuất phiên bản tính năng tiếp theo: v0.14.0. Không thay tag/release v0.13.0 đã ký.

## API và ranh giới bảo mật

Backend mappings không có `/api`; frontend gọi qua `/api` của Caddy.
Chọn prefix `/auth/account` để tái sử dụng Origin allowlist, `Cache-Control: no-store` và JSON forbidden hiện có. Tất cả endpoint mới bắt buộc authenticated; không đưa vào permitAll. Mọi write dùng CSRF thật. `GET /auth/session` và shape SessionResponse hiện có giữ nguyên.

| Method / endpoint backend | Request | Kết quả chính |
| --- | --- | --- |
| GET /auth/account | Không body | 200: displayName, email của chính account, capabilities; không hash/token/Google sub |
| GET /auth/account/delivery | Không body | 200 `{delivery:null}` hoặc delivery đã lưu |
| PUT /auth/account/delivery | receiverName, phone, addressLine, district?, city | 200 delivery sau upsert theo current principal |
| DELETE /auth/account/delivery | Không body | 204; không tác động đơn đã có |
| POST /auth/account/password/change | currentPassword, newPassword | 204 khi thành công; form xác nhận mật khẩu kiểm phía frontend |
| POST /auth/account/password/setup | Không nhận email/userId | 202 ChallengeResponse khi OTP được provider chấp nhận |
| POST /auth/account/password/setup/resend | challengeId | 202 generation mới, chỉ cùng account/session/purpose |
| POST /auth/account/password/setup/confirm | challengeId, otp, newPassword | 204 sau consume proof + đặt hash + tăng auth_version |
| POST /auth/logout (đã có) | Không body | Tái sử dụng, 204 idempotent; không làm logout giả chỉ bằng xóa client state |

Ví dụ self resource:
```json
{
  "displayName": "Khách hàng",
  "email": "buyer@example.com",
  "capabilities": {
    "manageDelivery": true,
    "changePassword": false,
    "setPassword": true
  }
}
```

- User ID lấy từ AccountPrincipal và kiểm active/auth_version phía server; từ chối unknown owner/email fields, không chọn account qua body/query. UI capability không thay authorization backend.
- Guest với CSRF hợp lệ:401; thiếu/sai CSRF hoặc Origin không cho phép:403; capability bị cấm/demo:403. Payload sai hoặc proof sai/hết hạn/replay:400. Stale setup/account đã có password:409. Rate limit:429 và Retry-After. Giữ shape message/status/fieldErrors, không mở error envelope toàn hệ thống.
- Không cho một account đọc/sửa thông tin của account khác, kể cả role SHOP_OWNER. Các quyền vận hành cũ không đổi.
- Các route frontend `/account/delivery` và `/account/password` phải có requiresAuth thực sự; không dựa vào meta.role=customer vì route customer hiện mở cho guest.
- Chọn purpose SET_PASSWORD riêng. Public `/auth/otp/resend`, `/auth/password/forgot`, `/auth/password/reset` không được trở thành đường vòng cho SET_PASSWORD; generic resend phải từ chối purpose này.
- Với private setup, có thể báo503 khi gửi mail thất bại, mark proof FAILED và không hồi sinh code; public forgot/registration vẫn giữ response chống dò account theo contract cũ.
- Gửi thư theo Accept-Language hiện tại, cùng Resend client/sender/banner; quotas bao phủ request mới và notice best-effort. Không retry gửi vô hạn, không lưu/log OTP, password, token hay raw provider response.

## Schema và transaction

### Địa chỉ mặc định

Bảng `account_delivery_details`: `user_id` PRIMARY KEY/FK users, receiver_name varchar255, phone varchar20, address_line varchar500, district varchar100 nullable, city varchar100, created_at/updated_at timestamptz. FK giữ nhất quán theo chính sách hiện có; không thêm endpoint xóa account.

Validation đi theo shipping DTO hiện có: trim ngoài dữ liệu địa chỉ, bắt buộc receiverName/phone/addressLine/city, giới hạn độ dài nhất quán. Không tự thêm dịch vụ tra địa giới hoặc chuẩn hóa số điện thoại ngoài yêu cầu.

Upsert/delete theo principal, kiểm account còn active và version hợp lệ ở transaction; unique user_id bảo đảm tối đa1 profile. Profile không được tham chiếu động từ đơn: orders vẫn giữ shipping snapshot như hiện tại.

### SET_PASSWORD

- Thêm migration sau V7, không sửa migration đã áp dụng; số V8/V9 cụ thể theo thứ tự đã chốt khi implement, tránh trùng version.
- Mở rộng CHECK purpose/target/payload cho SET_PASSWORD trong auth_email_challenges; giữ mọi invariant REGISTER/RESET_PASSWORD. Đọc mapper/cleanup/rate/consume cũ để không làm generic resend hoặc reset hiểu nhầm purpose mới.
- Thêm `issued_auth_version` nullable cho challenge cũ, bắt buộc khi SET_PASSWORD. Bind user_id, verified email, session, generation và phiên bản credential; HMAC verifier không plaintext.
- Google-only setup bắt buộc account active, verified email, Google identity hợp lệ và password_hash vẫn null tại lúc consume. Account đã có password không dùng setup để bỏ qua current password.
- Đổi/thiết lập password và consume/invalidate proof + tăng auth_version phải atomic. Giữ Google issuer/sub, role và account ID; không tạo user thứ hai.
- Đổi mật khẩu hiện tại phải xác minh currentPassword và giới hạn attempts trước khi cập nhật. Serialize với reset/link/setup theo lock order hiện có; không làm thay đổi stock/order transactions.
- Khi credential đổi, hủy proof RESET_PASSWORD/SET_PASSWORD còn chờ của account để không dùng mã cũ ghi đè password mới. Notice gửi sau commit, lỗi notice không rollback credential.
- Password policy giữ8 Unicode code point, đủ hoa/thường/số/ký hiệu và tối đa72 byte UTF-8; không trim/normalize/cắt thầm.
- OTP giữ6 số,10 phút,5 lần sai, resend60 giây, purpose/session/version binding và quotas hiện có; gửi lại không reset vô hạn failure budget.

## UI và quyền riêng tư

- Reuse Reka UI/component hiện có cho menu, dialog/form, button và validation; không cài UI/auth/mail dependency mới.
- Menu có loading/error state, Escape/click ngoài, keyboard/focus, bilingual copy; nhãn tên vẫn24 grapheme và full accessible title.
- Logout chỉ xác nhận thành công sau response server; khi lỗi mạng không tạo cảm giác đã vô hiệu server session. Xóa private query/form state khi session mất/đổi account; xử lý idempotent retry phù hợp.
- Profile/password forms có state đang lưu, validation, lỗi server/rate limit và xóa input mật khẩu/OTP sau hoàn tất hoặc mất phiên.
- Profile chỉ nằm server và query cache RAM theo account; không localStorage/sessionStorage/URL chứa địa chỉ, password hoặc proof. Cart storage vẫn chỉ productId/quantity.
- Không thêm menu giả trỏ vào màn hình không dùng được trong trạng thái PR hoàn tất. Toàn bộ bundle phải đầy đủ trước khi publish cho người dùng.


## Traceability và kiểm chứng

SF-151: menu/capabilities/logout; SF-155: SET_PASSWORD; SF-154: đổi mật khẩu; SF-152: địa chỉ mặc định; SF-153: prefill/snapshot; SF-156: integration, migration, browser và rollout evidence.

Dùng PostgreSQL thật, roundtrip cookie/CSRF, ma trận account A/B/local/Google-only/linked/demo, stale proof/version/session và concurrent credential updates. Kiểm two-session revocation, no auto-login/no auto-order, no PII storage, địa chỉ profile không đổi snapshot đơn cũ.

Full backend verify và frontend lint/build/E2E; review OpenAPI snapshot chỉ thay đổi có chủ đích; migration/restore trên DB tạm, không sửa V1–V7. Chỉ ghi PASS từ lần kiểm thử của thay đổi mới.
