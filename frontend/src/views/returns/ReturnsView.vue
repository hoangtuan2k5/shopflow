<script setup lang="ts">
import {
  IconAlertTriangle,
  IconArrowBackUp,
  IconBox,
  IconCheck,
  IconRefresh,
  IconTruckDelivery,
} from '@tabler/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'
import {
  ApiClientError,
  createReturn,
  getReturnableOrders,
  getReturns,
  updateReturn,
  type ReturnErrorDetails,
  type ReturnRequest,
  type ReturnStatus,
  type UpdateReturnRequest,
} from '@/api'
import { Button, buttonVariants } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

const returnsKey = ['returns'] as const
const returnableOrdersKey = ['returnable-orders'] as const
const inventoryKey = ['inventory'] as const

const { t, locale } = useI18n()
const route = useRoute()
const queryClient = useQueryClient()
const isWarehouse = computed(() => route.meta.role === 'warehouse')
const roleLabel = computed(() =>
  isWarehouse.value ? t('common.warehouseOperations') : t('common.shopOwner'),
)
const deliveriesPath = computed(() => (isWarehouse.value ? '/warehouse/deliveries' : '/shop-owner'))

const createOpen = ref(false)
const selectedOrderId = ref('')
const quantities = ref<Record<number, string | number>>({})
const returnReason = ref('')
const createFormErrors = ref<Record<string, string>>({})
const decideTarget = ref<ReturnRequest | null>(null)
const decideRestockable = ref<'yes' | 'no'>('yes')
const restockTarget = ref<ReturnRequest | null>(null)
const successMessage = ref('')

const statusLabels = computed<Record<ReturnStatus, string>>(() => ({
  REQUESTED: t('returns.status.REQUESTED'),
  APPROVED: t('returns.status.APPROVED'),
  RESTOCKED: t('returns.status.RESTOCKED'),
  REJECTED: t('returns.status.REJECTED'),
}))

const statusClasses: Record<ReturnStatus, string> = {
  REQUESTED: 'bg-muted text-muted-foreground',
  APPROVED: 'bg-success-muted text-success',
  RESTOCKED: 'bg-success-muted text-success',
  REJECTED: 'bg-destructive-muted text-destructive',
}

const currency = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})

const dateTime = computed(
  () =>
    new Intl.DateTimeFormat(locale.value === 'vi' ? 'vi-VN' : 'en-GB', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }),
)

const returnsQuery = useQuery({
  queryKey: returnsKey,
  queryFn: getReturns,
})

const ordersQuery = useQuery({
  queryKey: returnableOrdersKey,
  queryFn: getReturnableOrders,
})

const selectedOrder = computed(
  () =>
    ordersQuery.data.value?.find((order) => String(order.orderId) === selectedOrderId.value) ??
    null,
)

watch(selectedOrderId, () => {
  quantities.value = {}
  createFormErrors.value = {}
})

const createMutation = useMutation({
  mutationFn: createReturn,
  onSuccess: async (created) => {
    successMessage.value = t('returns.successCreated', {
      id: created.id,
      orderId: created.orderId,
    })
    closeCreate(true)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: returnsKey }),
      queryClient.invalidateQueries({ queryKey: returnableOrdersKey }),
    ])
  },
})

const updateMutation = useMutation({
  mutationFn: ({ returnId, update }: { returnId: number; update: UpdateReturnRequest }) =>
    updateReturn(returnId, update),
  onSuccess: async (updated) => {
    queryClient.setQueryData<ReturnRequest[]>(returnsKey, (returns) =>
      returns?.map((item) => (item.id === updated.id ? updated : item)),
    )
    successMessage.value = t('returns.successUpdated', {
      id: updated.id,
      status: statusLabels.value[updated.status].toLocaleLowerCase(),
    })
    decideTarget.value = null
    restockTarget.value = null
    await queryClient.invalidateQueries({ queryKey: returnableOrdersKey })
    if (updated.status === 'RESTOCKED') {
      await queryClient.invalidateQueries({ queryKey: inventoryKey })
    }
  },
  onError: () => queryClient.invalidateQueries({ queryKey: returnsKey }),
})

