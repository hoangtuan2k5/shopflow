<script setup lang="ts">
import {
  IconAlertTriangle,
  IconArrowRight,
  IconBox,
  IconCheck,
  IconClock,
  IconPackageExport,
  IconRefresh,
  IconTruckDelivery,
} from '@tabler/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  ApiClientError,
  getDeliveries,
  type DeliveryErrorDetails,
  type DeliveryOrder,
  type DeliveryStatus,
  updateDelivery,
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

type DeliveryFilter = 'active' | 'delivered' | 'all'

const deliveryKey = ['deliveries'] as const
const stages: DeliveryStatus[] = ['NONE', 'PREPARING', 'SHIPPED', 'DELIVERED']
const filters: { value: DeliveryFilter; label: string }[] = [
  { value: 'active', label: 'Active' },
  { value: 'delivered', label: 'Delivered' },
  { value: 'all', label: 'All paid' },
]
const statusLabels: Record<DeliveryStatus, string> = {
  NONE: 'Ready to prepare',
  PREPARING: 'Preparing',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
}
const nextStatuses: Partial<Record<DeliveryStatus, DeliveryStatus>> = {
  NONE: 'PREPARING',
  PREPARING: 'SHIPPED',
  SHIPPED: 'DELIVERED',
}
const actionLabels: Partial<Record<DeliveryStatus, string>> = {
  PREPARING: 'Start preparing',
  SHIPPED: 'Mark as shipped',
  DELIVERED: 'Complete delivery',
}

const route = useRoute()
const queryClient = useQueryClient()
const selectedOrder = ref<DeliveryOrder | null>(null)
const activeFilter = ref<DeliveryFilter>('active')
const successMessage = ref('')
const isWarehouse = computed(() => route.meta.role === 'warehouse')
const roleLabel = computed(() => (isWarehouse.value ? 'Warehouse operations' : 'Shop owner'))

const deliveryQuery = useQuery({
  queryKey: deliveryKey,
  queryFn: getDeliveries,
})

const transitionMutation = useMutation({
  mutationFn: ({ orderId, toStatus }: { orderId: number; toStatus: DeliveryStatus }) =>
    updateDelivery(orderId, toStatus),
  onSuccess: (updated) => {
    queryClient.setQueryData<DeliveryOrder[]>(deliveryKey, (orders) =>
      orders?.map((order) => (order.orderId === updated.orderId ? updated : order)),
    )
    successMessage.value = `Order #${updated.orderId} is now ${statusLabels[updated.deliveryStatus].toLowerCase()}.`
    selectedOrder.value = null
  },
  onError: () => queryClient.invalidateQueries({ queryKey: deliveryKey }),
})

const visibleOrders = computed(() => {
  const orders = deliveryQuery.data.value ?? []
  if (activeFilter.value === 'active') {
    return orders.filter((order) => order.deliveryStatus !== 'DELIVERED')
  }
  if (activeFilter.value === 'delivered') {
    return orders.filter((order) => order.deliveryStatus === 'DELIVERED')
  }
  return orders
})

const transitionError = computed(() => {
  const error = transitionMutation.error.value
  if (error instanceof ApiClientError && isDeliveryErrorDetails(error.details)) {
    return error.details.message
  }
  return error ? 'Delivery status could not be updated. Try again.' : ''
})

