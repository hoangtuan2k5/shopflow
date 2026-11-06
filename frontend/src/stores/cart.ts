import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { getProductById, type ProductListItem } from '@/api'

const storageKey = 'shopflow.cart.v1'
const maxBytes = 64 * 1024
const validId = (value: unknown): value is number =>
  typeof value === 'number' && Number.isSafeInteger(value) && value > 0
const validQuantity = (value: unknown): value is number =>
  typeof value === 'number' && Number.isInteger(value) && value > 0 && value <= 2_147_483_647

export const useCartStore = defineStore('cart', () => {
  const quantities = ref<Record<number, number>>({})
  const products = ref<Record<number, ProductListItem>>({})
  const storageWarning = ref(false)
  const quantityWarning = ref(false)
  const loading = ref(false)
  const ready = ref(false)
  const failed = ref<Record<number, boolean>>({})
  let refreshing: Promise<void> | null = null

  try {
    const raw = localStorage.getItem(storageKey)
    if (raw !== null) {
      if (new TextEncoder().encode(raw).length > maxBytes) throw new Error('Oversized cart')
      const saved: unknown = JSON.parse(raw)
      if (
        !saved ||
        typeof saved !== 'object' ||
        !('version' in saved) ||
        saved.version !== 1 ||
        !('items' in saved) ||
        !Array.isArray(saved.items)
      )
        throw new Error('Invalid cart')
      const restored: Record<number, number> = {}
      for (const item of saved.items) {
        if (
          !item ||
          typeof item !== 'object' ||
          !validId(item.productId) ||
          !validQuantity(item.quantity) ||
          item.productId in restored
        )
          throw new Error('Invalid cart item')
        restored[item.productId] = item.quantity
      }
      quantities.value = restored
    }
  } catch {
    storageWarning.value = true
  }

  watch(
    quantities,
    (value) => {
      try {
        const raw = JSON.stringify({
          version: 1,
          items: Object.entries(value).map(([id, quantity]) => ({
            productId: Number(id),
            quantity,
          })),
        })
        if (new TextEncoder().encode(raw).length > maxBytes) throw new Error('Oversized cart')
        localStorage.setItem(storageKey, raw)
        storageWarning.value = false
      } catch {
        storageWarning.value = true
        // Remove an older snapshot so a reload cannot silently resurrect a different cart.
        try {
          localStorage.removeItem(storageKey)
        } catch {
          /* Storage may be entirely unavailable. */
        }
      }
    },
    { deep: true, flush: 'sync' },
  )

  const itemCount = computed(() =>
    Object.values(quantities.value).reduce((sum, quantity) => sum + quantity, 0),
  )
  const issues = computed(() =>
    Object.keys(quantities.value)
      .map(Number)
      .filter(
        (id) =>
          failed.value[id] ||
          !products.value[id] ||
          products.value[id].stockStatus === 'OUT_OF_STOCK',
      ),
  )

  async function refresh() {
    if (refreshing) return refreshing
    loading.value = true
    refreshing = Promise.all(
      Object.keys(quantities.value).map(async (key) => {
        const id = Number(key)
        try {
          const product = await getProductById(id)
          if (product.id !== id || !['IN_STOCK', 'OUT_OF_STOCK'].includes(product.stockStatus))
            throw new Error('Invalid product')
          if (quantities.value[id]) {
            products.value[id] = product
            delete failed.value[id]
          }
        } catch {
          if (quantities.value[id]) failed.value[id] = true
        }
      }),
    )
      .then(() => {})
      .finally(() => {
        loading.value = false
        ready.value = true
        refreshing = null
      })
    return refreshing
  }

  function add(product: ProductListItem) {
    if (!validId(product.id) || product.stockStatus === 'OUT_OF_STOCK') return
    const next = (quantities.value[product.id] ?? 0) + 1
    if (!validQuantity(next)) {
      quantityWarning.value = true
      return
    }
    products.value[product.id] = product
    delete failed.value[product.id]
    quantities.value = { ...quantities.value, [product.id]: next }
    quantityWarning.value = false
  }

  function update(id: number, quantity: number) {
    if (quantity === 0) {
      remove(id)
      return
    }
    if (!validId(id) || !quantities.value[id] || !validQuantity(quantity)) {
      quantityWarning.value = true
      return
    }
    quantities.value = { ...quantities.value, [id]: quantity }
    quantityWarning.value = false
  }

  function remove(id: number) {
    const next = { ...quantities.value }
    delete next[id]
    delete products.value[id]
    delete failed.value[id]
    quantities.value = next
  }

  function clear() {
    quantities.value = {}
    products.value = {}
    failed.value = {}
    quantityWarning.value = false
  }

  return {
    quantities,
    products,
    storageWarning,
    quantityWarning,
    loading,
    ready,
    itemCount,
    issues,
    add,
    update,
    remove,
    clear,
    refresh,
  }
})