const createError = computed(() => {
  const error = createMutation.error.value
  if (error instanceof ApiClientError && isReturnErrorDetails(error.details)) {
    return error.details
  }
  return null
})

const createErrorMessage = computed(() => {
  if (createError.value) return createError.value.message
  const error = createMutation.error.value
  return error instanceof Error ? error.message : t('returns.createDialog.fallbackError')
})

const updateError = computed(() => {
  const error = updateMutation.error.value
  if (error instanceof ApiClientError && isReturnErrorDetails(error.details)) {
    return error.details.message
  }
  return error ? t('returns.fallbackUpdateError') : ''
})

function openCreate() {
  createOpen.value = true
  selectedOrderId.value = ''
  quantities.value = {}
  returnReason.value = ''
  createFormErrors.value = {}
  createMutation.reset()
}

function closeCreate(force = false) {
  if (force || !createMutation.isPending.value) createOpen.value = false
}

function openDecision(item: ReturnRequest) {
  decideTarget.value = item
  decideRestockable.value = 'yes'
  updateMutation.reset()
}

function closeDecision() {
  if (!updateMutation.isPending.value) decideTarget.value = null
}

function openRestock(item: ReturnRequest) {
  restockTarget.value = item
  updateMutation.reset()
}

function closeRestock() {
  if (!updateMutation.isPending.value) restockTarget.value = null
}

function submitCreate() {
  const order = selectedOrder.value
  if (!order) {
    createFormErrors.value = { order: t('returns.form.chooseOrder') }
    return
  }

  const errors: Record<string, string> = {}
  const items: Array<{ orderItemId: number; quantity: number }> = []
  for (const item of order.items) {
    const raw = String(quantities.value[item.orderItemId] ?? '').trim()
    if (raw === '') continue
    const quantity = Number(raw)
    if (!Number.isInteger(quantity) || quantity < 0) {
      errors[`quantity-${item.orderItemId}`] = t('returns.form.wholeQuantity')
      continue
    }
    if (quantity === 0) continue
    if (quantity > item.returnableQuantity) {
      errors[`quantity-${item.orderItemId}`] = t('returns.form.atMost', {
        max: item.returnableQuantity,
      })
      continue
    }
    items.push({ orderItemId: item.orderItemId, quantity })
  }

  const reasonText = returnReason.value.trim()
  if (reasonText.length > 500) {
    errors.reason = t('common.max500')
  }
  if (Object.keys(errors).length === 0 && items.length === 0) {
    errors.items = t('returns.form.atLeastOne')
  }
  if (Object.keys(errors).length > 0) {
    createFormErrors.value = errors
    return
  }

  createFormErrors.value = {}
  createMutation.mutate({
    orderId: order.orderId,
    reason: reasonText === '' ? null : reasonText,
    items,
  })
}

function approve() {
  if (!decideTarget.value) return
  updateMutation.mutate({
    returnId: decideTarget.value.id,
    update: { toStatus: 'APPROVED', restockable: decideRestockable.value === 'yes' },
  })
}

function reject() {
  if (!decideTarget.value) return
  updateMutation.mutate({ returnId: decideTarget.value.id, update: { toStatus: 'REJECTED' } })
}

function confirmRestock() {
  if (!restockTarget.value) return
  updateMutation.mutate({ returnId: restockTarget.value.id, update: { toStatus: 'RESTOCKED' } })
}

function formatDate(value: string) {
  return dateTime.value.format(new Date(value))
}

function isReturnErrorDetails(value: unknown): value is ReturnErrorDetails {
  if (!value || typeof value !== 'object') return false
  const details = value as Partial<ReturnErrorDetails>
  return typeof details.message === 'string' && typeof details.status === 'number'
}
</script>

