import { request } from './httpClient'

export type PaymentMethod = 'CARD'

export interface CreateOrderRequest {
  customer: {
    fullName: string
    email?: string | null
    phone?: string | null
  }
  shippingAddress: {
    receiverName: string
    phone: string
    addressLine: string
    district?: string | null
    city: string
  }
  paymentMethod: PaymentMethod
  items: Array<{ productId: number; quantity: number }>
}

export interface OrderResponse {
  id: number
  /** Tham chiếu không đoán được, dùng để thanh toán thay cho id tuần tự. */
  orderRef: string
  status: 'PENDING_PAYMENT'
  deliveryStatus: 'NONE'
  paymentMethod: PaymentMethod
  totalAmount: number
  customer: {
    fullName: string
    email: string | null
    phone: string | null
  }
  shippingAddress: {
    receiverName: string
    phone: string
    addressLine: string
    district: string | null
    city: string
  }
  items: Array<{
    productId: number
    productName: string
    unitPrice: number
    quantity: number
    lineTotal: number
  }>
  createdAt: string
}

export interface OrderErrorDetails {
  message?: string
  status?: number
  fieldErrors?: Record<string, string>
  unavailableProductIds?: number[]
  insufficientItems?: Array<{
    productId: number
    requestedQuantity: number
    availableStock: number
  }>
}

export function createOrder(body: CreateOrderRequest) {
  return request<OrderResponse>({ method: 'POST', url: '/orders', data: body })
}
