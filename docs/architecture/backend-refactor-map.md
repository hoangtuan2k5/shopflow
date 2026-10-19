# Bảng chuyển source và quyết định refactor — SF-131

Đối chiếu base develop `0c73a96c4bb56f9c6541aba335a76c05763dbc3f` với source
đợt SF-130. Có 119 file Java main trước thay đổi, gồm 12 `package-info.java`.
Sau thay đổi có 132 file: 107 file có tên cũ được giữ/chuyển, bỏ 12 mô tả package
cũ và thêm 25 file để tách type hoặc truy cập JDBC. Số file không phải số test.

Quyết định và luồng sử dụng nằm ở [kiến trúc backend](backend.md); baseline HTTP,
migration và kết quả test ở [báo cáo QA](../qa-backend-architecture.md).

## Quyết định

| Cách tổ chức | Đánh đổi | Quyết định |
|---|---|---|
| Tầng ở root: controller/service/repository/model/dto | Dễ nhận diện vai trò; một nghiệp vụ trải qua vài tầng | Chọn theo yêu cầu chủ repo |
| Feature trước, tầng bên trong | Gom nghiệp vụ gần nhau nhưng vẫn giữ feature ở root | Không dùng làm đích đợt này |
| Port/adapter và interface cho mọi lớp | Thêm lớp khi chưa có nhiều triển khai | Chưa cần |

Spring MVC ở đây trả JSON cho frontend Vue. Không thêm template engine, thư mục
routes/middleware/utils hoặc interface/Impl chỉ để có đủ tên thư mục. Spring
Security filter chain là cơ chế xử lý xác thực/CSRF; không sao chép JWT từ repo
tham khảo. Mapper không query database.

JPA thực sự dùng `Product`, `Order`, `OrderItem`, `Payment` và ba repository
`ProductRepository`, `OrderRepository`, `PaymentRepository`. JDBC phục vụ locking,
projection và aggregate, tiếp tục dùng cùng DataSource/transaction với JPA.

## Chuyển các file gốc

Đường dẫn tương đối với `backend/src/main/java/dev/hoangtuan/shopflow/`.
Các type tách khỏi file chung/nested và repository mới được liệt kê sau bảng.

