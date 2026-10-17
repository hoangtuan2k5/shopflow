import { request } from './httpClient'
import { validateProductPrice } from './validateProductPrice'

export type StockStatus = 'IN_STOCK' | 'OUT_OF_STOCK'

export interface ProductListItem {
  id: number
  name: string
  price: number
  imageUrl: string | null
  stockStatus: StockStatus
}

export interface ProductDetail extends ProductListItem {
  description: string | null
}

export interface ProductPage {
  items: ProductListItem[]
  nextCursor: string | null
}

export async function getProducts(
  options: {
    after?: string | null
    q?: string
    signal?: AbortSignal
  } = {},
): Promise<ProductPage> {
  const page = await request<ProductPage>({
    method: 'GET',
    url: '/products',
    params: { limit: 20, after: options.after ?? undefined, q: options.q || undefined },
    signal: options.signal,
  })

  return { ...page, items: page.items.map(validateProductPrice) }
}

export async function getProductById(id: number) {
  const product = await request<ProductDetail>({ method: 'GET', url: `/products/${id}` })

  return validateProductPrice(product)
}

export function uploadProductImage(id: number, file: File) {
  const data = new FormData()
  data.append('file', file)
  return request<{ imageUrl: string }>({ method: 'POST', url: `/products/${id}/image`, data })
}
