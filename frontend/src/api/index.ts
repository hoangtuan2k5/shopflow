export { apiBaseUrl, httpClient, onUnauthorized, request, ApiClientError } from './httpClient'
export type { ApiClientProblem } from './httpClient'
export { getSession, login, logout } from './auth'
export type {
  AuthErrorDetails,
  AuthenticatedUser,
  LoginRequest,
  SessionResponse,
  UserRole,
} from './auth'
export { getOpenApiDocument } from './openApi'
export type { OpenApiDocument, OpenApiInfo, OpenApiTag } from './openApi'
export { getProductById, getProducts } from './catalog'
export type { ProductDetail, ProductListItem, StockStatus } from './catalog'
export { adjustStock, getInventory, updateLowStockThreshold } from './inventory'
export type {
  InventoryErrorDetails,
  InventoryItem,
  LowStockThresholdRequest,
  StockAdjustmentRequest,
} from './inventory'
export { createReturn, getReturnableOrders, getReturns, updateReturn } from './returns'
export type {
  CreateReturnRequest,
  ReturnErrorDetails,
  ReturnItem,
  ReturnRequest,
  ReturnStatus,
  ReturnableItem,
  ReturnableOrder,
  UpdateReturnRequest,
} from './returns'
export { createReceiving } from './receiving'
export type {
  ReceivingErrorDetails,
  SupplierReceivingRequest,
  SupplierReceivingResponse,
} from './receiving'
export { getDeliveries, updateDelivery } from './delivery'
export type {
  DeliveryErrorDetails,
  DeliveryHistory,
  DeliveryItem,
  DeliveryOrder,
  DeliveryStatus,
} from './delivery'
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
