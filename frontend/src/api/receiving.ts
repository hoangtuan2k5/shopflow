import { request } from './httpClient'

export interface SupplierReceivingRequest {
  productId: number
  quantity: number
  supplierName: string | null
  note: string | null
}

export interface SupplierReceivingResponse {
  id: number
  productId: number
  productName: string
  quantity: number
  supplierName: string | null
  note: string | null
  createdAt: string
  createdBy: string | null
  onHandStock: number
  reservedStock: number
  availableStock: number
}

export interface ReceivingErrorDetails {
  message: string
  status: number
  fieldErrors: Record<string, string>
}

export function createReceiving(receiving: SupplierReceivingRequest) {
  return request<SupplierReceivingResponse>({
    method: 'POST',
    url: '/receivings',
    data: receiving,
  })
}
