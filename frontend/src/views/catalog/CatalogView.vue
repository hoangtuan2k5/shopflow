<script setup lang="ts">
import { computed, nextTick, ref, type Component } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import {
  IconAlertCircle,
  IconArrowRight,
  IconCheck,
  IconCircleCheck,
  IconCircleX,
  IconInbox,
  IconMinus,
  IconPackage,
  IconPlus,
  IconReceipt,
  IconRefresh,
  IconSearch,
  IconShieldCheck,
  IconShoppingCart,
  IconTrash,
  IconX,
} from '@tabler/icons-vue'
import {
  ApiClientError,
  createOrder,
  getProductById,
  getProducts,
  type CreateOrderRequest,
  type OrderErrorDetails,
  type StockStatus,
} from '@/api'
import { Button } from '@/components/ui/button'
import CheckoutForm from '@/components/forms/CheckoutForm.vue'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

const selectedProductId = ref<number | null>(null)
const selectedQuantities = ref<Record<number, number>>({})
const searchQuery = ref('')
const cartOpen = ref(false)
const checkoutOpen = ref(false)

const productsQuery = useQuery({
  queryKey: ['products'],
  queryFn: getProducts,
  retry: 1,
})

const productQuery = useQuery({
  queryKey: computed(() => ['products', selectedProductId.value]),
  queryFn: () => getProductById(selectedProductId.value!),
  enabled: computed(() => selectedProductId.value !== null),
  retry: 1,
})

const products = computed(() => productsQuery.data.value ?? [])
const normalizedSearch = computed(() => searchQuery.value.trim().toLocaleLowerCase('vi'))
const filteredProducts = computed(() => {
  if (!normalizedSearch.value) return products.value
  return products.value.filter((product) =>
    product.name.toLocaleLowerCase('vi').includes(normalizedSearch.value),
  )
})
const filteredInStockCount = computed(
  () => filteredProducts.value.filter((product) => product.stockStatus === 'IN_STOCK').length,
)

const stockStatus = {
  IN_STOCK: {
    label: 'Còn hàng',
    classes: 'bg-success-muted text-success',
    icon: IconCircleCheck,
  },
  OUT_OF_STOCK: {
    label: 'Hết hàng',
    classes: 'bg-destructive-muted text-destructive',
    icon: IconCircleX,
  },
} satisfies Record<StockStatus, { label: string; classes: string; icon: Component }>

const productVisuals = [
  'from-[#dce9ff] to-[#eef4ff] text-[#28558d]',
  'from-[#d7f4eb] to-[#ecfbf7] text-[#008169]',
  'from-[#eadcf8] to-[#f6effd] text-[#70499b]',
]

const currency = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
})

const checkoutLines = computed(() =>
  products.value.flatMap((product) => {
    const quantity = selectedQuantities.value[product.id]
    return quantity ? [{ product, quantity }] : []
  }),
)
const selectedItemCount = computed(() =>
  checkoutLines.value.reduce((total, line) => total + line.quantity, 0),
)
const cartTotal = computed(() =>
  checkoutLines.value.reduce((total, line) => total + line.product.price * line.quantity, 0),
)

const orderMutation = useMutation({ mutationFn: createOrder })
const orderError = computed<OrderErrorDetails | null>(() => {
  const error = orderMutation.error.value
  if (!error) return null
  if (error instanceof ApiClientError && isOrderErrorDetails(error.details)) return error.details
  return { message: 'Không thể tạo đơn hàng. Vui lòng thử lại.' }
})
const createdOrder = computed(() => orderMutation.data.value ?? null)

function isOrderErrorDetails(value: unknown): value is OrderErrorDetails {
  return typeof value === 'object' && value !== null && 'message' in value
}

function visualClass(productId: number) {
  return productVisuals[Math.abs(productId) % productVisuals.length]
}

function scrollToProducts() {
  document.getElementById('products')?.scrollIntoView({ behavior: 'smooth' })
}

function addToCart(product: { id: number; stockStatus: StockStatus }) {
  if (product.stockStatus === 'OUT_OF_STOCK') return
  selectedQuantities.value = {
    ...selectedQuantities.value,
    [product.id]: (selectedQuantities.value[product.id] ?? 0) + 1,
  }
  closeProduct()
}

function updateQuantity(productId: number, quantity: number) {
  if (quantity <= 0) {
    removeFromCart(productId)
    return
  }
  selectedQuantities.value = { ...selectedQuantities.value, [productId]: quantity }
}

