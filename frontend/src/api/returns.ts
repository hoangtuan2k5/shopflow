import { request } from './httpClient'

export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'RESTOCKED' | 'REJECTED'

export interface ReturnItem {
  orderItemId: number
  productId: number
  productName: string
  quantity: number
}

export interface ReturnRequest {
  id: number
  orderId: number
  status: ReturnStatus
  reason: string | null
  restockable: boolean
  createdAt: string
  updatedAt: string
  items: ReturnItem[]
}

export interface ReturnableItem {
  orderItemId: number
  productId: number
  productName: string
  quantity: number
  returnedQuantity: number
  returnableQuantity: number
}

export interface ReturnableOrder {
  orderId: number
  receiverName: string
  city: string
  totalAmount: number
  createdAt: string
  items: ReturnableItem[]
}

export interface CreateReturnRequest {
  orderId: number
  reason: string | null
  items: Array<{ orderItemId: number; quantity: number }>
}

export interface UpdateReturnRequest {
  toStatus: ReturnStatus
  restockable?: boolean
}

export interface ReturnErrorDetails {
  message: string
  status: number
  fieldErrors: Record<string, string>
}

export function getReturns() {
  return request<ReturnRequest[]>({
    method: 'GET',
    url: '/returns',
  })
}

export function getReturnableOrders() {
  return request<ReturnableOrder[]>({
    method: 'GET',
    url: '/returns/orders',
  })
}

export function createReturn(returnRequest: CreateReturnRequest) {
  return request<ReturnRequest>({
    method: 'POST',
    url: '/returns',
    data: returnRequest,
  })
}

export function updateReturn(returnId: number, update: UpdateReturnRequest) {
  return request<ReturnRequest>({
    method: 'PATCH',
    url: `/returns/${returnId}`,
    data: update,
  })
}
