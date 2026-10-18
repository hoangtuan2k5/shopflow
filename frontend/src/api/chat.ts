import { z } from 'zod'
import { request } from './httpClient'
import { validateProductPrice } from './validateProductPrice'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}
export interface ChatRequest {
  question: string
  history: ChatMessage[]
}

const responseSchema = z.object({
  reply: z.string().trim().min(1).max(4000),
  products: z
    .array(
      z.object({
        id: z.number().int().positive(),
        name: z.string().min(1),
        price: z.number().int().nonnegative(),
        stockStatus: z.enum(['IN_STOCK', 'OUT_OF_STOCK']),
        imageUrl: z.string().nullable(),
      }),
    )
    .max(3),
})

export async function sendChatMessage(data: ChatRequest) {
  const response = await request<unknown>({
    method: 'POST',
    url: '/chat/messages',
    data,
    timeout: 20_000,
  })
  const parsed = responseSchema.safeParse(response)
  if (!parsed.success) throw new TypeError('Invalid product assistant response')
  return { ...parsed.data, products: parsed.data.products.map(validateProductPrice) }
}
