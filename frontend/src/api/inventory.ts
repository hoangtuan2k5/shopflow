import { request } from './httpClient'

export interface InventoryItem {
  productId: number
  productName: string
  onHandStock: number
  reservedStock: number
  availableStock: number
}

export interface StockAdjustmentRequest {
  delta: number
  reason: string
}

export interface InventoryErrorDetails {
  message: string
  status: number
  fieldErrors: Record<string, string>
}

export function getInventory() {
  return request<InventoryItem[]>({
    method: 'GET',
    url: '/inventory',
  })
}

export function adjustStock(productId: number, adjustment: StockAdjustmentRequest) {
  return request<InventoryItem>({
    method: 'POST',
    url: `/inventory/${productId}/adjustments`,
    data: adjustment,
  })
}