| Trước | Sau |
|---|---|
| `ShopflowApplication.java` | `ShopflowApplication.java` |
| `access/AccountPrincipal.java` | `security/AccountPrincipal.java` |
| `access/AccountUserDetailsService.java` | `security/AccountUserDetailsService.java` |
| `access/AuthController.java` | `controller/AuthController.java` |
| `access/AuthErrorHandler.java` | `exception/handler/AuthErrorHandler.java` |
| `access/AuthService.java` | `service/AuthService.java` |
| `access/AuthenticatedUser.java` | `dto/access/AuthenticatedUser.java` |
| `access/LoginRateLimiter.java` | `security/LoginRateLimiter.java` |
| `access/LoginRequest.java` | `dto/access/LoginRequest.java` |
| `access/Role.java` | `model/access/Role.java` |
| `access/SecurityConfig.java` | `config/SecurityConfig.java` |
| `access/SessionResponse.java` | `dto/access/SessionResponse.java` |
| `access/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `catalog/CatalogController.java` | `controller/CatalogController.java` |
| `catalog/CatalogErrorHandler.java` | `exception/handler/CatalogErrorHandler.java` |
| `catalog/CatalogReader.java` | `service/CatalogReader.java` |
| `catalog/Product.java` | `model/catalog/Product.java` |
| `catalog/ProductDto.java` | `dto/catalog/ProductDto.java` |
| `catalog/ProductImageController.java` | `controller/ProductImageController.java` |
| `catalog/ProductImageErrorHandler.java` | `exception/handler/ProductImageErrorHandler.java` |
| `catalog/ProductImageException.java` | `exception/catalog/ProductImageException.java` |
| `catalog/ProductImageService.java` | `service/ProductImageService.java` |
| `catalog/ProductImageStorage.java` | `integration/storage/ProductImageStorage.java` |
| `catalog/ProductNotFoundException.java` | `exception/catalog/ProductNotFoundException.java` |
| `catalog/ProductRepository.java` | `repository/jpa/ProductRepository.java` |
| `catalog/ProductService.java` | `service/ProductService.java` |
| `catalog/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `chat/ChatController.java` | `controller/ChatController.java` |
| `chat/ChatErrorHandler.java` | `exception/handler/ChatErrorHandler.java` |
| `chat/ChatQuotaGuard.java` | `service/ChatQuotaGuard.java` |
| `chat/ChatRequest.java` | `dto/chat/ChatRequest.java` |
| `chat/ChatResponse.java` | `dto/chat/ChatResponse.java` |
| `chat/ChatService.java` | `service/ChatService.java` |
| `chat/ChatUnavailableException.java` | `exception/chat/ChatUnavailableException.java` |
| `chat/GemmaClient.java` | `integration/ai/GemmaClient.java` |
| `chat/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `common/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `config/OpenApiConfig.java` | `config/OpenApiConfig.java` |
| `config/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `customerreturn/CreateReturnRequest.java` | `dto/customerreturn/CreateReturnRequest.java` |
| `customerreturn/ReturnController.java` | `controller/ReturnController.java` |
| `customerreturn/ReturnErrorHandler.java` | `exception/handler/ReturnErrorHandler.java` |
| `customerreturn/ReturnItemRequest.java` | `dto/customerreturn/ReturnItemRequest.java` |
| `customerreturn/ReturnItemResponse.java` | `dto/customerreturn/ReturnItemResponse.java` |
| `customerreturn/ReturnResponse.java` | `dto/customerreturn/ReturnResponse.java` |
| `customerreturn/ReturnService.java` | `service/ReturnService.java` |
| `customerreturn/ReturnStatus.java` | `model/customerreturn/ReturnStatus.java` |
| `customerreturn/ReturnableItemResponse.java` | `dto/customerreturn/ReturnableItemResponse.java` |
| `customerreturn/ReturnableOrderResponse.java` | `dto/customerreturn/ReturnableOrderResponse.java` |
| `customerreturn/UpdateReturnRequest.java` | `dto/customerreturn/UpdateReturnRequest.java` |
| `customerreturn/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `dashboard/DashboardController.java` | `controller/DashboardController.java` |
| `dashboard/DashboardErrorHandler.java` | `exception/handler/DashboardErrorHandler.java` |
| `dashboard/DashboardResponse.java` | `dto/dashboard/DashboardResponse.java` |
| `dashboard/DashboardService.java` | `service/DashboardService.java` |
| `dashboard/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `delivery/DeliveryController.java` | `controller/DeliveryController.java` |
| `delivery/DeliveryErrorHandler.java` | `exception/handler/DeliveryErrorHandler.java` |
| `delivery/DeliveryResponse.java` | `dto/delivery/DeliveryResponse.java` |
| `delivery/DeliveryService.java` | `service/DeliveryService.java` |
| `delivery/UpdateDeliveryRequest.java` | `dto/delivery/UpdateDeliveryRequest.java` |
| `delivery/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `inventory/InventoryConflictException.java` | `exception/inventory/InventoryConflictException.java` |
| `inventory/InventoryController.java` | `controller/InventoryController.java` |
| `inventory/InventoryErrorHandler.java` | `exception/handler/InventoryErrorHandler.java` |
| `inventory/InventoryNotFoundException.java` | `exception/inventory/InventoryNotFoundException.java` |
| `inventory/InventoryResponse.java` | `dto/inventory/InventoryResponse.java` |
| `inventory/InventoryService.java` | `service/InventoryService.java` |
| `inventory/InventoryValidationException.java` | `exception/inventory/InventoryValidationException.java` |
| `inventory/LowStockThresholdRequest.java` | `dto/inventory/LowStockThresholdRequest.java` |
| `inventory/StockAdjustmentRequest.java` | `dto/inventory/StockAdjustmentRequest.java` |
| `inventory/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `order/CreateOrderRequest.java` | `dto/order/CreateOrderRequest.java` |
| `order/CustomerSnapshot.java` | `model/order/CustomerSnapshot.java` |
| `order/DeliveryStatus.java` | `model/order/DeliveryStatus.java` |
| `order/InsufficientStock.java` | `model/inventory/InsufficientStock.java` |
| `order/Order.java` | `model/order/Order.java` |
| `order/OrderController.java` | `controller/OrderController.java` |
| `order/OrderErrorHandler.java` | `exception/handler/OrderErrorHandler.java` |
| `order/OrderItem.java` | `model/order/OrderItem.java` |
| `order/OrderRepository.java` | `repository/jpa/OrderRepository.java` |
| `order/OrderResponse.java` | `dto/order/OrderResponse.java` |
| `order/OrderService.java` | `service/OrderService.java` |
| `order/OrderStatus.java` | `model/order/OrderStatus.java` |
| `order/OrderValidationException.java` | `exception/order/OrderValidationException.java` |
| `order/PaymentMethod.java` | `model/order/PaymentMethod.java` |
| `order/ShippingAddress.java` | `model/order/ShippingAddress.java` |
| `order/StockRequest.java` | `model/inventory/StockRequest.java` |
| `order/StockReservationService.java` | `service/StockReservationService.java` |
| `order/StockValidationResult.java` | `model/inventory/StockValidationResult.java` |
| `order/ValidatedStockItem.java` | `model/inventory/ValidatedStockItem.java` |
| `order/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `payment/CreatePaymentRequest.java` | `dto/payment/CreatePaymentRequest.java` |
| `payment/Payment.java` | `model/payment/Payment.java` |
| `payment/PaymentConflictException.java` | `exception/payment/PaymentConflictException.java` |
| `payment/PaymentController.java` | `controller/PaymentController.java` |
| `payment/PaymentErrorHandler.java` | `exception/handler/PaymentErrorHandler.java` |
| `payment/PaymentNotFoundException.java` | `exception/payment/PaymentNotFoundException.java` |
| `payment/PaymentRepository.java` | `repository/jpa/PaymentRepository.java` |
| `payment/PaymentResponse.java` | `dto/payment/PaymentResponse.java` |
| `payment/PaymentService.java` | `service/PaymentService.java` |
| `payment/PaymentStatus.java` | `model/payment/PaymentStatus.java` |
| `payment/PaymentValidationException.java` | `exception/payment/PaymentValidationException.java` |
| `payment/SimulatedPaymentResult.java` | `model/payment/SimulatedPaymentResult.java` |
| `payment/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `receiving/ReceivingController.java` | `controller/ReceivingController.java` |
| `receiving/ReceivingErrorHandler.java` | `exception/handler/ReceivingErrorHandler.java` |
| `receiving/ReceivingRequest.java` | `dto/receiving/ReceivingRequest.java` |
| `receiving/ReceivingResponse.java` | `dto/receiving/ReceivingResponse.java` |
| `receiving/ReceivingService.java` | `service/ReceivingService.java` |
| `receiving/package-info.java` | Bỏ package-info cũ; trách nhiệm được mô tả trong backend.md |
| `search/CatalogIndexController.java` | `controller/CatalogIndexController.java` |
| `search/CatalogIndexErrorHandler.java` | `exception/handler/CatalogIndexErrorHandler.java` |
| `search/CatalogIndexService.java` | `service/CatalogIndexService.java` |
| `search/CatalogSearch.java` | `service/CatalogSearch.java` |
| `search/GeminiEmbeddingClient.java` | `integration/ai/GeminiEmbeddingClient.java` |
| `search/QdrantClient.java` | `integration/search/QdrantClient.java` |
| `search/SearchConfiguration.java` | `config/SearchConfiguration.java` |
| `search/SearchUnavailableException.java` | `exception/search/SearchUnavailableException.java` |

## File mới do tách trách nhiệm

| File mới | Nguồn / trách nhiệm |
|---|---|
| `config/R2Configuration.java` | Bean configuration trước nằm cùng ProductImageStorage |
| `exception/handler/MultipartSizeErrorHandler.java` | Advice trước nằm cùng ProductImageErrorHandler; giữ scope/status |
| `exception/delivery/DeliveryNotFoundException.java` | Tách type trong DeliveryErrorHandler |
| `exception/delivery/DeliveryConflictException.java` | Tách type trong DeliveryErrorHandler |
| `exception/receiving/ReceivingNotFoundException.java` | Tách type trong ReceivingErrorHandler |
| `exception/receiving/ReceivingConflictException.java` | Tách type trong ReceivingErrorHandler |
| `exception/receiving/ReceivingValidationException.java` | Tách type trong ReceivingErrorHandler |
| `exception/customerreturn/ReturnNotFoundException.java` | Tách type trong ReturnErrorHandler |
| `exception/customerreturn/ReturnConflictException.java` | Tách type trong ReturnErrorHandler |
| `exception/customerreturn/ReturnValidationException.java` | Tách type trong ReturnErrorHandler |
| `dto/delivery/DeliveryItemResponse.java` | Tách record trong DeliveryResponse |
| `dto/delivery/DeliveryHistoryResponse.java` | Tách record trong DeliveryResponse |
| `dto/search/IndexResult.java` | HTTP response trước là nested record của CatalogIndexService |
| `dto/search/SearchResult.java` | HTTP response trước là nested record của CatalogIndexService |
| `model/search/ProductText.java` | Dữ liệu nội bộ trước nested trong CatalogIndexService; client không cần phụ thuộc service |
| `model/chat/ModelReply.java` | Dữ liệu trả về từ GemmaClient, tách khỏi nested type |
| `repository/jdbc/AccountRepository.java` | SQL từ AccountUserDetailsService; security giữ kết nối UserDetails/Principal |
| `repository/jdbc/InventoryRepository.java` | SQL từ InventoryService; service giữ validation, tính tồn và transaction |
| `repository/jdbc/ReceivingRepository.java` | SQL từ ReceivingService; service phối hợp nhập kho, receiving record và movement |
| `repository/jdbc/StockReservationRepository.java` | SQL từ StockReservationService; service giữ thứ tự product ID, available và reserve/release |
| `repository/jdbc/PaymentOrderRepository.java` | JDBC từ PaymentService; service phối hợp JPA payment và reservation trong cùng transaction |
| `repository/jdbc/DeliveryRepository.java` | SQL từ DeliveryService; service giữ state machine và xuất kho |
| `repository/jdbc/ReturnRepository.java` | SQL từ ReturnService; service giữ số lượng trả, state machine và restock |
| `repository/jdbc/DashboardRepository.java` | SQL aggregate từ DashboardService; service giữ ngày/timezone/zero-fill |
| `repository/jdbc/CatalogIndexRepository.java` | SELECT sản phẩm active từ CatalogIndexService; service giữ batch/upsert/stale deletion |

Đổi package cần mở `public` cho type, constructor và method được tầng khác gọi;
không mở lifecycle method/helper nội bộ entity. Các field trước truy cập trong
cùng package như ProductImageException.status/LimitReached.retryAfter chuyển
thành accessor để không công khai field. Các record phục vụ khóa/đọc được đặt
cạnh repository; một số projection chỉ đọc tái sử dụng DTO đã có, DTO không I/O.

Các test di chuyển theo tầng tương ứng, giữ test controller, security, service,
repository/JPA và integration. Thêm API snapshot và guard chiều phụ thuộc; không
thêm dependency kiểm tra kiến trúc. Xem QA để phân biệt baseline và kết quả mới.

## Repo tham khảo

- [Spring PetClinic REST: OwnerRestControllerV1](https://github.com/spring-petclinic/spring-petclinic-rest/blob/afc8fc1d3b8ec8de69414482b3bd70a1da78cb95/src/main/java/org/springframework/samples/petclinic/rest/controller/v1/OwnerRestControllerV1.java)
  và [ClinicServiceImpl](https://github.com/spring-petclinic/spring-petclinic-rest/blob/afc8fc1d3b8ec8de69414482b3bd70a1da78cb95/src/main/java/org/springframework/samples/petclinic/service/ClinicServiceImpl.java):
  tham khảo chiều controller → service → repository và tách lớp theo vai trò.
- [EMSI-LearnHub: PostService](https://github.com/EnniouarMohammed/EMSI-LearnHub/blob/9ab0acf18893382e7be1d5f8aea150e1ee1bfc39/src/main/java/ma/xproce/emsilearnhub/service/PostService.java):
  tham khảo bố cục controller/service/repository/model/dto/security/config.
  [PostMapper](https://github.com/EnniouarMohammed/EMSI-LearnHub/blob/9ab0acf18893382e7be1d5f8aea150e1ee1bfc39/src/main/java/ma/xproce/emsilearnhub/mapper/PostMapper.java)
  có truy cập repository; không sao chép cách đó vì mapper cần thuần và không che
  giấu query. [Test ứng dụng](https://github.com/EnniouarMohammed/EMSI-LearnHub/blob/9ab0acf18893382e7be1d5f8aea150e1ee1bfc39/src/test/java/ma/xproce/emsilearnhub/EmsiLearnHubApplicationTests.java)
  chỉ kiểm tra context, không thay thế các test nghiệp vụ/concurrency của ShopFlow.
- [Spring MVC REST response body](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/responsebody.html)
  giải thích controller trả body qua HTTP message conversion. Spring không bắt
  buộc một cây package duy nhất.

## Việc tiếp theo

Giữ nguyên ba đường đọc N+1 để sửa và đo query count riêng trong SF-136:
`GET /deliveries` là `1 + 2N`; `GET /returns` và `GET /returns/orders` là `1 + N`
SELECT từ đường gọi source, chưa phải số đo thực nghiệm. SF-132 dọn dependency
Lombok/MapStruct. Không nhận hai công việc này đã hoàn tất cùng việc chuyển tầng.
