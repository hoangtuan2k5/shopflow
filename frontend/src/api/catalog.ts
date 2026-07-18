import { request } from './httpClient'
import { validateProductPrice } from './validateProductPrice'

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

export async function getProducts() {
  const products = await request<ProductListItem[]>({ method: 'GET', url: '/products' })

  return products.map(validateProductPrice)
}

export async function getProductById(id: number) {
  const product = await request<ProductDetail>({ method: 'GET', url: `/products/${id}` })

  return validateProductPrice(product)
}
