import { request } from './httpClient'

export type DeliveryStatus = 'NONE' | 'PREPARING' | 'SHIPPED' | 'DELIVERED'

export interface DeliveryItem {
  productId: number
  productName: string
  quantity: number
}

export interface DeliveryHistory {
  fromStatus: DeliveryStatus
  toStatus: DeliveryStatus
  changedAt: string
  changedBy: string | null
}

export interface DeliveryOrder {
  orderId: number
  orderStatus: 'PAID'
  deliveryStatus: DeliveryStatus
  receiverName: string
  city: string
  totalAmount: number
  createdAt: string
  items: DeliveryItem[]
  history: DeliveryHistory[]
}

export interface DeliveryErrorDetails {
  message: string
  status: number
}

export function getDeliveries() {
  return request<DeliveryOrder[]>({
    method: 'GET',
    url: '/deliveries',
  })
}

export function updateDelivery(orderId: number, toStatus: DeliveryStatus) {
  return request<DeliveryOrder>({
    method: 'PATCH',
    url: `/orders/${orderId}/delivery`,
    data: { toStatus },
  })
}
