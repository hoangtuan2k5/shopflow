import { request } from './httpClient'

export type StockStatus = 'IN_STOCK' | 'OUT_OF_STOCK'

export interface ProductListItem {
  id: number
  name: string
  price: number
  stockStatus: StockStatus
}

export interface ProductDetail extends ProductListItem {
  description: string | null
}

export function getProducts() {
  return request<ProductListItem[]>({ method: 'GET', url: '/products' })
}

export function getProductById(id: number) {
  return request<ProductDetail>({ method: 'GET', url: `/products/${id}` })
}
