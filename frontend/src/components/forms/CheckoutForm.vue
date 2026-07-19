<script setup lang="ts">
import { computed } from 'vue'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { z } from 'zod'
import {
  IconAlertTriangle,
  IconArrowUpRight,
  IconMinus,
  IconPlus,
  IconReceipt,
  IconTrash,
} from '@tabler/icons-vue'
import type { OrderErrorDetails, OrderResponse } from '@/api'
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
}>()

const emit = defineEmits<{
  submit: [values: CheckoutValues]
  'update-quantity': [productId: number, quantity: number]
  remove: [productId: number]
  'start-over': []
}>()

const formSchema = toTypedSchema(
  z.object({
    fullName: z.string().trim().min(1, 'Enter the customer name.'),
    email: z.string().trim().email('Enter a valid email.').or(z.literal('')),
    customerPhone: z.string().trim(),
    receiverName: z.string().trim().min(1, 'Enter the receiver name.'),
    receiverPhone: z.string().trim().min(1, 'Enter a receiver phone number.'),
    addressLine: z.string().trim().min(1, 'Enter the delivery address.'),
    district: z.string().trim(),
    city: z.string().trim().min(1, 'Enter the delivery city.'),
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
    return `Only ${insufficient.availableStock} available right now.`
  }
  if (unavailableProductIds.value.has(productId)) {
    return 'This product is no longer available.'
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
          Checkout desk
        </p>
        <h2 id="checkout-title" class="mt-2 text-2xl font-bold tracking-tight">Build your order</h2>
        <p class="mt-1 max-w-xl text-sm text-muted-foreground">
          We lock the current price and stock only when you submit.
        </p>
      </div>
      <div class="rounded-md border border-border bg-card/80 px-3 py-2 text-right">
        <p class="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
          {{ props.lines.length }} {{ props.lines.length === 1 ? 'item' : 'items' }}
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
          {{ props.error.message || 'Order could not be created.' }}
        </p>
        <p class="mt-1 text-sm text-muted-foreground">
          Review the highlighted item and try again. The server is the source of truth for stock.
        </p>
      </div>
    </div>

    <div v-if="props.successOrder" class="grid gap-5 p-5 sm:p-7" aria-live="polite">
      <div class="flex items-start gap-4 rounded-lg border border-success/25 bg-success-muted p-5">
        <div class="grid size-10 shrink-0 place-items-center rounded-full bg-success text-white">
          <IconReceipt :size="21" :stroke-width="1.8" aria-hidden="true" />
        </div>
        <div>
          <p class="text-sm font-bold uppercase tracking-wider text-success">Order received</p>
          <h3 class="mt-1 text-xl font-bold">Order #{{ props.successOrder.id }} is ready</h3>
          <p class="mt-1 text-sm text-muted-foreground">
            Status:
            <span class="font-semibold text-foreground">{{ props.successOrder.status }}</span
            >. Payment is the next step.
          </p>
        </div>
      </div>
      <div class="flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm text-muted-foreground">Your reservation is recorded safely.</p>
        <Button type="button" variant="outline" @click="emit('start-over')">
          Continue shopping
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
            01 / Review
          </p>
          <h3 class="mt-1 text-lg font-bold">Selected products</h3>
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
                  {{ formatCurrency(line.product.price) }} each
                </p>
              </div>
              <button
                type="button"
                class="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-destructive-muted hover:text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                :aria-label="`Remove ${line.product.name}`"
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
                  :aria-label="`Decrease ${line.product.name} quantity`"
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
                  :aria-label="`Increase ${line.product.name} quantity`"
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
          <span class="text-sm text-muted-foreground">Order total</span>
          <span class="text-xl font-bold tabular-nums">{{ formatCurrency(totalAmount) }}</span>
        </div>
      </div>

      <div class="grid content-start gap-5 lg:border-l lg:border-border lg:pl-7">
        <div>
          <p class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            02 / Details
          </p>
          <h3 class="mt-1 text-lg font-bold">Where should we send it?</h3>
        </div>

        <div class="grid gap-4 sm:grid-cols-2">
          <label class="grid gap-1.5 sm:col-span-2">
            <span class="text-sm font-semibold"
              >Customer name <span class="text-destructive">*</span></span
            >
            <Input
              id="checkout-full-name"
              v-model="fullName"
              v-bind="fullNameAttrs"
              autocomplete="name"
              :aria-invalid="Boolean(errors.fullName)"
              placeholder="Nguyen Van A"
            />
            <span v-if="errors.fullName" class="text-xs text-destructive">{{
              errors.fullName
            }}</span>
          </label>
          <label class="grid gap-1.5">
            <span class="text-sm font-semibold"
              >Email <span class="font-normal text-muted-foreground">(optional)</span></span
            >
            <Input
              id="checkout-email"
              v-model="email"
              v-bind="emailAttrs"
              type="email"
              autocomplete="email"
              :aria-invalid="Boolean(errors.email)"
              placeholder="you@example.com"
            />
            <span v-if="errors.email" class="text-xs text-destructive">{{ errors.email }}</span>
          </label>
          <label class="grid gap-1.5">
            <span class="text-sm font-semibold"
              >Customer phone
              <span class="font-normal text-muted-foreground">(optional)</span></span
            >
            <Input
              id="checkout-customer-phone"
              v-model="customerPhone"
              v-bind="customerPhoneAttrs"
              autocomplete="tel"
              placeholder="0901234567"
            />
          </label>
          <label class="grid gap-1.5 sm:col-span-2">
            <span class="text-sm font-semibold"
              >Receiver name <span class="text-destructive">*</span></span
            >
            <Input
              id="checkout-receiver-name"
              v-model="receiverName"
              v-bind="receiverNameAttrs"
              autocomplete="shipping name"
              :aria-invalid="Boolean(errors.receiverName)"
              placeholder="Nguyen Van A"
            />
            <span v-if="errors.receiverName" class="text-xs text-destructive">{{
              errors.receiverName
            }}</span>
          </label>
          <label class="grid gap-1.5">
            <span class="text-sm font-semibold"
              >Receiver phone <span class="text-destructive">*</span></span
            >
            <Input
              id="checkout-receiver-phone"
              v-model="receiverPhone"
              v-bind="receiverPhoneAttrs"
              autocomplete="shipping tel"
              :aria-invalid="Boolean(errors.receiverPhone)"
              placeholder="0901234567"
            />
            <span v-if="errors.receiverPhone" class="text-xs text-destructive">{{
              errors.receiverPhone
            }}</span>
          </label>
          <label class="grid gap-1.5">
            <span class="text-sm font-semibold">City <span class="text-destructive">*</span></span>
            <Input
              id="checkout-city"
              v-model="city"
              v-bind="cityAttrs"
              autocomplete="shipping address-level1"
              :aria-invalid="Boolean(errors.city)"
              placeholder="Ho Chi Minh"
            />
            <span v-if="errors.city" class="text-xs text-destructive">{{ errors.city }}</span>
          </label>
          <label class="grid gap-1.5 sm:col-span-2">
            <span class="text-sm font-semibold"
              >Address line <span class="text-destructive">*</span></span
            >
            <Input
              id="checkout-address"
              v-model="addressLine"
              v-bind="addressLineAttrs"
              autocomplete="shipping street-address"
              :aria-invalid="Boolean(errors.addressLine)"
              placeholder="123 Nguyen Hue"
            />
            <span v-if="errors.addressLine" class="text-xs text-destructive">{{
              errors.addressLine
            }}</span>
          </label>
          <label class="grid gap-1.5 sm:col-span-2">
            <span class="text-sm font-semibold"
              >District <span class="font-normal text-muted-foreground">(optional)</span></span
            >
            <Input
              id="checkout-district"
              v-model="district"
              v-bind="districtAttrs"
              autocomplete="shipping address-level2"
              placeholder="District 1"
            />
          </label>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5">
          <p class="max-w-xs text-xs leading-relaxed text-muted-foreground">
            Payment is simulated in the next step. This checkout accepts CARD only.
          </p>
          <Button
            type="submit"
            size="lg"
            :disabled="props.submitting || hasUnavailableLine || props.lines.length === 0"
          >
            <span v-if="props.submitting">Creating order…</span>
            <span v-else>Place order</span>
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
