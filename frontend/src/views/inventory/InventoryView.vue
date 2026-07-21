<script setup lang="ts">
import {
  IconAdjustmentsHorizontal,
  IconAlertTriangle,
  IconBox,
  IconCheck,
  IconRefresh,
} from '@tabler/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { z } from 'zod'
import {
  adjustStock,
  ApiClientError,
  getInventory,
  type InventoryErrorDetails,
  type InventoryItem,
} from '@/api'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

const inventoryKey = ['inventory'] as const
const queryClient = useQueryClient()
const selectedItem = ref<InventoryItem | null>(null)
const delta = ref<string | number>('')
const reason = ref('')
const formErrors = ref<Record<string, string>>({})
const successMessage = ref('')

const adjustmentSchema = z.object({
  delta: z
    .number({
      required_error: 'Enter a whole-number adjustment.',
      invalid_type_error: 'Enter a whole-number adjustment.',
    })
    .int('Enter a whole-number adjustment.')
    .min(-2_147_483_648, 'Adjustment is too small.')
    .max(2_147_483_647, 'Adjustment is too large.')
    .refine((value) => value !== 0, 'Adjustment cannot be zero.'),
  reason: z.string().min(1, 'Reason is required.').max(500, 'Use 500 characters or fewer.'),
})

const inventoryQuery = useQuery({
  queryKey: inventoryKey,
  queryFn: getInventory,
})

const adjustmentMutation = useMutation({
  mutationFn: ({ productId, change }: { productId: number; change: number }) =>
    adjustStock(productId, { delta: change, reason: reason.value.trim() }),
  onSuccess: (updated) => {
    queryClient.setQueryData<InventoryItem[]>(inventoryKey, (items) =>
      items?.map((item) => (item.productId === updated.productId ? updated : item)),
    )
    successMessage.value = `${updated.productName} stock updated.`
    selectedItem.value = null
  },
})

const adjustmentError = computed(() => {
  const error = adjustmentMutation.error.value
  if (error instanceof ApiClientError && isInventoryErrorDetails(error.details)) {
    return error.details
  }
  return null
})

function openAdjustment(item: InventoryItem) {
  selectedItem.value = item
  delta.value = ''
  reason.value = ''
  formErrors.value = {}
  adjustmentMutation.reset()
}

function closeAdjustment() {
  if (!adjustmentMutation.isPending.value) selectedItem.value = null
}

function submitAdjustment() {
  const result = adjustmentSchema.safeParse({
    delta: String(delta.value).trim() === '' ? Number.NaN : Number(delta.value),
    reason: reason.value.trim(),
  })

  if (!result.success) {
    formErrors.value = Object.fromEntries(
      result.error.issues.map((issue) => [String(issue.path[0]), issue.message]),
    )
    return
  }

  formErrors.value = {}
  adjustmentMutation.mutate({
    productId: selectedItem.value!.productId,
    change: result.data.delta,
  })
}

function isInventoryErrorDetails(value: unknown): value is InventoryErrorDetails {
  if (!value || typeof value !== 'object') return false
  const details = value as Partial<InventoryErrorDetails>
  return typeof details.message === 'string' && typeof details.status === 'number'
}
</script>

