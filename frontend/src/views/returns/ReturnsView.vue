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

const route = useRoute()
const queryClient = useQueryClient()
const isWarehouse = computed(() => route.meta.role === 'warehouse')
const roleLabel = computed(() => (isWarehouse.value ? 'Warehouse operations' : 'Shop owner'))
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

const statusLabels: Record<ReturnStatus, string> = {
  REQUESTED: 'Requested',
  APPROVED: 'Approved',
  RESTOCKED: 'Restocked',
  REJECTED: 'Rejected',
}

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

const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' })

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
    ordersQuery.data.value?.find((order) => String(order.orderId) === selectedOrderId.value) ?? null,
)

watch(selectedOrderId, () => {
  quantities.value = {}
  createFormErrors.value = {}
})

const createMutation = useMutation({
  mutationFn: createReturn,
  onSuccess: async (created) => {
    successMessage.value = `Return #${created.id} requested for order #${created.orderId}.`
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
    successMessage.value = `Return #${updated.id} is now ${statusLabels[updated.status].toLowerCase()}.`
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
  return error instanceof Error ? error.message : 'Return could not be saved. Try again.'
})

const updateError = computed(() => {
  const error = updateMutation.error.value
  if (error instanceof ApiClientError && isReturnErrorDetails(error.details)) {
    return error.details.message
  }
  return error ? 'Return could not be updated. Try again.' : ''
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
    createFormErrors.value = { order: 'Choose a delivered order.' }
    return
  }

  const errors: Record<string, string> = {}
  const items: Array<{ orderItemId: number; quantity: number }> = []
  for (const item of order.items) {
    const raw = String(quantities.value[item.orderItemId] ?? '').trim()
    if (raw === '') continue
    const quantity = Number(raw)
    if (!Number.isInteger(quantity) || quantity < 0) {
      errors[`quantity-${item.orderItemId}`] = 'Enter a whole-number quantity.'
      continue
    }
    if (quantity === 0) continue
    if (quantity > item.returnableQuantity) {
      errors[`quantity-${item.orderItemId}`] = `At most ${item.returnableQuantity} can be returned.`
      continue
    }
    items.push({ orderItemId: item.orderItemId, quantity })
  }

  const reasonText = returnReason.value.trim()
  if (reasonText.length > 500) {
    errors.reason = 'Use 500 characters or fewer.'
  }
  if (Object.keys(errors).length === 0 && items.length === 0) {
    errors.items = 'Enter a quantity for at least one item.'
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
  return dateTime.format(new Date(value))
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
        <h1 class="text-3xl font-semibold text-primary">Return management</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          Register returns for delivered orders, review each request and restock approved items.
        </p>
      </div>
      <div class="flex flex-wrap gap-2">
        <RouterLink
          v-if="isWarehouse"
          to="/warehouse"
          :class="buttonVariants({ variant: 'outline' })"
        >
          <IconBox :size="18" :stroke-width="1.8" aria-hidden="true" />
          Manage inventory
        </RouterLink>
        <RouterLink :to="deliveriesPath" :class="buttonVariants({ variant: 'outline' })">
          <IconTruckDelivery :size="18" :stroke-width="1.8" aria-hidden="true" />
          Manage deliveries
        </RouterLink>
        <Button
          variant="outline"
          :disabled="returnsQuery.isFetching.value"
          @click="returnsQuery.refetch()"
        >
          <IconRefresh :size="18" :stroke-width="1.8" aria-hidden="true" />
          Refresh
        </Button>
        <Button @click="openCreate">
          <IconArrowBackUp :size="18" :stroke-width="1.8" aria-hidden="true" />
          New return
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
      aria-label="Loading returns"
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
        <h2 class="text-lg font-semibold">Returns could not be loaded</h2>
        <p class="text-sm text-muted-foreground">Check the connection and try again.</p>
        <Button variant="outline" @click="returnsQuery.refetch()">Try again</Button>
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
        <h2 class="text-lg font-semibold">No return requests</h2>
        <p class="text-sm text-muted-foreground">
          Returns will appear once a delivered order is registered for return.
        </p>
      </div>
    </div>

    <ul v-else aria-label="Return requests" class="grid gap-4">
      <li
        v-for="item in returnsQuery.data.value"
        :key="item.id"
        class="grid gap-4 rounded-lg border bg-card p-5 shadow-sm"
      >
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="min-w-0">
            <p class="font-semibold text-primary">Return #{{ item.id }} · Order #{{ item.orderId }}</p>
            <p class="text-xs text-muted-foreground">Requested {{ formatDate(item.createdAt) }}</p>
          </div>
          <span
            class="rounded-full px-3 py-1 text-xs font-semibold"
            :class="statusClasses[item.status]"
          >
            {{ statusLabels[item.status] }}
          </span>
        </div>

        <ul class="grid gap-1 text-sm" :aria-label="`Return #${item.id} items`">
          <li
            v-for="returnItem in item.items"
            :key="returnItem.orderItemId"
            class="flex items-center justify-between gap-3"
          >
            <span class="truncate">{{ returnItem.productName }}</span>
            <span class="font-semibold tabular-nums">× {{ returnItem.quantity }}</span>
          </li>
        </ul>

        <p v-if="item.reason" class="text-sm text-muted-foreground">Reason: {{ item.reason }}</p>
        <p
          v-if="item.status === 'APPROVED' && !item.restockable"
          class="text-sm text-muted-foreground"
        >
          Approved without restocking. Inventory stays unchanged.
        </p>

        <div
          v-if="item.status === 'REQUESTED' || (item.status === 'APPROVED' && item.restockable)"
          class="flex flex-wrap gap-2"
        >
          <Button v-if="item.status === 'REQUESTED'" variant="outline" @click="openDecision(item)">
            Review request
          </Button>
          <Button
            v-if="isWarehouse && item.status === 'APPROVED' && item.restockable"
            @click="openRestock(item)"
          >
            Restock items
          </Button>
          <p
            v-if="!isWarehouse && item.status === 'APPROVED' && item.restockable"
            class="self-center text-sm text-muted-foreground"
          >
            Waiting for warehouse restock.
          </p>
        </div>
      </li>
    </ul>

    <Dialog :open="createOpen" @update:open="(open) => !open && closeCreate()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New return request</DialogTitle>
          <DialogDescription>
            Choose a delivered order and enter the quantity to return for each item.
          </DialogDescription>
        </DialogHeader>

        <form class="grid gap-4" novalidate @submit.prevent="submitCreate">
          <div class="grid gap-1.5">
            <label for="return-order" class="text-sm font-medium">Delivered order</label>
            <select
              id="return-order"
              v-model="selectedOrderId"
              class="h-9 w-full rounded-md border border-input bg-background px-3 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring"
              :aria-invalid="Boolean(createFormErrors.order)"
              aria-describedby="return-order-error"
            >
              <option value="" disabled>Select an order</option>
              <option
                v-for="order in ordersQuery.data.value"
                :key="order.orderId"
                :value="String(order.orderId)"
              >
                Order #{{ order.orderId }} · {{ order.receiverName }} ·
                {{ currency.format(order.totalAmount) }}
              </option>
            </select>
            <p v-if="createFormErrors.order" id="return-order-error" class="text-sm text-destructive">
              {{ createFormErrors.order }}
            </p>
            <p
              v-if="!ordersQuery.isPending.value && ordersQuery.data.value?.length === 0"
              class="text-sm text-muted-foreground"
            >
              No delivered orders are available for return yet.
            </p>
          </div>

          <div v-if="selectedOrder" class="grid gap-3">
            <p class="text-sm font-medium">Items to return</p>
            <div
              v-for="item in selectedOrder.items"
              :key="item.orderItemId"
              class="grid gap-1.5 rounded-md border border-border p-3"
            >
              <div class="flex items-center justify-between gap-3 text-sm">
                <span class="truncate font-medium">{{ item.productName }}</span>
                <span class="text-muted-foreground">
                  {{ item.returnableQuantity }} of {{ item.quantity }} returnable
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
                :aria-label="`Quantity for ${item.productName}`"
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
            <label for="return-reason" class="text-sm font-medium">Reason (optional)</label>
            <textarea
              id="return-reason"
              v-model="returnReason"
              rows="3"
              class="w-full resize-none rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring"
              placeholder="Why is the customer returning these items?"
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
              Cancel
            </Button>
            <Button type="submit" :disabled="createMutation.isPending.value">
              {{ createMutation.isPending.value ? 'Requesting…' : 'Request return' }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog :open="decideTarget !== null" @update:open="(open) => !open && closeDecision()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Review return #{{ decideTarget?.id }}</DialogTitle>
          <DialogDescription>
            Approve the request after inspecting the items, or reject it. Approving requires a
            restock decision; inventory only changes after a warehouse restock confirmation.
          </DialogDescription>
        </DialogHeader>

        <fieldset class="grid gap-2">
          <legend class="text-sm font-medium">Can the items go back to stock?</legend>
          <label class="flex items-center gap-2 text-sm">
            <input v-model="decideRestockable" type="radio" value="yes" name="restockable" />
            Yes, items are restockable
          </label>
          <label class="flex items-center gap-2 text-sm">
            <input v-model="decideRestockable" type="radio" value="no" name="restockable" />
            No, items cannot be resold
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
            Cancel
          </Button>
          <Button
            type="button"
            variant="destructive"
            :disabled="updateMutation.isPending.value"
            @click="reject"
          >
            Reject
          </Button>
          <Button type="button" :disabled="updateMutation.isPending.value" @click="approve">
            {{ updateMutation.isPending.value ? 'Saving…' : 'Approve' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="restockTarget !== null" @update:open="(open) => !open && closeRestock()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Restock return #{{ restockTarget?.id }}</DialogTitle>
          <DialogDescription>
            Confirm the items are back in the warehouse. On-hand stock increases for every returned
            item and each movement is recorded.
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
            Cancel
          </Button>
          <Button type="button" :disabled="updateMutation.isPending.value" @click="confirmRestock">
            {{ updateMutation.isPending.value ? 'Restocking…' : 'Confirm restock' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </section>
</template>
