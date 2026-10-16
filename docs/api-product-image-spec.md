# ShopFlow — Product Image API Specification

**Phiên bản:** 1.0
**Ngày cập nhật:** 16/10/2026
**Trạng thái:** Backend/frontend triển khai ở SF-90/SF-91; bằng chứng kiểm tra ở SF-92.

**Liên quan:** [SF-74](https://tuanwork.atlassian.net/browse/SF-74),
[SF-89](https://tuanwork.atlassian.net/browse/SF-89),
[SRS](./SRS.md), [Catalog API](./api-product-catalog-spec.md),
[phân quyền](./api-authorization-spec.md), [QA ảnh sản phẩm](./qa-product-image.md).

## 1. Phạm vi và quyết định

Mỗi product có tối đa một ảnh. Chỉ chủ shop tải lên hoặc thay ảnh; customer và
warehouse không được ghi. Đây không phải chức năng tạo/sửa/xoá product.

Upload qua backend để kiểm tra byte thật trước khi ghi R2, dùng lại session và
CSRF cùng origin. Presigned PUT chưa cần thiết vì sẽ thêm CORS và bước xác nhận
upload; xem lại khi cần upload hàng loạt hoặc file lớn.

Ảnh được đọc trực tiếp qua custom domain của bucket R2 dành riêng cho ảnh.
Public access áp dụng toàn bucket; không để backup hoặc dữ liệu private trong
bucket này. Không thêm proxy đọc ảnh hay pipeline CDN riêng.

## 2. POST /products/{id}/image

URL backend local; qua frontend production có prefix `/api`.

- Quyền: `SHOP_OWNER`; anonymous `401`, `CUSTOMER`/`WAREHOUSE` `403`.
- Session cookie và CSRF như [Authentication API](./api-authentication-spec.md).
- Body `multipart/form-data`, đúng một part tên `file`, không rỗng.
- `id` phải trỏ tới product active; missing/inactive trả `404`.
- Chỉ JPEG/PNG, tối đa **2 MiB = 2.097.152 byte**, bao gồm giá trị biên.
- Không tin filename hay MIME client gửi. Nhận diện PNG qua
  `89 50 4E 47 0D 0A 1A 0A`; JPEG qua `FF D8 FF`, gồm APP2/ICC.
- Không dùng `URLConnection.guessContentTypeFromStream`: có thể loại nhầm JPEG
  APP2 hoặc thay đổi kết quả theo khả năng mark/reset của stream.
- Header chưa đủ: dùng ImageIO đọc metadata và decode để từ chối ảnh hỏng.
  Tối đa 16 triệu pixel để không giải mã ảnh có kích thước bộ nhớ quá lớn;
  vượt giới hạn này trả `415`. Không resize hay thay đổi byte ảnh được lưu.
- Thiếu/rỗng/nhiều part `file` trả `400`; JSON không phải multipart trả `415`.
- Mọi validation và kiểm tra product hoàn tất trước khi gọi R2.

Response `200 OK`:

```json
{
  "imageUrl": "https://media.example.com/products/42/7ff061ad-7754-4a28-95d6-188654244d42.png"
}
```

Tên object sinh phía server, không dùng filename client:
`products/{id}/{uuid}.{png|jpg}`. Lưu object với Content-Type được xác định từ
byte; database chỉ lưu key trong `products.image_key VARCHAR(255)` nullable.
URL ghép `R2_PUBLIC_BASE_URL` và key khi đọc; không lưu URL đầy đủ.

## 3. Thay ảnh và lỗi giữa storage/database

R2 và PostgreSQL không chung transaction. Trong transaction, khoá dòng product
để hai lần thay ảnh không mất thứ tự. Upload key mới, lưu key mới vào database.
Chỉ sau commit thành công mới xoá key cũ. Không xoá ảnh cũ trước commit.

Nếu upload thất bại, database giữ key cũ. Nếu ghi/commit database thất bại sau
upload, rollback key và thử xoá object mới. Xoá bù hoặc xoá ảnh cũ thất bại chỉ
log lỗi, không làm mất ảnh đang được tham chiếu. Không xây retry/job dọn rác;
object mồ côi có thể cần dọn thủ công sau lỗi storage.

## 4. Lỗi

Lỗi nghiệp vụ dùng `{message, status}`; lỗi security do Spring Security xử lý.

| Status | Trường hợp | Message |
| --- | --- | --- |
| 400 | Thiếu/rỗng/nhiều file | `Exactly one non-empty image is required` |
| 401 | Chưa đăng nhập | Theo security |
| 403 | Sai vai trò; phiên đã đăng nhập thiếu hoặc sai CSRF | Theo security |
| 404 | Product missing/inactive | `Product not found` |
| 413 | File quá 2 MiB hoặc request vượt giới hạn multipart | `Image exceeds 2 MiB limit` |
| 415 | Sai định dạng, ảnh hỏng hoặc quá 16 triệu pixel | `Only valid JPEG or PNG images up to 16 megapixels are supported` |
| 503 | R2 chưa cấu hình trong dev hoặc upload R2 thất bại | `Image storage unavailable` |

Giới hạn request multipart là 3 MiB để có chỗ cho metadata; file vẫn kiểm đúng
2 MiB. Handler `413` không giới hạn theo controller vì parser có thể từ chối
request trước khi chọn handler. Lỗi commit database không giả làm lỗi client;
rollback và cleanup như mục 3, không lộ credential hoặc SDK error ra response.

## 5. Catalog và cấu hình

`GET /products` và `GET /products/{id}` thêm `imageUrl: string | null`.
Không có ảnh trả `null`; frontend giữ placeholder hiện có khi null hoặc tải lỗi.
Các field/stockStatus và luồng checkout giữ nguyên.

Năm biến môi trường: `R2_ENDPOINT`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`,
`R2_BUCKET`, `R2_PUBLIC_BASE_URL`. Region SDK cố định `auto`; client dùng
S3-compatible API, path style và tắt chunked encoding. Credential chỉ ở backend.
Staging bắt buộc đủ cấu hình; dev/test không yêu cầu credential thật để khởi động.
CI dùng client mock, không gọi R2. Token chỉ có Object Read & Write ở bucket ảnh.

## 6. Kiểm chứng và ngoài phạm vi

SF-90 có backend test cho quyền/CSRF, mọi lỗi ở mục 4, size boundary, JPEG ICC,
null imageUrl, upload/thay ảnh và rollback cleanup. SF-91 có browser test cho
upload, lỗi, cache, fallback và điều hướng. SF-92 tổng hợp QA và smoke R2 thật
trên sản phẩm thử riêng, không đổi ảnh sản phẩm đang dùng.

Không gồm gallery, crop/resize/thumbnail, endpoint xoá ảnh, product CRUD,
import Shopee, backup database hoặc release/deploy production.

## 7. Tham khảo

- [Cloudflare R2 Java SDK](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/)
- [R2 public buckets](https://developers.cloudflare.com/r2/buckets/public-buckets/)