<template>
  <section class="grid gap-6">
    <header class="flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-end sm:justify-between">
      <div class="space-y-2">
        <p class="text-xs font-bold uppercase text-success">Warehouse operations</p>
        <h1 class="text-3xl font-semibold text-primary">Inventory management</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          Review physical, reserved and available stock before making a counted adjustment.
        </p>
      </div>
      <Button
        variant="outline"
        :disabled="inventoryQuery.isFetching.value"
        @click="inventoryQuery.refetch()"
      >
        <IconRefresh :size="18" :stroke-width="1.8" aria-hidden="true" />
        Refresh
      </Button>
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

    <div v-if="inventoryQuery.isPending.value" class="overflow-hidden rounded-lg border bg-card">
      <div
        v-for="index in 4"
        :key="index"
        class="grid min-h-24 animate-pulse gap-4 border-b p-4 last:border-b-0 lg:grid-cols-5 lg:items-center"
      >
        <div class="h-5 w-40 rounded bg-muted" />
        <div v-for="value in 4" :key="value" class="h-5 w-full rounded bg-muted" />
      </div>
    </div>

    <div
      v-else-if="inventoryQuery.isError.value"
      class="grid min-h-64 place-items-center rounded-lg border border-destructive/30 bg-destructive-muted p-6 text-center"
    >
      <div class="grid max-w-sm justify-items-center gap-3">
        <IconAlertTriangle
          class="text-destructive"
          :size="28"
          :stroke-width="1.8"
          aria-hidden="true"
        />
        <h2 class="text-lg font-semibold">Inventory could not be loaded</h2>
        <p class="text-sm text-muted-foreground">Check the connection and try again.</p>
        <Button variant="outline" @click="inventoryQuery.refetch()">Try again</Button>
      </div>
    </div>

    <div
      v-else-if="inventoryQuery.data.value?.length === 0"
      class="grid min-h-64 place-items-center rounded-lg border bg-card p-6 text-center"
    >
      <div class="grid max-w-sm justify-items-center gap-3">
        <IconBox class="text-muted-foreground" :size="30" :stroke-width="1.8" aria-hidden="true" />
        <h2 class="text-lg font-semibold">No products to manage</h2>
        <p class="text-sm text-muted-foreground">Inventory will appear when products are available.</p>
      </div>
    </div>

    <div v-else class="overflow-hidden rounded-lg border bg-card shadow-sm">
      <div
        class="hidden grid-cols-[minmax(12rem,2fr)_repeat(3,minmax(6rem,1fr))_auto] gap-4 border-b bg-muted/60 px-5 py-3 text-xs font-bold uppercase text-muted-foreground lg:grid"
      >
        <span>Product</span>
        <span>On hand</span>
        <span>Reserved</span>
        <span>Available</span>
        <span class="sr-only">Action</span>
      </div>
      <ul aria-label="Inventory products" class="divide-y">
        <li
          v-for="item in inventoryQuery.data.value"
          :key="item.productId"
          class="grid gap-4 p-5 lg:grid-cols-[minmax(12rem,2fr)_repeat(3,minmax(6rem,1fr))_auto] lg:items-center"
        >
          <div class="min-w-0">
            <p class="truncate font-semibold text-primary">{{ item.productName }}</p>
            <p class="text-xs text-muted-foreground">Product #{{ item.productId }}</p>
          </div>
          <dl class="grid grid-cols-3 gap-3 lg:contents">
            <div>
              <dt class="text-xs text-muted-foreground lg:sr-only">On hand</dt>
              <dd class="mt-1 font-semibold tabular-nums lg:mt-0">{{ item.onHandStock }}</dd>
            </div>
            <div>
              <dt class="text-xs text-muted-foreground lg:sr-only">Reserved</dt>
              <dd class="mt-1 font-semibold tabular-nums lg:mt-0">{{ item.reservedStock }}</dd>
            </div>
            <div>
              <dt class="text-xs text-muted-foreground lg:sr-only">Available</dt>
              <dd
                class="mt-1 font-semibold tabular-nums lg:mt-0"
                :class="item.availableStock > 0 ? 'text-success' : 'text-destructive'"
              >
                {{ item.availableStock }}
              </dd>
            </div>
          </dl>
          <Button class="w-full lg:w-auto" variant="outline" @click="openAdjustment(item)">
            <IconAdjustmentsHorizontal :size="18" :stroke-width="1.8" aria-hidden="true" />
            Adjust
          </Button>
        </li>
      </ul>
    </div>

    <Dialog :open="selectedItem !== null" @update:open="(open) => !open && closeAdjustment()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Adjust {{ selectedItem?.productName }}</DialogTitle>
          <DialogDescription>
            Enter the counted change, not the final stock quantity. Negative values reduce on-hand
            stock.
          </DialogDescription>
        </DialogHeader>

        <div v-if="selectedItem" class="grid grid-cols-3 gap-2 rounded-md bg-muted p-3 text-center">
          <div>
            <p class="text-xs text-muted-foreground">On hand</p>
            <p class="font-semibold tabular-nums">{{ selectedItem.onHandStock }}</p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Reserved</p>
            <p class="font-semibold tabular-nums">{{ selectedItem.reservedStock }}</p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">Available</p>
            <p class="font-semibold tabular-nums">{{ selectedItem.availableStock }}</p>
          </div>
        </div>

        <form class="grid gap-4" @submit.prevent="submitAdjustment">
          <div class="grid gap-1.5">
            <label for="stock-delta" class="text-sm font-medium">Stock change</label>
            <Input
              id="stock-delta"
              v-model="delta"
              type="number"
              step="1"
              inputmode="numeric"
              placeholder="For example: 5 or -2"
              :aria-invalid="Boolean(formErrors.delta || adjustmentError?.fieldErrors?.delta)"
              aria-describedby="stock-delta-error"
            />
            <p
              v-if="formErrors.delta || adjustmentError?.fieldErrors?.delta"
              id="stock-delta-error"
              class="text-sm text-destructive"
            >
              {{ formErrors.delta || adjustmentError?.fieldErrors?.delta }}
            </p>
          </div>

          <div class="grid gap-1.5">
            <label for="stock-reason" class="text-sm font-medium">Reason</label>
            <textarea
              id="stock-reason"
              v-model="reason"
              rows="3"
              maxlength="500"
              class="w-full resize-none rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring"
              placeholder="Why does the counted stock need changing?"
              :aria-invalid="Boolean(formErrors.reason || adjustmentError?.fieldErrors?.reason)"
              aria-describedby="stock-reason-error"
            />
            <p
              v-if="formErrors.reason || adjustmentError?.fieldErrors?.reason"
              id="stock-reason-error"
              class="text-sm text-destructive"
            >
              {{ formErrors.reason || adjustmentError?.fieldErrors?.reason }}
            </p>
          </div>

          <p
            v-if="adjustmentMutation.isError.value && adjustmentError"
            class="rounded-md bg-destructive-muted px-3 py-2 text-sm text-destructive"
            role="alert"
          >
            {{ adjustmentError.message }}
          </p>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              :disabled="adjustmentMutation.isPending.value"
              @click="closeAdjustment"
            >
              Cancel
            </Button>
            <Button type="submit" :disabled="adjustmentMutation.isPending.value">
              {{ adjustmentMutation.isPending.value ? 'Saving…' : 'Save adjustment' }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </section>
</template>