<template>
  <section class="grid gap-6">
    <header
      class="flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-end sm:justify-between"
    >
      <div class="space-y-2">
        <p class="text-xs font-bold uppercase text-success">{{ roleLabel }}</p>
        <h1 class="text-3xl font-semibold text-primary">{{ t('returns.title') }}</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          {{ t('returns.subtitle') }}
        </p>
      </div>
      <div class="flex flex-wrap gap-2">
        <RouterLink
          v-if="isWarehouse"
          to="/warehouse"
          :class="buttonVariants({ variant: 'outline' })"
        >
          <IconBox :size="18" :stroke-width="1.8" aria-hidden="true" />
          {{ t('common.manageInventory') }}
        </RouterLink>
        <RouterLink :to="deliveriesPath" :class="buttonVariants({ variant: 'outline' })">
          <IconTruckDelivery :size="18" :stroke-width="1.8" aria-hidden="true" />
          {{ t('common.manageDeliveries') }}
        </RouterLink>
        <Button
          variant="outline"
          :disabled="returnsQuery.isFetching.value"
          @click="returnsQuery.refetch()"
        >
          <IconRefresh :size="18" :stroke-width="1.8" aria-hidden="true" />
          {{ t('common.refresh') }}
        </Button>
        <Button @click="openCreate">
          <IconArrowBackUp :size="18" :stroke-width="1.8" aria-hidden="true" />
          {{ t('returns.newReturn') }}
        </Button>
      </div>
    </header>

    <p
      v-if="successMessage"
      class="flex items-center gap-2 rounded-md border border-brand/30 bg-success-muted px-4 py-3 text-sm text-success"
      role="status"
      aria-live="polite"
    >
      <IconCheck :size="18" :stroke-width="1.8" aria-hidden="true" />
      {{ successMessage }}
    </p>

    <div
      v-if="returnsQuery.isPending.value"
      :aria-label="t('returns.loadingLabel')"
      class="overflow-hidden rounded-lg border bg-card"
    >
      <div
        v-for="index in 4"
        :key="index"
        class="grid min-h-24 animate-pulse gap-4 border-b p-4 last:border-b-0"
      >
        <div class="h-5 w-40 rounded bg-muted" />
        <div class="h-5 w-full rounded bg-muted" />
      </div>
    </div>

    <div
      v-else-if="returnsQuery.isError.value"
      class="grid min-h-64 place-items-center rounded-lg border border-destructive/30 bg-destructive-muted p-6 text-center"
    >
      <div class="grid max-w-sm justify-items-center gap-3">
        <IconAlertTriangle
          class="text-destructive"
          :size="28"
          :stroke-width="1.8"
          aria-hidden="true"
        />
        <h2 class="text-lg font-semibold">{{ t('returns.loadErrorTitle') }}</h2>
        <p class="text-sm text-muted-foreground">{{ t('common.checkConnection') }}</p>
        <Button variant="outline" @click="returnsQuery.refetch()">
          {{ t('common.tryAgain') }}
        </Button>
      </div>
    </div>

    <div
      v-else-if="returnsQuery.data.value?.length === 0"
      class="grid min-h-64 place-items-center rounded-lg border bg-card p-6 text-center"
    >
      <div class="grid max-w-sm justify-items-center gap-3">
        <IconArrowBackUp
          class="text-muted-foreground"
          :size="30"
          :stroke-width="1.8"
          aria-hidden="true"
        />
        <h2 class="text-lg font-semibold">{{ t('returns.emptyTitle') }}</h2>
        <p class="text-sm text-muted-foreground">
          {{ t('returns.emptyHint') }}
        </p>
      </div>
    </div>

    <ul v-else :aria-label="t('returns.listLabel')" class="grid gap-4">
      <li
        v-for="item in returnsQuery.data.value"
        :key="item.id"
        class="grid gap-4 rounded-lg border bg-card p-5 shadow-sm"
      >
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="min-w-0">
            <p class="font-semibold text-primary">
              {{ t('returns.itemTitle', { id: item.id, orderId: item.orderId }) }}
            </p>
            <p class="text-xs text-muted-foreground">
              {{ t('returns.requestedAt', { date: formatDate(item.createdAt) }) }}
            </p>
          </div>
          <span
            class="rounded-full px-3 py-1 text-xs font-semibold"
            :class="statusClasses[item.status]"
          >
            {{ statusLabels[item.status] }}
          </span>
        </div>

        <ul class="grid gap-1 text-sm" :aria-label="t('returns.itemsLabel', { id: item.id })">
          <li
            v-for="returnItem in item.items"
            :key="returnItem.orderItemId"
            class="flex items-center justify-between gap-3"
          >
            <span class="truncate">{{ returnItem.productName }}</span>
            <span class="font-semibold tabular-nums">× {{ returnItem.quantity }}</span>
          </li>
        </ul>

        <p v-if="item.reason" class="text-sm text-muted-foreground">
          {{ t('returns.reason', { reason: item.reason }) }}
        </p>
        <p
          v-if="item.status === 'APPROVED' && !item.restockable"
          class="text-sm text-muted-foreground"
        >
          {{ t('returns.approvedNoRestock') }}
        </p>

        <div
          v-if="item.status === 'REQUESTED' || (item.status === 'APPROVED' && item.restockable)"
          class="flex flex-wrap gap-2"
        >
          <Button v-if="item.status === 'REQUESTED'" variant="outline" @click="openDecision(item)">
            {{ t('returns.reviewRequest') }}
          </Button>
          <Button
            v-if="isWarehouse && item.status === 'APPROVED' && item.restockable"
            @click="openRestock(item)"
          >
            {{ t('returns.restockItems') }}
          </Button>
          <p
            v-if="!isWarehouse && item.status === 'APPROVED' && item.restockable"
            class="self-center text-sm text-muted-foreground"
          >
            {{ t('returns.waitingRestock') }}
          </p>
        </div>
      </li>
    </ul>

    <Dialog :open="createOpen" @update:open="(open) => !open && closeCreate()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ t('returns.createDialog.title') }}</DialogTitle>
          <DialogDescription>
            {{ t('returns.createDialog.description') }}
          </DialogDescription>
        </DialogHeader>

        <form class="grid gap-4" novalidate @submit.prevent="submitCreate">
          <div class="grid gap-1.5">
            <label for="return-order" class="text-sm font-medium">
              {{ t('returns.createDialog.orderLabel') }}
            </label>
            <select
              id="return-order"
              v-model="selectedOrderId"
              class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring"
              :aria-invalid="Boolean(createFormErrors.order)"
              aria-describedby="return-order-error"
            >
              <option value="" disabled>{{ t('returns.createDialog.orderPlaceholder') }}</option>
              <option
                v-for="order in ordersQuery.data.value"
                :key="order.orderId"
                :value="String(order.orderId)"
              >
                {{
                  t('returns.createDialog.orderOption', {
                    orderId: order.orderId,
                    receiver: order.receiverName,
                    total: currency.format(order.totalAmount),
                  })
                }}
              </option>
            </select>
            <p
              v-if="createFormErrors.order"
              id="return-order-error"
              class="text-sm text-destructive"
            >
              {{ createFormErrors.order }}
            </p>
            <p
              v-if="!ordersQuery.isPending.value && ordersQuery.data.value?.length === 0"
              class="text-sm text-muted-foreground"
            >
              {{ t('returns.createDialog.noOrders') }}
            </p>
          </div>

          <div v-if="selectedOrder" class="grid gap-3">
            <p class="text-sm font-medium">{{ t('returns.createDialog.itemsTitle') }}</p>
            <div
              v-for="item in selectedOrder.items"
              :key="item.orderItemId"
              class="grid gap-1.5 rounded-md border border-border p-3"
            >
              <div class="flex items-center justify-between gap-3 text-sm">
                <span class="truncate font-medium">{{ item.productName }}</span>
                <span class="text-muted-foreground">
                  {{
                    t('returns.createDialog.returnable', {
                      returnable: item.returnableQuantity,
                      quantity: item.quantity,
                    })
                  }}
                </span>
              </div>
              <Input
                v-model="quantities[item.orderItemId]"
                type="number"
                step="1"
                inputmode="numeric"
                min="0"
                :max="item.returnableQuantity"
                :disabled="item.returnableQuantity === 0"
                placeholder="0"
                :aria-label="t('returns.createDialog.quantityFor', { product: item.productName })"
                :aria-invalid="Boolean(createFormErrors[`quantity-${item.orderItemId}`])"
              />
              <p
                v-if="createFormErrors[`quantity-${item.orderItemId}`]"
                class="text-sm text-destructive"
              >
                {{ createFormErrors[`quantity-${item.orderItemId}`] }}
              </p>
            </div>
            <p v-if="createFormErrors.items" class="text-sm text-destructive">
              {{ createFormErrors.items }}
            </p>
          </div>

          <div class="grid gap-1.5">
            <label for="return-reason" class="text-sm font-medium">
              {{ t('returns.createDialog.reasonLabel') }}
            </label>
            <textarea
              id="return-reason"
              v-model="returnReason"
              rows="3"
              class="w-full resize-none rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring"
              :placeholder="t('returns.createDialog.reasonPlaceholder')"
              :aria-invalid="Boolean(createFormErrors.reason || createError?.fieldErrors?.reason)"
              aria-describedby="return-reason-error"
            />
            <p
              v-if="createFormErrors.reason || createError?.fieldErrors?.reason"
              id="return-reason-error"
              class="text-sm text-destructive"
            >
              {{ createFormErrors.reason || createError?.fieldErrors?.reason }}
            </p>
          </div>

          <p
            v-if="createMutation.isError.value"
            class="rounded-md bg-destructive-muted px-3 py-2 text-sm text-destructive"
            role="alert"
          >
            {{ createErrorMessage }}
          </p>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              :disabled="createMutation.isPending.value"
              @click="closeCreate()"
            >
              {{ t('common.cancel') }}
            </Button>
            <Button type="submit" :disabled="createMutation.isPending.value">
              {{
                createMutation.isPending.value
                  ? t('returns.createDialog.submitting')
                  : t('returns.createDialog.submit')
              }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog :open="decideTarget !== null" @update:open="(open) => !open && closeDecision()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ t('returns.decideDialog.title', { id: decideTarget?.id }) }}</DialogTitle>
          <DialogDescription>
            {{ t('returns.decideDialog.description') }}
          </DialogDescription>
        </DialogHeader>

        <fieldset class="grid gap-2">
          <legend class="text-sm font-medium">
            {{ t('returns.decideDialog.restockQuestion') }}
          </legend>
          <label class="flex items-center gap-2 text-sm">
            <input v-model="decideRestockable" type="radio" value="yes" name="restockable" />
            {{ t('returns.decideDialog.restockYes') }}
          </label>
          <label class="flex items-center gap-2 text-sm">
            <input v-model="decideRestockable" type="radio" value="no" name="restockable" />
            {{ t('returns.decideDialog.restockNo') }}
          </label>
        </fieldset>

        <p
          v-if="updateMutation.isError.value"
          class="rounded-md bg-destructive-muted px-3 py-2 text-sm text-destructive"
          role="alert"
        >
          {{ updateError }}
        </p>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            :disabled="updateMutation.isPending.value"
            @click="closeDecision"
          >
            {{ t('common.cancel') }}
          </Button>
          <Button
            type="button"
            variant="destructive"
            :disabled="updateMutation.isPending.value"
            @click="reject"
          >
            {{ t('returns.decideDialog.reject') }}
          </Button>
          <Button type="button" :disabled="updateMutation.isPending.value" @click="approve">
            {{
              updateMutation.isPending.value
                ? t('common.saving')
                : t('returns.decideDialog.approve')
            }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="restockTarget !== null" @update:open="(open) => !open && closeRestock()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{
            t('returns.restockDialog.title', { id: restockTarget?.id })
          }}</DialogTitle>
          <DialogDescription>
            {{ t('returns.restockDialog.description') }}
          </DialogDescription>
        </DialogHeader>

        <ul class="grid gap-1 text-sm">
          <li
            v-for="item in restockTarget?.items"
            :key="item.orderItemId"
            class="flex items-center justify-between gap-3"
          >
            <span class="truncate">{{ item.productName }}</span>
            <span class="font-semibold tabular-nums">+ {{ item.quantity }}</span>
          </li>
        </ul>

        <p
          v-if="updateMutation.isError.value"
          class="rounded-md bg-destructive-muted px-3 py-2 text-sm text-destructive"
          role="alert"
        >
          {{ updateError }}
        </p>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            :disabled="updateMutation.isPending.value"
            @click="closeRestock"
          >
            {{ t('common.cancel') }}
          </Button>
          <Button type="button" :disabled="updateMutation.isPending.value" @click="confirmRestock">
            {{
              updateMutation.isPending.value
                ? t('returns.restockDialog.submitting')
                : t('returns.restockDialog.confirm')
            }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </section>
</template>
