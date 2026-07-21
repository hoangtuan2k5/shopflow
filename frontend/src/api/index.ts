export { apiBaseUrl, httpClient, request, ApiClientError } from './httpClient'
export type { ApiClientProblem } from './httpClient'
export { getOpenApiDocument } from './openApi'
export type { OpenApiDocument, OpenApiInfo, OpenApiTag } from './openApi'
export { getProductById, getProducts } from './catalog'
export type { ProductDetail, ProductListItem, StockStatus } from './catalog'
export { createOrder } from './order'
export type { CreateOrderRequest, OrderErrorDetails, OrderResponse, PaymentMethod } from './order'
export { createPayment } from './payment'
export type {
  CreatePaymentRequest,
  PaymentErrorDetails,
  PaymentResponse,
  PaymentStatus,
  SimulatedPaymentResult,
} from './payment'