function removeFromCart(productId: number) {
  const next = { ...selectedQuantities.value }
  delete next[productId]
  selectedQuantities.value = next
}

function openCheckout() {
  if (checkoutLines.value.length === 0) return
  cartOpen.value = false
  void nextTick(() => {
    checkoutOpen.value = true
  })
}

type CheckoutFormValues = {
  fullName: string
  email: string
  customerPhone: string
  receiverName: string
  receiverPhone: string
  addressLine: string
  district: string
  city: string
}

function submitOrder(values: CheckoutFormValues) {
  const body: CreateOrderRequest = {
    customer: {
      fullName: values.fullName,
      email: values.email || null,
      phone: values.customerPhone || null,
    },
    shippingAddress: {
      receiverName: values.receiverName,
      phone: values.receiverPhone,
      addressLine: values.addressLine,
      district: values.district || null,
      city: values.city,
    },
    paymentMethod: 'CARD',
    items: checkoutLines.value.map((line) => ({
      productId: line.product.id,
      quantity: line.quantity,
    })),
  }
  orderMutation.reset()
  orderMutation.mutate(body)
}

function startOver() {
  selectedQuantities.value = {}
  orderMutation.reset()
  checkoutOpen.value = false
}

function closeProduct() {
  selectedProductId.value = null
}
</script>

