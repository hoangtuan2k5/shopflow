<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { z } from 'zod'
import {
  IconAlertTriangle,
  IconArrowUpRight,
  IconCircleCheck,
  IconCircleX,
  IconMinus,
  IconPlus,
  IconReceipt,
  IconTrash,
} from '@tabler/icons-vue'
import type {
  OrderErrorDetails,
  OrderResponse,
  PaymentErrorDetails,
  PaymentResponse,
  SimulatedPaymentResult,
} from '@/api'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

interface CheckoutLine {
  product: {
    id: number
    name: string
    price: number
    stockStatus: 'IN_STOCK' | 'OUT_OF_STOCK'
  }
  quantity: number
}

const props = defineProps<{
  lines: CheckoutLine[]
  submitting: boolean
  error: OrderErrorDetails | null
  successOrder: OrderResponse | null
  paymentSubmitting: boolean
  paymentError: PaymentErrorDetails | null
  paymentResult: PaymentResponse | null
}>()

const emit = defineEmits<{
  submit: [values: CheckoutValues]
  'update-quantity': [productId: number, quantity: number]
  remove: [productId: number]
  'start-over': []
  pay: [result: SimulatedPaymentResult]
}>()

const formSchema = toTypedSchema(
  z.object({
    fullName: z.string().trim().min(1, 'Nhập tên khách hàng.'),
    email: z.string().trim().email('Nhập email hợp lệ.').or(z.literal('')),
    customerPhone: z.string().trim(),
    receiverName: z.string().trim().min(1, 'Nhập tên người nhận.'),
    receiverPhone: z.string().trim().min(1, 'Nhập số điện thoại người nhận.'),
    addressLine: z.string().trim().min(1, 'Nhập địa chỉ giao hàng.'),
    district: z.string().trim(),
    city: z.string().trim().min(1, 'Nhập tỉnh hoặc thành phố giao hàng.'),
  }),
)

type CheckoutValues = {
  fullName: string
  email: string
  customerPhone: string
  receiverName: string
  receiverPhone: string
  addressLine: string
  district: string
  city: string
}

const { defineField, errors, handleSubmit } = useForm<CheckoutValues>({
  validationSchema: formSchema,
  initialValues: {
    fullName: '',
    email: '',
    customerPhone: '',
    receiverName: '',
    receiverPhone: '',
    addressLine: '',
    district: '',
    city: '',
  },
})

const [fullName, fullNameAttrs] = defineField('fullName')
const [email, emailAttrs] = defineField('email')
const [customerPhone, customerPhoneAttrs] = defineField('customerPhone')
const [receiverName, receiverNameAttrs] = defineField('receiverName')
const [receiverPhone, receiverPhoneAttrs] = defineField('receiverPhone')
const [addressLine, addressLineAttrs] = defineField('addressLine')
const [district, districtAttrs] = defineField('district')
const [city, cityAttrs] = defineField('city')
const paymentSimulation = ref<HTMLElement | null>(null)

const displayedErrors = computed(() => ({
  fullName: errors.value.fullName ?? props.error?.fieldErrors?.['customer.fullName'],
  email: errors.value.email ?? props.error?.fieldErrors?.['customer.email'],
  customerPhone: errors.value.customerPhone ?? props.error?.fieldErrors?.['customer.phone'],
  receiverName:
    errors.value.receiverName ?? props.error?.fieldErrors?.['shippingAddress.receiverName'],
  receiverPhone: errors.value.receiverPhone ?? props.error?.fieldErrors?.['shippingAddress.phone'],
  addressLine:
    errors.value.addressLine ?? props.error?.fieldErrors?.['shippingAddress.addressLine'],
  district: errors.value.district ?? props.error?.fieldErrors?.['shippingAddress.district'],
  city: errors.value.city ?? props.error?.fieldErrors?.['shippingAddress.city'],
}))

watch(
  () => props.successOrder,
  async (order) => {
    if (!order) return
    await nextTick()
    paymentSimulation.value?.focus()
  },
)

const currency = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
})

const totalAmount = computed(() =>
  props.lines.reduce((total, line) => total + line.product.price * line.quantity, 0),
)

