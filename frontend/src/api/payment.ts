import { request } from './httpClient'

export type SimulatedPaymentResult = 'SUCCESS' | 'FAILED' | 'EXPIRED'
export type PaymentStatus = SimulatedPaymentResult

export interface CreatePaymentRequest {
  result: SimulatedPaymentResult
  failureReason?: string
}

export interface PaymentResponse {
  id: number
  orderId: number
  method: 'CARD'
  status: PaymentStatus
  amount: number
  paidAt: string | null
  failedReason: string | null
  createdAt: string
  orderStatus: 'PAID' | 'PAYMENT_FAILED'
}

export interface PaymentErrorDetails {
  message?: string
  status?: number
}

export function createPayment(orderId: number, body: CreatePaymentRequest) {
  return request<PaymentResponse>({
    method: 'POST',
    url: `/orders/${orderId}/payments`,
    data: body,
  })
}