<template>
  <div class="min-h-screen bg-[#f5f6f8] text-foreground">
    <header class="sticky top-0 z-30 border-b border-primary/10 bg-card/95 backdrop-blur-xl">
      <div
        class="mx-auto grid max-w-7xl grid-cols-[1fr_auto] items-center gap-3 px-3 py-3 sm:px-6 md:grid-cols-[10rem_minmax(16rem,1fr)_auto] md:gap-7"
      >
        <img
          class="h-8 w-auto md:col-start-1 md:row-start-1 md:h-9"
          src="/brand/shopflow-wordmark.png"
          alt="ShopFlow"
        />
        <label class="relative col-span-2 row-start-2 md:col-span-1 md:col-start-2 md:row-start-1">
          <span class="sr-only">Tìm sản phẩm</span>
          <input
            v-model="searchQuery"
            type="search"
            autocomplete="off"
            class="h-11 w-full rounded-lg border-2 border-primary bg-card px-4 pr-12 text-sm outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-2 md:h-12"
            placeholder="Tìm sản phẩm bạn cần..."
          />
          <span
            class="pointer-events-none absolute right-1 top-1 grid size-9 place-items-center rounded-md bg-primary text-white md:size-10"
          >
            <IconSearch :size="19" :stroke-width="1.9" aria-hidden="true" />
          </span>
        </label>
        <Button
          class="h-11 gap-2 border-border px-3 font-bold text-primary md:col-start-3 md:row-start-1 md:h-12"
          variant="outline"
          type="button"
          aria-label="Mở giỏ hàng"
          @click="cartOpen = true"
        >
          <IconShoppingCart :size="20" :stroke-width="1.8" aria-hidden="true" />
          <span class="hidden sm:inline">Giỏ hàng</span>
          <span
            class="grid size-6 place-items-center rounded-full bg-success text-xs font-bold text-white"
            aria-live="polite"
          >
            {{ selectedItemCount }}
          </span>
        </Button>
      </div>
    </header>

    <main class="mx-auto grid max-w-7xl gap-5 px-2.5 pb-24 pt-3 sm:px-6 sm:pt-6">
      <section
        class="relative min-h-56 overflow-hidden rounded-xl bg-[radial-gradient(circle_at_82%_20%,rgba(30,245,184,0.28),transparent_23%),linear-gradient(120deg,#11204a_0%,#1b3778_58%,#087861_130%)] text-white shadow-[0_18px_50px_rgba(20,37,83,0.18)] sm:min-h-72 sm:rounded-2xl"
      >
        <div class="relative z-10 max-w-2xl px-5 py-7 sm:px-12 sm:py-11">
          <p class="text-xs font-black uppercase tracking-[0.16em] text-[#7ff0cf]">
            Mua sắm liền mạch
          </p>
          <h1
            class="mt-3 max-w-lg text-[2rem] font-black leading-[1.02] tracking-[-0.04em] sm:text-5xl"
          >
            Chọn nhanh.<br />Đặt hàng gọn.
          </h1>
          <p class="mt-3 max-w-xl text-sm leading-relaxed text-[#e3eaff] sm:text-base">
            Giá VND minh bạch, trạng thái còn hàng rõ ràng và quy trình đặt hàng liền mạch trên mọi
            thiết bị.
          </p>
          <Button
            class="mt-5 bg-[#6ff0cb] font-black text-[#073a32] hover:bg-[#8bf4d7]"
            type="button"
            @click="scrollToProducts"
          >
            Khám phá sản phẩm
            <IconArrowRight :size="18" :stroke-width="1.9" aria-hidden="true" />
          </Button>
        </div>
        <div class="absolute -bottom-12 right-5 hidden size-72 sm:block" aria-hidden="true">
          <span class="absolute inset-3 rounded-full bg-white/[0.08]" />
          <span class="absolute bottom-10 left-8 size-32 rounded-full bg-brand/40" />
          <div
            class="absolute bottom-5 right-5 grid h-48 w-44 rotate-6 place-items-center rounded-[2rem] border-[3px] border-white/90 bg-white/10 shadow-2xl"
          >
            <img class="w-24 brightness-0 invert" src="/brand/shopflow-mark.png" alt="" />
          </div>
        </div>
      </section>

      <section
        class="flex snap-x overflow-x-auto rounded-xl border border-border bg-card sm:grid sm:grid-cols-3"
        aria-label="Lợi ích mua hàng"
      >
        <div class="flex min-w-[82%] snap-start items-center gap-3 p-4 sm:min-w-0">
          <span class="grid size-10 shrink-0 place-items-center rounded-xl bg-accent text-success">
            ₫
          </span>
          <span
            ><strong class="block text-sm">Giá VND rõ ràng</strong
            ><small class="text-muted-foreground">Không làm tròn hoặc phí ẩn</small></span
          >
        </div>
        <div
          class="flex min-w-[82%] snap-start items-center gap-3 border-l border-border p-4 sm:min-w-0"
        >
          <span class="grid size-10 shrink-0 place-items-center rounded-xl bg-accent text-success">
            <IconShieldCheck :size="20" :stroke-width="1.8" aria-hidden="true" />
          </span>
          <span
            ><strong class="block text-sm">Tồn kho trực tiếp</strong
            ><small class="text-muted-foreground">Kiểm tra lại khi đặt hàng</small></span
          >
        </div>
        <div
          class="flex min-w-[82%] snap-start items-center gap-3 border-l border-border p-4 sm:min-w-0"
        >
          <span class="grid size-10 shrink-0 place-items-center rounded-xl bg-accent text-success">
            <IconReceipt :size="20" :stroke-width="1.8" aria-hidden="true" />
          </span>
          <span
            ><strong class="block text-sm">Đặt hàng liền mạch</strong
            ><small class="text-muted-foreground">Giữ giỏ khi cần sửa thông tin</small></span
          >
        </div>
      </section>

      <section id="products" class="scroll-mt-36 pt-3">
        <header class="mb-4 flex items-end justify-between gap-4">
          <div>
            <p class="text-xs font-black uppercase tracking-[0.16em] text-success">Gợi ý hôm nay</p>
            <h2 class="mt-1 text-2xl font-black tracking-tight text-primary sm:text-3xl">
              Sản phẩm dành cho bạn
            </h2>
            <p
              v-if="!productsQuery.isPending.value && !productsQuery.isError.value"
              class="mt-2 text-sm text-muted-foreground"
            >
              {{ filteredProducts.length }} sản phẩm · {{ filteredInStockCount }} còn hàng
            </p>
          </div>
          <Button
            v-if="searchQuery"
            variant="outline"
            size="sm"
            type="button"
            @click="searchQuery = ''"
          >
            <IconX :size="16" :stroke-width="1.8" aria-hidden="true" />
            Xóa tìm kiếm
          </Button>
        </header>

        <div
          v-if="productsQuery.isPending.value"
          class="grid grid-cols-2 gap-2 sm:gap-4 lg:grid-cols-3 xl:grid-cols-4"
          aria-label="Đang tải sản phẩm"
        >
          <div
            v-for="index in 4"
            :key="index"
            class="overflow-hidden rounded-xl border border-border bg-card"
            aria-hidden="true"
          >
            <div class="aspect-square animate-pulse bg-muted sm:aspect-[4/3]" />
            <div class="grid gap-3 p-4">
              <div class="h-4 w-3/4 animate-pulse rounded bg-muted" />
              <div class="h-7 w-1/2 animate-pulse rounded bg-muted" />
              <div class="h-10 animate-pulse rounded bg-muted" />
            </div>
          </div>
        </div>

        <div
          v-else-if="productsQuery.isError.value"
          class="flex items-start gap-4 border-l-4 border-destructive bg-destructive-muted p-5"
          role="alert"
        >
          <IconAlertCircle
            class="mt-0.5 shrink-0 text-destructive"
            :size="24"
            :stroke-width="1.8"
            aria-hidden="true"
          />
          <div>
            <p class="font-semibold">Không thể tải sản phẩm</p>
            <p class="mt-1 text-sm text-muted-foreground">
              Vui lòng kiểm tra kết nối tới backend rồi thử lại.
            </p>
            <Button class="mt-4" variant="outline" @click="productsQuery.refetch()">
              <IconRefresh :size="18" :stroke-width="1.8" aria-hidden="true" />
              Thử lại
            </Button>
          </div>
        </div>

        <div
          v-else-if="products.length === 0"
          class="grid justify-items-center gap-3 rounded-xl border border-dashed border-border bg-card py-16 text-center"
        >
          <div class="grid size-12 place-items-center rounded-lg bg-secondary text-primary">
            <IconInbox :size="26" :stroke-width="1.6" aria-hidden="true" />
          </div>
          <div>
            <p class="font-semibold">Chưa có sản phẩm</p>
            <p class="mt-1 text-sm text-muted-foreground">
              Catalog hiện chưa có sản phẩm đang bán.
            </p>
          </div>
        </div>

        <div
          v-else-if="filteredProducts.length === 0"
          class="grid justify-items-center gap-3 rounded-xl border border-dashed border-border bg-card py-16 text-center"
        >
          <IconSearch :size="30" :stroke-width="1.6" aria-hidden="true" />
          <div>
            <p class="font-semibold">Không tìm thấy sản phẩm</p>
            <p class="mt-1 text-sm text-muted-foreground">Thử một từ khóa khác.</p>
          </div>
          <Button variant="outline" type="button" @click="searchQuery = ''">Xóa tìm kiếm</Button>
        </div>

        <div v-else class="grid grid-cols-2 gap-2 sm:gap-4 lg:grid-cols-3 xl:grid-cols-4">
          <article
            v-for="product in filteredProducts"
            :key="product.id"
            class="group flex min-w-0 flex-col overflow-hidden rounded-xl border border-border bg-card shadow-[0_5px_16px_rgba(20,37,83,0.05)] transition duration-200 hover:-translate-y-1 hover:border-brand/50 hover:shadow-[0_16px_32px_rgba(20,37,83,0.12)]"
          >
            <button
              class="relative grid aspect-square place-items-center overflow-hidden bg-gradient-to-br sm:aspect-[4/3]"
              :class="visualClass(product.id)"
              type="button"
              :aria-label="`Xem chi tiết ${product.name}`"
              @click="selectedProductId = product.id"
            >
              <span class="absolute -right-8 -top-10 size-36 rounded-full bg-white/45" />
              <span class="absolute -bottom-10 -left-8 size-28 rounded-full bg-white/45" />
              <IconPackage
                class="relative drop-shadow-xl transition-transform duration-200 group-hover:scale-105"
                :size="64"
                :stroke-width="1.5"
                aria-hidden="true"
              />
              <span
                :class="[
                  'absolute left-2 top-2 flex items-center gap-1 rounded-md px-2 py-1 text-[9px] font-black uppercase sm:left-3 sm:top-3 sm:text-[11px]',
                  stockStatus[product.stockStatus].classes,
                ]"
              >
                <component
                  :is="stockStatus[product.stockStatus].icon"
                  :size="13"
                  :stroke-width="2"
                  aria-hidden="true"
                />
                {{ stockStatus[product.stockStatus].label }}
              </span>
            </button>
            <div class="flex flex-1 flex-col p-3 sm:p-4">
              <button
                class="line-clamp-2 min-h-10 text-left text-sm font-bold leading-snug hover:text-primary sm:text-base"
                type="button"
                @click="selectedProductId = product.id"
              >
                {{ product.name }}
              </button>
              <p class="mt-3 text-lg font-black tracking-tight text-primary sm:text-2xl">
                {{ currency.format(product.price) }}
              </p>
              <Button
                class="mt-3 w-full px-2 text-xs font-black sm:text-sm"
                type="button"
                :disabled="product.stockStatus === 'OUT_OF_STOCK'"
                @click="addToCart(product)"
              >
                <IconShoppingCart :size="17" :stroke-width="1.8" aria-hidden="true" />
                {{ product.stockStatus === 'OUT_OF_STOCK' ? 'Hết hàng' : 'Thêm vào giỏ' }}
              </Button>
            </div>
          </article>
        </div>
      </section>
    </main>

    <Button
      v-if="selectedItemCount > 0"
      class="fixed inset-x-3 bottom-3 z-20 h-14 justify-between rounded-xl px-4 font-black shadow-[0_16px_40px_rgba(20,37,83,0.34)] sm:hidden"
      type="button"
      @click="cartOpen = true"
    >
      <span>Xem giỏ hàng · {{ selectedItemCount }} sản phẩm</span>
      <span>{{ currency.format(cartTotal) }}</span>
    </Button>

    <Dialog :open="selectedProductId !== null" @update:open="(open) => !open && closeProduct()">
      <DialogContent class="overflow-hidden p-0">
        <DialogClose as-child>
          <Button
            class="absolute right-4 top-4 z-10 size-10 bg-card/90 p-0"
            variant="outline"
            aria-label="Đóng chi tiết sản phẩm"
          >
            <IconX :size="20" :stroke-width="1.8" aria-hidden="true" />
          </Button>
        </DialogClose>

        <div v-if="productQuery.isPending.value" class="grid min-h-72 place-items-center p-6">
          <DialogHeader class="sr-only">
            <DialogTitle>Chi tiết sản phẩm</DialogTitle>
            <DialogDescription>Đang tải chi tiết sản phẩm.</DialogDescription>
          </DialogHeader>
          <IconPackage class="animate-pulse text-primary" :size="40" aria-hidden="true" />
        </div>

        <div v-else-if="productQuery.isError.value" class="p-6 pr-16" role="alert">
          <DialogHeader>
            <DialogTitle>Không thể tải sản phẩm</DialogTitle>
            <DialogDescription>Sản phẩm này hiện không thể truy cập.</DialogDescription>
          </DialogHeader>
        </div>

        <template v-else-if="productQuery.data.value">
          <div
            class="grid aspect-[16/8] place-items-center bg-gradient-to-br"
            :class="visualClass(productQuery.data.value.id)"
          >
            <IconPackage :size="76" :stroke-width="1.4" aria-hidden="true" />
          </div>
          <div class="grid gap-5 p-6">
            <DialogHeader>
              <span
                :class="[
                  'mr-auto flex items-center gap-1 rounded-md px-2 py-1 text-xs font-bold',
                  stockStatus[productQuery.data.value.stockStatus].classes,
                ]"
              >
                <IconCheck :size="14" aria-hidden="true" />
                {{ stockStatus[productQuery.data.value.stockStatus].label }}
              </span>
              <DialogTitle class="pt-2 text-xl leading-snug">
                {{ productQuery.data.value.name }}
              </DialogTitle>
              <DialogDescription class="leading-relaxed">
                {{ productQuery.data.value.description || 'Chưa có mô tả sản phẩm.' }}
              </DialogDescription>
            </DialogHeader>
            <p class="border-t border-border pt-5 text-2xl font-black text-primary">
              {{ currency.format(productQuery.data.value.price) }}
            </p>
            <Button
              type="button"
              :disabled="productQuery.data.value.stockStatus === 'OUT_OF_STOCK'"
              @click="addToCart(productQuery.data.value)"
            >
              <IconShoppingCart :size="18" :stroke-width="1.8" aria-hidden="true" />
              {{
                productQuery.data.value.stockStatus === 'OUT_OF_STOCK' ? 'Hết hàng' : 'Thêm vào giỏ'
              }}
            </Button>
          </div>
        </template>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="cartOpen">
      <DialogContent
        class="left-auto right-0 top-0 grid h-dvh w-[min(100%,29rem)] translate-x-0 translate-y-0 grid-rows-[auto_1fr_auto] gap-0 rounded-none border-y-0 border-r-0 p-0"
      >
        <div class="flex items-start justify-between border-b border-border p-5 text-left">
          <div>
            <p class="text-xs font-black uppercase tracking-[0.16em] text-success">
              Đơn hàng của bạn
            </p>
            <DialogTitle class="mt-1 text-2xl text-primary">Giỏ hàng</DialogTitle>
            <DialogDescription>{{ selectedItemCount }} sản phẩm đã chọn</DialogDescription>
          </div>
          <DialogClose as-child>
            <Button class="size-10 shrink-0 p-0" variant="outline" aria-label="Đóng giỏ hàng">
              <IconX :size="20" :stroke-width="1.8" aria-hidden="true" />
            </Button>
          </DialogClose>
        </div>

        <div v-if="checkoutLines.length === 0" class="grid place-items-center p-8 text-center">
          <div>
            <IconShoppingCart
              class="mx-auto text-muted-foreground"
              :size="42"
              :stroke-width="1.5"
              aria-hidden="true"
            />
            <p class="mt-3 font-bold">Giỏ hàng đang trống</p>
            <p class="mt-1 text-sm text-muted-foreground">Thêm sản phẩm để bắt đầu đặt hàng.</p>
          </div>
        </div>
        <div v-else class="overflow-y-auto px-5">
          <article
            v-for="line in checkoutLines"
            :key="line.product.id"
            class="grid grid-cols-[4.5rem_1fr_auto] gap-3 border-b border-border py-5"
          >
            <div
              class="grid size-[4.5rem] place-items-center rounded-lg bg-gradient-to-br"
              :class="visualClass(line.product.id)"
            >
              <IconPackage :size="30" :stroke-width="1.5" aria-hidden="true" />
            </div>
            <div class="min-w-0">
              <p class="truncate font-bold">{{ line.product.name }}</p>
              <p class="mt-1 text-sm text-muted-foreground">
                {{ currency.format(line.product.price) }}
              </p>
              <div class="mt-3 inline-flex items-center rounded-md border border-border">
                <button
                  class="grid size-8 place-items-center disabled:opacity-40"
                  type="button"
                  :disabled="line.quantity <= 1"
                  :aria-label="`Giảm số lượng ${line.product.name}`"
                  @click="updateQuantity(line.product.id, line.quantity - 1)"
                >
                  <IconMinus :size="15" :stroke-width="2" aria-hidden="true" />
                </button>
                <span class="min-w-8 text-center text-sm font-bold">{{ line.quantity }}</span>
                <button
                  class="grid size-8 place-items-center"
                  type="button"
                  :aria-label="`Tăng số lượng ${line.product.name}`"
                  @click="updateQuantity(line.product.id, line.quantity + 1)"
                >
                  <IconPlus :size="15" :stroke-width="2" aria-hidden="true" />
                </button>
              </div>
            </div>
            <button
              class="self-start rounded-md p-2 text-destructive hover:bg-destructive-muted"
              type="button"
              :aria-label="`Xóa ${line.product.name}`"
              @click="removeFromCart(line.product.id)"
            >
              <IconTrash :size="18" :stroke-width="1.8" aria-hidden="true" />
            </button>
          </article>
        </div>

        <footer class="border-t border-border bg-secondary/30 p-5">
          <div class="flex items-end justify-between gap-4">
            <span class="text-sm text-muted-foreground">Tạm tính</span>
            <strong class="whitespace-nowrap text-2xl font-black text-primary">
              {{ currency.format(cartTotal) }}
            </strong>
          </div>
          <Button
            class="mt-4 h-12 w-full bg-success font-black hover:bg-success/90"
            type="button"
            :disabled="checkoutLines.length === 0"
            @click="openCheckout"
          >
            Tiến hành đặt hàng
            <IconArrowRight :size="18" :stroke-width="1.8" aria-hidden="true" />
          </Button>
        </footer>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="checkoutOpen">
      <DialogContent
        class="max-h-[calc(100dvh-1rem)] w-[min(calc(100%-1rem),68rem)] max-w-none overflow-y-auto p-0 sm:max-h-[calc(100dvh-2rem)]"
      >
        <DialogHeader class="sr-only">
          <DialogTitle>Đặt hàng</DialogTitle>
          <DialogDescription>Kiểm tra sản phẩm và nhập thông tin giao hàng.</DialogDescription>
        </DialogHeader>
        <DialogClose as-child>
          <Button
            class="absolute right-3 top-3 z-20 size-10 bg-card/90 p-0"
            variant="outline"
            aria-label="Đóng đặt hàng"
          >
            <IconX :size="20" :stroke-width="1.8" aria-hidden="true" />
          </Button>
        </DialogClose>
        <CheckoutForm
          :lines="checkoutLines"
          :submitting="orderMutation.isPending.value"
          :error="orderError"
          :success-order="createdOrder"
          @submit="submitOrder"
          @update-quantity="updateQuantity"
          @remove="removeFromCart"
          @start-over="startOver"
        />
      </DialogContent>
    </Dialog>
  </div>
</template>