const currency = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})
const dateTime = new Intl.DateTimeFormat('en-GB', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function countFor(filter: DeliveryFilter) {
  const orders = deliveryQuery.data.value ?? []
  if (filter === 'active') return orders.filter((order) => order.deliveryStatus !== 'DELIVERED').length
  if (filter === 'delivered') return orders.filter((order) => order.deliveryStatus === 'DELIVERED').length
  return orders.length
}

function nextStatus(order: DeliveryOrder) {
  return nextStatuses[order.deliveryStatus]
}

function openTransition(order: DeliveryOrder) {
  selectedOrder.value = order
  transitionMutation.reset()
}

function closeTransition() {
  if (!transitionMutation.isPending.value) selectedOrder.value = null
}

function confirmTransition() {
  const order = selectedOrder.value
  const toStatus = order && nextStatus(order)
  if (!order || !toStatus) return
  transitionMutation.mutate({ orderId: order.orderId, toStatus })
}

function stageReached(order: DeliveryOrder, stage: DeliveryStatus) {
  return stages.indexOf(stage) <= stages.indexOf(order.deliveryStatus)
}

function statusClass(status: DeliveryStatus) {
  if (status === 'DELIVERED') return 'bg-success-muted text-success border-success/20'
  if (status === 'SHIPPED') return 'bg-primary/10 text-primary border-primary/15'
  if (status === 'PREPARING') return 'bg-amber-50 text-amber-800 border-amber-200'
  return 'bg-muted text-muted-foreground border-border'
}

function formatDate(value: string) {
  return dateTime.format(new Date(value))
}

function isDeliveryErrorDetails(value: unknown): value is DeliveryErrorDetails {
  if (!value || typeof value !== 'object') return false
  const details = value as Partial<DeliveryErrorDetails>
  return typeof details.message === 'string' && typeof details.status === 'number'
}
</script>

<template>
  <section class="grid gap-6">
    <header
      class="flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-end sm:justify-between"
    >
      <div class="space-y-2">
        <p class="text-xs font-bold uppercase tracking-[0.14em] text-success">{{ roleLabel }}</p>
        <h1 class="text-3xl font-semibold text-primary">Delivery management</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          Move paid orders through fulfillment in sequence and review every recorded handoff.
        </p>
      </div>
      <div class="flex flex-wrap gap-2">
        <RouterLink
          v-if="isWarehouse"
          to="/warehouse"
          :class="buttonVariants({ variant: 'outline' })"
        >
          <IconBox :size="18" :stroke-width="1.8" aria-hidden="true" />
          Inventory
        </RouterLink>
        <Button
          variant="outline"
          :disabled="deliveryQuery.isFetching.value"
          @click="deliveryQuery.refetch()"
        >
          <IconRefresh :size="18" :stroke-width="1.8" aria-hidden="true" />
          Refresh
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
      class="overflow-hidden rounded-xl border border-primary/15 bg-gradient-to-br from-primary/[0.07] via-card to-accent/50 p-1 shadow-[0_18px_55px_-42px_rgba(30,42,90,0.65)]"
    >
      <div class="grid gap-3 rounded-lg bg-card/80 p-4 sm:grid-cols-3">
        <button
          v-for="filter in filters"
          :key="filter.value"
          type="button"
          class="flex items-center justify-between rounded-lg border px-4 py-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          :class="
            activeFilter === filter.value
              ? 'border-primary bg-primary text-primary-foreground'
              : 'border-border bg-card hover:border-primary/35'
          "
          :aria-pressed="activeFilter === filter.value"
          @click="activeFilter = filter.value"
        >
          <span class="text-sm font-semibold">{{ filter.label }}</span>
          <span class="text-lg font-bold tabular-nums">{{ countFor(filter.value) }}</span>
        </button>
      </div>
    </div>

    <div v-if="deliveryQuery.isPending.value" class="grid gap-4" aria-label="Loading deliveries">
      <div
        v-for="index in 3"
        :key="index"
        class="h-64 animate-pulse rounded-xl border bg-card p-5"
      >
        <div class="h-5 w-32 rounded bg-muted" />
        <div class="mt-6 h-10 w-full rounded bg-muted" />
        <div class="mt-6 h-24 w-full rounded bg-muted" />
      </div>
    </div>

    <div
      v-else-if="deliveryQuery.isError.value"
      class="grid min-h-64 place-items-center rounded-xl border border-destructive/30 bg-destructive-muted p-6 text-center"
    >
      <div class="grid max-w-sm justify-items-center gap-3">
        <IconAlertTriangle class="text-destructive" :size="30" :stroke-width="1.8" aria-hidden="true" />
        <h2 class="text-lg font-semibold">Deliveries could not be loaded</h2>
        <p class="text-sm text-muted-foreground">Check the connection and try again.</p>
        <Button variant="outline" @click="deliveryQuery.refetch()">Try again</Button>
      </div>
    </div>

    <div
      v-else-if="visibleOrders.length === 0"
      class="grid min-h-64 place-items-center rounded-xl border bg-card p-6 text-center"
    >
      <div class="grid max-w-sm justify-items-center gap-3">
        <IconPackageExport class="text-muted-foreground" :size="32" :stroke-width="1.7" aria-hidden="true" />
        <h2 class="text-lg font-semibold">No deliveries in this view</h2>
        <p class="text-sm text-muted-foreground">
          Paid orders will appear here when they are ready for fulfillment.
        </p>
      </div>
    </div>

    <ul v-else class="grid gap-4" aria-label="Paid delivery orders">
      <li
        v-for="order in visibleOrders"
        :key="order.orderId"
        class="overflow-hidden rounded-xl border bg-card shadow-[0_12px_35px_-30px_rgba(30,42,90,0.8)]"
      >
        <div class="flex flex-wrap items-start justify-between gap-3 border-b bg-muted/35 px-5 py-4">
          <div>
            <div class="flex flex-wrap items-center gap-2">
              <h2 class="text-lg font-semibold text-primary">Order #{{ order.orderId }}</h2>
              <span class="rounded-full border border-success/20 bg-success-muted px-2 py-0.5 text-xs font-bold text-success">
                PAID
              </span>
              <span
                class="rounded-full border px-2 py-0.5 text-xs font-bold"
                :class="statusClass(order.deliveryStatus)"
              >
                {{ statusLabels[order.deliveryStatus] }}
              </span>
            </div>
            <p class="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground">
              <IconClock :size="14" :stroke-width="1.8" aria-hidden="true" />
              Created {{ formatDate(order.createdAt) }}
            </p>
          </div>
          <p class="text-lg font-bold tabular-nums text-primary">
            {{ currency.format(order.totalAmount) }}
          </p>
        </div>

        <div class="grid gap-6 p-5 lg:grid-cols-[minmax(0,1fr)_18rem]">
          <div class="grid gap-5">
            <dl class="grid gap-3 sm:grid-cols-2">
              <div class="rounded-lg border bg-background px-4 py-3">
                <dt class="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Receiver</dt>
                <dd class="mt-1 font-semibold">{{ order.receiverName }}</dd>
                <dd class="text-sm text-muted-foreground">{{ order.city }}</dd>
              </div>
              <div class="rounded-lg border bg-background px-4 py-3">
                <dt class="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Items</dt>
                <dd class="mt-1 space-y-1 text-sm">
                  <span v-for="item in order.items" :key="item.productId" class="flex justify-between gap-3">
                    <span class="truncate">{{ item.productName }}</span>
                    <span class="shrink-0 font-semibold tabular-nums">× {{ item.quantity }}</span>
                  </span>
                </dd>
              </div>
            </dl>

            <div aria-label="Delivery progress">
              <ol class="grid grid-cols-2 gap-y-4 sm:grid-cols-4">
                <li v-for="(stage, index) in stages" :key="stage" class="relative text-center">
                  <div
                    v-if="index > 0"
                    class="absolute right-1/2 top-3 hidden h-0.5 w-full sm:block"
                    :class="stageReached(order, stage) ? 'bg-brand' : 'bg-border'"
                  />
                  <span
                    class="relative mx-auto grid size-6 place-items-center rounded-full border-2 bg-card text-[0.65rem] font-bold"
                    :class="
                      stageReached(order, stage)
                        ? 'border-brand text-success'
                        : 'border-border text-muted-foreground'
                    "
                  >
                    <IconCheck v-if="stageReached(order, stage)" :size="13" :stroke-width="2.2" aria-hidden="true" />
                    <span v-else>{{ index + 1 }}</span>
                  </span>
                  <span class="mt-2 block text-xs font-semibold text-muted-foreground">
                    {{ statusLabels[stage] }}
                  </span>
                </li>
              </ol>
            </div>

            <details class="group rounded-lg border bg-background px-4 py-3">
              <summary class="cursor-pointer text-sm font-semibold text-primary">
                Status history · {{ order.history.length }} events
              </summary>
              <ol v-if="order.history.length" class="mt-4 grid gap-3 border-l-2 border-brand/25 pl-4">
                <li v-for="(event, index) in order.history" :key="`${event.changedAt}-${index}`" class="text-sm">
                  <p class="font-semibold">
                    {{ statusLabels[event.fromStatus] }}
                    <IconArrowRight class="mx-1 inline" :size="14" aria-hidden="true" />
                    {{ statusLabels[event.toStatus] }}
                  </p>
                  <p class="text-xs text-muted-foreground">{{ formatDate(event.changedAt) }}</p>
                </li>
              </ol>
              <p v-else class="mt-3 text-sm text-muted-foreground">No transitions recorded yet.</p>
            </details>
          </div>

          <aside class="flex flex-col justify-between gap-4 rounded-lg bg-primary px-5 py-5 text-primary-foreground">
            <div>
              <IconTruckDelivery :size="28" :stroke-width="1.7" aria-hidden="true" />
              <p class="mt-4 text-xs font-bold uppercase tracking-[0.14em] text-primary-foreground/65">
                Next handoff
              </p>
              <p class="mt-1 text-xl font-semibold">
                {{ nextStatus(order) ? statusLabels[nextStatus(order)!] : 'Fulfillment complete' }}
              </p>
              <p class="mt-2 text-sm text-primary-foreground/70">
                {{
                  nextStatus(order)
                    ? 'Confirm this handoff when the physical order is ready.'
                    : 'This order has completed the delivery lifecycle.'
                }}
              </p>
            </div>
            <Button
              v-if="nextStatus(order)"
              class="w-full bg-brand text-primary hover:bg-brand/90"
              @click="openTransition(order)"
            >
              {{ actionLabels[nextStatus(order)!] }}
              <IconArrowRight :size="18" :stroke-width="1.8" aria-hidden="true" />
            </Button>
            <p v-else class="flex items-center gap-2 text-sm font-semibold text-brand">
              <IconCheck :size="18" :stroke-width="2" aria-hidden="true" />
              Delivered
            </p>
          </aside>
        </div>
      </li>
    </ul>

    <Dialog :open="selectedOrder !== null" @update:open="(open) => !open && closeTransition()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Confirm order #{{ selectedOrder?.orderId }}</DialogTitle>
          <DialogDescription v-if="selectedOrder && nextStatus(selectedOrder)">
            Move from {{ statusLabels[selectedOrder.deliveryStatus] }} to
            {{ statusLabels[nextStatus(selectedOrder)!] }}. This transition cannot be reversed.
          </DialogDescription>
        </DialogHeader>

        <div
          v-if="selectedOrder && nextStatus(selectedOrder) === 'DELIVERED'"
          class="flex gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
        >
          <IconAlertTriangle class="mt-0.5 shrink-0" :size="20" :stroke-width="1.8" aria-hidden="true" />
          Completing delivery reduces on-hand and reserved stock for every item in this order.
        </div>

        <p
          v-if="transitionError"
          class="rounded-md bg-destructive-muted px-3 py-2 text-sm text-destructive"
          role="alert"
        >
          {{ transitionError }}
        </p>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            :disabled="transitionMutation.isPending.value"
            @click="closeTransition"
          >
            Cancel
          </Button>
          <Button
            type="button"
            :disabled="transitionMutation.isPending.value"
            @click="confirmTransition"
          >
            {{ transitionMutation.isPending.value ? 'Updating…' : 'Confirm transition' }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </section>
</template>