const insufficientByProduct = computed(
  () => new Map((props.error?.insufficientItems ?? []).map((item) => [item.productId, item])),
)

const unavailableProductIds = computed(() => new Set(props.error?.unavailableProductIds ?? []))

const hasUnavailableLine = computed(() =>
  props.lines.some((line) => line.product.stockStatus === 'OUT_OF_STOCK'),
)

function formatCurrency(value: number) {
  return currency.format(value)
}

function lineError(productId: number) {
  const insufficient = insufficientByProduct.value.get(productId)
  if (insufficient) {
    return `Hiện chỉ còn ${insufficient.availableStock} sản phẩm.`
  }
  if (unavailableProductIds.value.has(productId)) {
    return 'Sản phẩm này không còn khả dụng.'
  }
  return null
}

const submit = handleSubmit((values) => emit('submit', values))
</script>

<template>
  <section
    id="checkout"
    class="overflow-hidden rounded-xl border border-primary/15 bg-card shadow-[0_18px_55px_-35px_rgba(30,42,90,0.55)]"
    aria-labelledby="checkout-title"
  >
    <header
      class="flex flex-wrap items-start justify-between gap-4 border-b border-border bg-gradient-to-br from-primary/[0.07] via-card to-accent/[0.45] px-5 py-5 sm:px-7"
    >
      <div>
        <p
          class="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.16em] text-success"
        >
          <IconReceipt :size="16" :stroke-width="1.8" aria-hidden="true" />
          Đặt hàng
        </p>
        <h2 id="checkout-title" class="mt-2 text-2xl font-bold tracking-tight">
          Hoàn tất đơn hàng
        </h2>
        <p class="mt-1 max-w-xl text-sm text-muted-foreground">
          Giá và tồn kho được kiểm tra lại khi bạn gửi đơn hàng.
        </p>
      </div>
      <div class="rounded-md border border-border bg-card/80 px-3 py-2 text-right">
        <p class="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
          {{ props.lines.length }} sản phẩm
        </p>
        <p class="mt-0.5 text-lg font-bold tabular-nums">{{ formatCurrency(totalAmount) }}</p>
      </div>
    </header>

    <div
      v-if="props.error"
      class="mx-5 mt-5 flex gap-3 border-l-4 border-destructive bg-destructive-muted p-4 sm:mx-7"
      role="alert"
    >
      <IconAlertTriangle
        class="mt-0.5 shrink-0 text-destructive"
        :size="20"
        :stroke-width="1.8"
        aria-hidden="true"
      />
      <div>
        <p class="font-semibold text-foreground">
          {{ props.error.message || 'Không thể tạo đơn hàng.' }}
        </p>
        <p class="mt-1 text-sm text-muted-foreground">
          Kiểm tra trường hoặc sản phẩm được đánh dấu rồi thử lại. Tồn kho trên hệ thống là dữ liệu
          quyết định.
        </p>
      </div>
    </div>

    <div
      v-if="props.successOrder"
      id="payment-simulation"
      ref="paymentSimulation"
      class="grid gap-5 p-5 sm:p-7"
      tabindex="-1"
      aria-labelledby="payment-simulation-title"
      aria-live="polite"
    >
      <div class="flex items-start gap-4 rounded-lg border border-success/25 bg-success-muted p-5">
        <div class="grid size-10 shrink-0 place-items-center rounded-full bg-success text-white">
          <IconReceipt :size="21" :stroke-width="1.8" aria-hidden="true" />
        </div>
        <div>
          <p class="text-sm font-bold uppercase tracking-wider text-success">03 / Thanh toán</p>
          <h3 id="payment-simulation-title" class="mt-1 text-xl font-bold">Mô phỏng thanh toán</h3>
          <p class="mt-1 text-sm text-muted-foreground">
            Đơn hàng #{{ props.successOrder.id }} đã được tạo và sẵn sàng cho bước thanh toán thẻ.
          </p>
        </div>
      </div>
      <dl class="grid gap-3 rounded-lg border border-border bg-background/70 p-4 sm:grid-cols-3">
        <div>
          <dt class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Đơn hàng
          </dt>
          <dd class="mt-1 font-bold">#{{ props.successOrder.id }}</dd>
        </div>
        <div>
          <dt class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Trạng thái
          </dt>
          <dd class="mt-1 font-bold">{{ props.successOrder.status }}</dd>
        </div>
        <div>
          <dt class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            Tổng tiền
          </dt>
          <dd class="mt-1 font-bold tabular-nums">
            {{ formatCurrency(props.successOrder.totalAmount) }}
          </dd>
        </div>
      </dl>
      <div
        v-if="props.paymentResult"
        class="flex items-start gap-4 rounded-lg border p-5"
        :class="
          props.paymentResult.status === 'SUCCESS'
            ? 'border-success/25 bg-success-muted'
            : 'border-destructive/25 bg-destructive-muted'
        "
        role="status"
      >
        <IconCircleCheck
          v-if="props.paymentResult.status === 'SUCCESS'"
          class="mt-0.5 shrink-0 text-success"
          :size="24"
          :stroke-width="1.8"
          aria-hidden="true"
        />
        <IconCircleX
          v-else
          class="mt-0.5 shrink-0 text-destructive"
          :size="24"
          :stroke-width="1.8"
          aria-hidden="true"
        />
        <div>
          <p class="font-bold">
            {{
              props.paymentResult.status === 'SUCCESS'
                ? 'Thanh toán thành công'
                : props.paymentResult.status === 'EXPIRED'
                  ? 'Thanh toán đã hết hạn'
                  : 'Thanh toán thất bại'
            }}
          </p>
          <p class="mt-1 text-sm text-muted-foreground">
            Payment {{ props.paymentResult.status }} · Order {{ props.paymentResult.orderStatus }}
          </p>
          <p v-if="props.paymentResult.failedReason" class="mt-1 text-sm text-muted-foreground">
            {{ props.paymentResult.failedReason }}
          </p>
        </div>
      </div>
      <div v-else class="grid gap-4 rounded-lg border border-border bg-background/70 p-4">
        <div
          v-if="props.paymentError"
          class="flex gap-3 border-l-4 border-destructive bg-destructive-muted p-4"
          role="alert"
        >
          <IconAlertTriangle
            class="mt-0.5 shrink-0 text-destructive"
            :size="20"
            :stroke-width="1.8"
            aria-hidden="true"
          />
          <div>
            <p class="font-semibold">{{ props.paymentError.message || 'Không thể thanh toán.' }}</p>
            <p class="mt-1 text-sm text-muted-foreground">
              Đơn hàng vẫn được giữ để bạn kiểm tra lỗi.
            </p>
          </div>
        </div>
        <div>
          <p class="font-semibold">Chọn kết quả thanh toán mô phỏng</p>
          <p class="mt-1 text-sm text-muted-foreground">
            Đây là môi trường demo; không có giao dịch thẻ thật.
          </p>
        </div>
        <div class="grid gap-2 sm:grid-cols-3">
          <Button type="button" :disabled="props.paymentSubmitting" @click="emit('pay', 'SUCCESS')">
            Thanh toán thành công
          </Button>
          <Button
            type="button"
            variant="outline"
            :disabled="props.paymentSubmitting"
            @click="emit('pay', 'FAILED')"
          >
            Mô phỏng thất bại
          </Button>
          <Button
            type="button"
            variant="outline"
            :disabled="props.paymentSubmitting"
            @click="emit('pay', 'EXPIRED')"
          >
            Mô phỏng hết hạn
          </Button>
        </div>
        <p v-if="props.paymentSubmitting" class="text-sm font-semibold text-primary" role="status">
          Đang xử lý kết quả thanh toán…
        </p>
      </div>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm text-muted-foreground">
          {{
            props.paymentResult
              ? 'Kết quả đã được ghi nhận cho đơn hàng.'
              : 'Tồn kho đã được giữ. Hệ thống chưa ghi nhận thanh toán.'
          }}
        </p>
        <Button type="button" variant="outline" @click="emit('start-over')">
          Tiếp tục mua sắm
          <IconArrowUpRight :size="17" :stroke-width="1.8" aria-hidden="true" />
        </Button>
      </div>
    </div>

    <form
      v-else
      class="grid gap-6 p-5 sm:p-7 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]"
      @submit.prevent="submit"
    >
      <div class="grid content-start gap-4">
        <div>
          <p class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            01 / Kiểm tra
          </p>
          <h3 class="mt-1 text-lg font-bold">Sản phẩm đã chọn</h3>
        </div>

        <div class="grid gap-3">
          <article
            v-for="line in props.lines"
            :key="line.product.id"
            class="rounded-lg border border-border bg-background/70 p-4 transition-colors"
            :class="
              lineError(line.product.id) ? 'border-destructive/45' : 'hover:border-primary/25'
            "
          >
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="truncate font-semibold">{{ line.product.name }}</p>
                <p class="mt-1 text-sm tabular-nums text-muted-foreground">
                  {{ formatCurrency(line.product.price) }} mỗi sản phẩm
                </p>
              </div>
              <button
                type="button"
                class="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-destructive-muted hover:text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                :aria-label="`Xóa ${line.product.name}`"
                @click="emit('remove', line.product.id)"
              >
                <IconTrash :size="17" :stroke-width="1.8" aria-hidden="true" />
              </button>
            </div>
            <div class="mt-4 flex items-end justify-between gap-3">
              <div class="inline-flex items-center rounded-md border border-border bg-card">
                <button
                  type="button"
                  class="grid size-8 place-items-center text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
                  :disabled="line.quantity <= 1"
                  :aria-label="`Giảm số lượng ${line.product.name}`"
                  @click="emit('update-quantity', line.product.id, line.quantity - 1)"
                >
                  <IconMinus :size="15" :stroke-width="2" aria-hidden="true" />
                </button>
                <span class="min-w-8 text-center text-sm font-bold tabular-nums">{{
                  line.quantity
                }}</span>
                <button
                  type="button"
                  class="grid size-8 place-items-center text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
                  :aria-label="`Tăng số lượng ${line.product.name}`"
                  @click="emit('update-quantity', line.product.id, line.quantity + 1)"
                >
                  <IconPlus :size="15" :stroke-width="2" aria-hidden="true" />
                </button>
              </div>
              <p class="font-bold tabular-nums">
                {{ formatCurrency(line.product.price * line.quantity) }}
              </p>
            </div>
            <p
              v-if="lineError(line.product.id)"
              class="mt-3 text-xs font-semibold text-destructive"
            >
              {{ lineError(line.product.id) }}
            </p>
          </article>
        </div>

        <div class="flex items-center justify-between border-t border-border pt-4">
          <span class="text-sm text-muted-foreground">Tổng đơn hàng</span>
          <span class="text-xl font-bold tabular-nums">{{ formatCurrency(totalAmount) }}</span>
        </div>
      </div>

      <div class="grid content-start gap-5 lg:border-l lg:border-border lg:pl-7">
        <div>
          <p class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            02 / Giao hàng
          </p>
          <h3 class="mt-1 text-lg font-bold">Bạn muốn nhận hàng ở đâu?</h3>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <label class="grid gap-1.5 sm:col-span-2">
            <span class="text-sm font-semibold"
              >Tên khách hàng <span class="text-destructive">*</span></span
            >
            <Input
              id="checkout-full-name"
              v-model="fullName"
              v-bind="fullNameAttrs"
              autocomplete="name"
              maxlength="255"
              :aria-invalid="Boolean(displayedErrors.fullName)"
              placeholder="Nguyễn Văn A"
            />
            <span v-if="displayedErrors.fullName" class="text-xs text-destructive">{{
              displayedErrors.fullName
            }}</span>
          </label>
          <label class="grid gap-1.5">
            <span class="text-sm font-semibold"
              >Email <span class="font-normal text-muted-foreground">(không bắt buộc)</span></span
            >
            <Input
              id="checkout-email"
              v-model="email"
              v-bind="emailAttrs"
              type="email"
              autocomplete="email"
              maxlength="255"
              :aria-invalid="Boolean(displayedErrors.email)"
              placeholder="you@example.com"
            />
            <span v-if="displayedErrors.email" class="text-xs text-destructive">{{
              displayedErrors.email
            }}</span>
          </label>
          <label class="grid gap-1.5">
            <span class="text-sm font-semibold"
              >Số điện thoại khách hàng
              <span class="font-normal text-muted-foreground">(không bắt buộc)</span></span
            >
            <Input
              id="checkout-customer-phone"
              v-model="customerPhone"
              v-bind="customerPhoneAttrs"
              autocomplete="tel"
              maxlength="20"
              :aria-invalid="Boolean(displayedErrors.customerPhone)"
              placeholder="0901234567"
            />
            <span v-if="displayedErrors.customerPhone" class="text-xs text-destructive">{{
              displayedErrors.customerPhone
            }}</span>
          </label>
          <label class="grid gap-1.5 sm:col-span-2">
            <span class="text-sm font-semibold"
              >Tên người nhận <span class="text-destructive">*</span></span
            >
            <Input
              id="checkout-receiver-name"
              v-model="receiverName"
              v-bind="receiverNameAttrs"
              autocomplete="shipping name"
              maxlength="255"
              :aria-invalid="Boolean(displayedErrors.receiverName)"
              placeholder="Nguyễn Văn A"
            />
            <span v-if="displayedErrors.receiverName" class="text-xs text-destructive">{{
              displayedErrors.receiverName
            }}</span>
          </label>
          <label class="grid gap-1.5">
            <span class="text-sm font-semibold"
              >Số điện thoại người nhận <span class="text-destructive">*</span></span
            >
            <Input
              id="checkout-receiver-phone"
              v-model="receiverPhone"
              v-bind="receiverPhoneAttrs"
              autocomplete="shipping tel"
              maxlength="20"
              :aria-invalid="Boolean(displayedErrors.receiverPhone)"
              placeholder="0901234567"
            />
            <span v-if="displayedErrors.receiverPhone" class="text-xs text-destructive">{{
              displayedErrors.receiverPhone
            }}</span>
          </label>
          <label class="grid gap-1.5">
            <span class="text-sm font-semibold"
              >Tỉnh/Thành phố <span class="text-destructive">*</span></span
            >
            <Input
              id="checkout-city"
              v-model="city"
              v-bind="cityAttrs"
              autocomplete="shipping address-level1"
              maxlength="100"
              :aria-invalid="Boolean(displayedErrors.city)"
              placeholder="Thành phố Hồ Chí Minh"
            />
            <span v-if="displayedErrors.city" class="text-xs text-destructive">{{
              displayedErrors.city
            }}</span>
          </label>
          <label class="grid gap-1.5 sm:col-span-2">
            <span class="text-sm font-semibold"
              >Địa chỉ <span class="text-destructive">*</span></span
            >
            <Input
              id="checkout-address"
              v-model="addressLine"
              v-bind="addressLineAttrs"
              autocomplete="shipping street-address"
              maxlength="500"
              :aria-invalid="Boolean(displayedErrors.addressLine)"
              placeholder="123 Nguyễn Huệ"
            />
            <span v-if="displayedErrors.addressLine" class="text-xs text-destructive">{{
              displayedErrors.addressLine
            }}</span>
          </label>
          <label class="grid gap-1.5 sm:col-span-2">
            <span class="text-sm font-semibold"
              >Quận/Huyện
              <span class="font-normal text-muted-foreground">(không bắt buộc)</span></span
            >
            <Input
              id="checkout-district"
              v-model="district"
              v-bind="districtAttrs"
              autocomplete="shipping address-level2"
              maxlength="100"
              :aria-invalid="Boolean(displayedErrors.district)"
              placeholder="Quận 1"
            />
            <span v-if="displayedErrors.district" class="text-xs text-destructive">{{
              displayedErrors.district
            }}</span>
          </label>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5">
          <p class="max-w-xs text-xs leading-relaxed text-muted-foreground">
            Thanh toán được mô phỏng ở bước tiếp theo. Đơn hàng hiện chỉ hỗ trợ thẻ.
          </p>
          <Button
            type="submit"
            size="lg"
            :disabled="props.submitting || hasUnavailableLine || props.lines.length === 0"
          >
            <span v-if="props.submitting">Đang tạo đơn hàng…</span>
            <span v-else>Đặt hàng</span>
            <IconArrowUpRight
              v-if="!props.submitting"
              :size="18"
              :stroke-width="1.8"
              aria-hidden="true"
            />
          </Button>
        </div>
      </div>
    </form>
  </section>
</template>
