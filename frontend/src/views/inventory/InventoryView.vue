<script setup lang="ts">
import {
  IconAdjustmentsHorizontal,
  IconAlertTriangle,
  IconArrowBackUp,
  IconBell,
  IconBox,
  IconCheck,
  IconRefresh,
  IconTruckDelivery,
} from '@tabler/icons-vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { z } from 'zod'
import {
  adjustStock,
  ApiClientError,
  createReceiving,
  getInventory,
  updateLowStockThreshold,
  type InventoryErrorDetails,
  type InventoryItem,
  type ReceivingErrorDetails,
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

const { t } = useI18n()
const inventoryKey = ['inventory'] as const
const queryClient = useQueryClient()
const selectedItem = ref<InventoryItem | null>(null)
const receivingItem = ref<InventoryItem | null>(null)
const delta = ref<string | number>('')
const reason = ref('')
const formErrors = ref<Record<string, string>>({})
const receivingQuantity = ref<string | number>('')
const supplierName = ref('')
const receivingNote = ref('')
const receivingFormErrors = ref<Record<string, string>>({})
const thresholdItem = ref<InventoryItem | null>(null)
const thresholdValue = ref<string | number>('')
const thresholdFormErrors = ref<Record<string, string>>({})
const successMessage = ref('')

function adjustmentSchema() {
  return z.object({
    delta: z
      .number({
        required_error: t('inventory.form.deltaInvalid'),
        invalid_type_error: t('inventory.form.deltaInvalid'),
      })
      .int(t('inventory.form.deltaInvalid'))
      .min(-2_147_483_648, t('inventory.form.deltaTooSmall'))
      .max(2_147_483_647, t('inventory.form.deltaTooLarge'))
      .refine((value) => value !== 0, t('inventory.form.deltaZero')),
    reason: z.string().min(1, t('inventory.form.reasonRequired')).max(500, t('common.max500')),
  })
}

function receivingSchema() {
  return z.object({
    quantity: z
      .number({
        required_error: t('inventory.form.quantityInvalid'),
        invalid_type_error: t('inventory.form.quantityInvalid'),
      })
      .int(t('inventory.form.quantityInvalid'))
      .min(1, t('inventory.form.quantityMin'))
      .max(2_147_483_647, t('inventory.form.quantityMax')),
    supplierName: z
      .string()
      .refine((value) => value === '' || value.trim().length > 0, t('inventory.form.supplierBlank'))
      .refine((value) => value.trim().length <= 255, t('inventory.form.supplierMax')),
    note: z
      .string()
      .refine((value) => value === '' || value.trim().length > 0, t('inventory.form.noteBlank'))
      .refine((value) => value.trim().length <= 500, t('common.max500')),
  })
}

function thresholdSchema() {
  return z
    .number({
      invalid_type_error: t('inventory.form.thresholdInvalid'),
    })
    .int(t('inventory.form.thresholdInvalid'))
    .min(0, t('inventory.form.thresholdNegative'))
    .max(2_147_483_647, t('inventory.form.thresholdMax'))
    .nullable()
}

const inventoryQuery = useQuery({
  queryKey: inventoryKey,
  queryFn: getInventory,
})

const lowStockCount = computed(
  () => inventoryQuery.data.value?.filter((item) => item.lowStock).length ?? 0,
)

const adjustmentMutation = useMutation({
  mutationFn: ({ productId, change }: { productId: number; change: number }) =>
    adjustStock(productId, { delta: change, reason: reason.value.trim() }),
  onSuccess: (updated) => {
    queryClient.setQueryData<InventoryItem[]>(inventoryKey, (items) =>
      items?.map((item) => (item.productId === updated.productId ? updated : item)),
    )
    successMessage.value = t('inventory.successAdjusted', { product: updated.productName })
    selectedItem.value = null
  },
})

const receivingMutation = useMutation({
  mutationFn: ({
    productId,
    quantity,
    supplierName,
    note,
  }: {
    productId: number
    quantity: number
    supplierName: string | null
    note: string | null
  }) => createReceiving({ productId, quantity, supplierName, note }),
  onSuccess: async (received) => {
    queryClient.setQueryData<InventoryItem[]>(inventoryKey, (items) =>
      items?.map((item) =>
        item.productId === received.productId
          ? {
              ...item,
              onHandStock: received.onHandStock,
              reservedStock: received.reservedStock,
              availableStock: received.availableStock,
              lowStock:
                item.lowStockThreshold !== null &&
                received.availableStock <= item.lowStockThreshold,
            }
          : item,
      ),
    )
    successMessage.value = t('inventory.successReceived', {
      quantity: received.quantity,
      product: received.productName,
    })
    receivingItem.value = null
    await queryClient.invalidateQueries({ queryKey: inventoryKey })
  },
})

const thresholdMutation = useMutation({
  mutationFn: ({ productId, threshold }: { productId: number; threshold: number | null }) =>
    updateLowStockThreshold(productId, { lowStockThreshold: threshold }),
  onSuccess: (updated) => {
    queryClient.setQueryData<InventoryItem[]>(inventoryKey, (items) =>
      items?.map((item) => (item.productId === updated.productId ? updated : item)),
    )
    successMessage.value =
      updated.lowStockThreshold === null
        ? t('inventory.successAlertRemoved', { product: updated.productName })
        : t('inventory.successAlertSet', {
            product: updated.productName,
            threshold: updated.lowStockThreshold,
          })
    thresholdItem.value = null
  },
})

const adjustmentError = computed(() => {
  const error = adjustmentMutation.error.value
  if (error instanceof ApiClientError && isInventoryErrorDetails(error.details)) {
    return error.details
  }
  return null
})

const thresholdError = computed(() => {
  const error = thresholdMutation.error.value
  if (error instanceof ApiClientError && isInventoryErrorDetails(error.details)) {
    return error.details
  }
  return null
})

const receivingError = computed(() => {
  const error = receivingMutation.error.value
  if (error instanceof ApiClientError && isReceivingErrorDetails(error.details)) {
    return error.details
  }
  return null
})

const receivingErrorMessage = computed(() => {
  if (receivingError.value) return receivingError.value.message
  const error = receivingMutation.error.value
  return error instanceof Error ? error.message : t('inventory.receivingDialog.fallbackError')
})

const thresholdErrorMessage = computed(() => {
  if (thresholdError.value) return thresholdError.value.message
  const error = thresholdMutation.error.value
  return error instanceof Error ? error.message : t('inventory.thresholdDialog.fallbackError')
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

function openReceiving(item: InventoryItem) {
  receivingItem.value = item
  receivingQuantity.value = ''
  supplierName.value = ''
  receivingNote.value = ''
  receivingFormErrors.value = {}
  receivingMutation.reset()
}

function closeReceiving() {
  if (!receivingMutation.isPending.value) receivingItem.value = null
}

function openThreshold(item: InventoryItem) {
  thresholdItem.value = item
  thresholdValue.value = item.lowStockThreshold ?? ''
  thresholdFormErrors.value = {}
  thresholdMutation.reset()
}

function closeThreshold() {
  if (!thresholdMutation.isPending.value) thresholdItem.value = null
}

function submitThreshold() {
  const raw = String(thresholdValue.value).trim()
  const result = thresholdSchema().safeParse(raw === '' ? null : Number(raw))

  if (!result.success) {
    thresholdFormErrors.value = { lowStockThreshold: result.error.issues[0]!.message }
    return
  }

  thresholdFormErrors.value = {}
  thresholdMutation.mutate({
    productId: thresholdItem.value!.productId,
    threshold: result.data,
  })
}

function submitAdjustment() {
  const result = adjustmentSchema().safeParse({
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

function submitReceiving() {
  const result = receivingSchema().safeParse({
    quantity:
      String(receivingQuantity.value).trim() === '' ? Number.NaN : Number(receivingQuantity.value),
    supplierName: supplierName.value,
    note: receivingNote.value,
  })

  if (!result.success) {
    receivingFormErrors.value = Object.fromEntries(
      result.error.issues.map((issue) => [String(issue.path[0]), issue.message]),
    )
    return
  }

  receivingFormErrors.value = {}
  receivingMutation.mutate({
    productId: receivingItem.value!.productId,
    quantity: result.data.quantity,
    supplierName: result.data.supplierName === '' ? null : result.data.supplierName.trim(),
    note: result.data.note === '' ? null : result.data.note.trim(),
  })
}

function isInventoryErrorDetails(value: unknown): value is InventoryErrorDetails {
  if (!value || typeof value !== 'object') return false
  const details = value as Partial<InventoryErrorDetails>
  return typeof details.message === 'string' && typeof details.status === 'number'
}

function isReceivingErrorDetails(value: unknown): value is ReceivingErrorDetails {
  if (!value || typeof value !== 'object') return false
  const details = value as Partial<ReceivingErrorDetails>
  return typeof details.message === 'string' && typeof details.status === 'number'
}
</script>

<template>
  <section class="grid gap-6">
    <header
      class="flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-end sm:justify-between"
    >
      <div class="space-y-2">
        <p class="text-xs font-bold uppercase text-success">
          {{ t('common.warehouseOperations') }}
        </p>
        <h1 class="text-3xl font-semibold text-primary">{{ t('inventory.title') }}</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">
          {{ t('inventory.subtitle') }}
        </p>
      </div>
      <div class="flex flex-wrap gap-2">
        <RouterLink to="/warehouse/deliveries" :class="buttonVariants({ variant: 'outline' })">
          <IconTruckDelivery :size="18" :stroke-width="1.8" aria-hidden="true" />
          {{ t('common.manageDeliveries') }}
        </RouterLink>
        <RouterLink to="/warehouse/returns" :class="buttonVariants({ variant: 'outline' })">
          <IconArrowBackUp :size="18" :stroke-width="1.8" aria-hidden="true" />
          {{ t('common.manageReturns') }}
        </RouterLink>
        <Button
          variant="outline"
          :disabled="inventoryQuery.isFetching.value"
          @click="inventoryQuery.refetch()"
        >
          <IconRefresh :size="18" :stroke-width="1.8" aria-hidden="true" />
          {{ t('common.refresh') }}
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

    <p
      v-if="lowStockCount > 0"
      class="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive-muted px-4 py-3 text-sm text-destructive"
      role="alert"
    >
      <IconBell :size="18" :stroke-width="1.8" aria-hidden="true" />
      {{ t('inventory.lowStockBanner', lowStockCount) }}
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
        <h2 class="text-lg font-semibold">{{ t('inventory.loadErrorTitle') }}</h2>
        <p class="text-sm text-muted-foreground">{{ t('common.checkConnection') }}</p>
        <Button variant="outline" @click="inventoryQuery.refetch()">
          {{ t('common.tryAgain') }}
        </Button>
      </div>
    </div>

    <div
      v-else-if="inventoryQuery.data.value?.length === 0"
      class="grid min-h-64 place-items-center rounded-lg border bg-card p-6 text-center"
    >
      <div class="grid max-w-sm justify-items-center gap-3">
        <IconBox class="text-muted-foreground" :size="30" :stroke-width="1.8" aria-hidden="true" />
        <h2 class="text-lg font-semibold">{{ t('inventory.emptyTitle') }}</h2>
        <p class="text-sm text-muted-foreground">{{ t('inventory.emptyHint') }}</p>
      </div>
    </div>

    <div v-else class="overflow-hidden rounded-lg border bg-card shadow-sm">
      <div
        class="hidden grid-cols-[minmax(12rem,2fr)_repeat(3,minmax(6rem,1fr))_auto] gap-4 border-b bg-muted/60 px-5 py-3 text-xs font-bold uppercase text-muted-foreground lg:grid"
      >
        <span>{{ t('inventory.columns.product') }}</span>
        <span>{{ t('inventory.columns.onHand') }}</span>
        <span>{{ t('inventory.columns.reserved') }}</span>
        <span>{{ t('inventory.columns.available') }}</span>
        <span class="sr-only">{{ t('inventory.columns.action') }}</span>
      </div>
      <ul :aria-label="t('inventory.listLabel')" class="divide-y">
        <li
          v-for="item in inventoryQuery.data.value"
          :key="item.productId"
          class="grid gap-4 p-5 lg:grid-cols-[minmax(12rem,2fr)_repeat(3,minmax(6rem,1fr))_auto] lg:items-center"
        >
          <div class="min-w-0">
            <p class="flex items-center gap-2 font-semibold text-primary">
              <span class="truncate">{{ item.productName }}</span>
              <span
                v-if="item.lowStock"
                class="shrink-0 rounded-full bg-destructive-muted px-2 py-0.5 text-xs font-semibold text-destructive"
              >
                {{ t('inventory.lowStockBadge') }}
              </span>
            </p>
            <p class="text-xs text-muted-foreground">
              {{ t('inventory.productLine', { id: item.productId })
              }}<template v-if="item.lowStockThreshold !== null">
                · {{ t('inventory.alertsAt', { threshold: item.lowStockThreshold }) }}</template
              >
            </p>
          </div>
          <dl class="grid grid-cols-3 gap-3 lg:contents">
            <div>
              <dt class="text-xs text-muted-foreground lg:sr-only">
                {{ t('inventory.columns.onHand') }}
              </dt>
              <dd class="mt-1 font-semibold tabular-nums lg:mt-0">{{ item.onHandStock }}</dd>
            </div>
            <div>
              <dt class="text-xs text-muted-foreground lg:sr-only">
                {{ t('inventory.columns.reserved') }}
              </dt>
              <dd class="mt-1 font-semibold tabular-nums lg:mt-0">{{ item.reservedStock }}</dd>
            </div>
            <div>
              <dt class="text-xs text-muted-foreground lg:sr-only">
                {{ t('inventory.columns.available') }}
              </dt>
              <dd
                class="mt-1 font-semibold tabular-nums lg:mt-0"
                :class="item.availableStock > 0 ? 'text-success' : 'text-destructive'"
              >
                {{ item.availableStock }}
              </dd>
            </div>
          </dl>
          <div class="grid gap-2 sm:grid-cols-2 lg:flex">
            <Button class="w-full lg:w-auto" @click="openReceiving(item)">
              <IconBox :size="18" :stroke-width="1.8" aria-hidden="true" />
              {{ t('inventory.receiveStock') }}
            </Button>
            <Button class="w-full lg:w-auto" variant="outline" @click="openAdjustment(item)">
              <IconAdjustmentsHorizontal :size="18" :stroke-width="1.8" aria-hidden="true" />
              {{ t('inventory.adjust') }}
            </Button>
            <Button class="w-full lg:w-auto" variant="outline" @click="openThreshold(item)">
              <IconBell :size="18" :stroke-width="1.8" aria-hidden="true" />
              {{ t('inventory.setAlert') }}
            </Button>
          </div>
        </li>
      </ul>
    </div>

    <Dialog :open="selectedItem !== null" @update:open="(open) => !open && closeAdjustment()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {{ t('inventory.adjustDialog.title', { product: selectedItem?.productName }) }}
          </DialogTitle>
          <DialogDescription>
            {{ t('inventory.adjustDialog.description') }}
          </DialogDescription>
        </DialogHeader>

        <div v-if="selectedItem" class="grid grid-cols-3 gap-2 rounded-md bg-muted p-3 text-center">
          <div>
            <p class="text-xs text-muted-foreground">{{ t('inventory.columns.onHand') }}</p>
            <p class="font-semibold tabular-nums">{{ selectedItem.onHandStock }}</p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">{{ t('inventory.columns.reserved') }}</p>
            <p class="font-semibold tabular-nums">{{ selectedItem.reservedStock }}</p>
          </div>
          <div>
            <p class="text-xs text-muted-foreground">{{ t('inventory.columns.available') }}</p>
            <p class="font-semibold tabular-nums">{{ selectedItem.availableStock }}</p>
          </div>
        </div>

        <form class="grid gap-4" @submit.prevent="submitAdjustment">
          <div class="grid gap-1.5">
            <label for="stock-delta" class="text-sm font-medium">
              {{ t('inventory.adjustDialog.deltaLabel') }}
            </label>
            <Input
              id="stock-delta"
              v-model="delta"
              type="number"
              step="1"
              inputmode="numeric"
              :placeholder="t('inventory.adjustDialog.deltaPlaceholder')"
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
            <label for="stock-reason" class="text-sm font-medium">
              {{ t('inventory.adjustDialog.reasonLabel') }}
            </label>
            <textarea
              id="stock-reason"
              v-model="reason"
              rows="3"
              maxlength="500"
              class="w-full resize-none rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring"
              :placeholder="t('inventory.adjustDialog.reasonPlaceholder')"
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
              {{ t('common.cancel') }}
            </Button>
            <Button type="submit" :disabled="adjustmentMutation.isPending.value">
              {{
                adjustmentMutation.isPending.value
                  ? t('common.saving')
                  : t('inventory.adjustDialog.submit')
              }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog :open="receivingItem !== null" @update:open="(open) => !open && closeReceiving()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {{ t('inventory.receivingDialog.title', { product: receivingItem?.productName }) }}
          </DialogTitle>
          <DialogDescription>
            {{ t('inventory.receivingDialog.description') }}
          </DialogDescription>
        </DialogHeader>

        <form class="grid gap-4" novalidate @submit.prevent="submitReceiving">
          <div class="grid gap-1.5">
            <label for="receiving-quantity" class="text-sm font-medium">
              {{ t('inventory.receivingDialog.quantityLabel') }}
            </label>
            <Input
              id="receiving-quantity"
              v-model="receivingQuantity"
              type="number"
              step="1"
              inputmode="numeric"
              :placeholder="t('inventory.receivingDialog.quantityPlaceholder')"
              :aria-invalid="
                Boolean(receivingFormErrors.quantity || receivingError?.fieldErrors?.quantity)
              "
              aria-describedby="receiving-quantity-error"
            />
            <p
              v-if="receivingFormErrors.quantity || receivingError?.fieldErrors?.quantity"
              id="receiving-quantity-error"
              class="text-sm text-destructive"
            >
              {{ receivingFormErrors.quantity || receivingError?.fieldErrors?.quantity }}
            </p>
          </div>

          <div class="grid gap-1.5">
            <label for="receiving-supplier" class="text-sm font-medium">
              {{ t('inventory.receivingDialog.supplierLabel') }}
            </label>
            <Input
              id="receiving-supplier"
              v-model="supplierName"
              :placeholder="t('inventory.receivingDialog.supplierPlaceholder')"
              :aria-invalid="
                Boolean(
                  receivingFormErrors.supplierName || receivingError?.fieldErrors?.supplierName,
                )
              "
              aria-describedby="receiving-supplier-error"
            />
            <p
              v-if="receivingFormErrors.supplierName || receivingError?.fieldErrors?.supplierName"
              id="receiving-supplier-error"
              class="text-sm text-destructive"
            >
              {{ receivingFormErrors.supplierName || receivingError?.fieldErrors?.supplierName }}
            </p>
          </div>

          <div class="grid gap-1.5">
            <label for="receiving-note" class="text-sm font-medium">
              {{ t('inventory.receivingDialog.noteLabel') }}
            </label>
            <textarea
              id="receiving-note"
              v-model="receivingNote"
              rows="3"
              class="w-full resize-none rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring"
              :placeholder="t('inventory.receivingDialog.notePlaceholder')"
              :aria-invalid="Boolean(receivingFormErrors.note || receivingError?.fieldErrors?.note)"
              aria-describedby="receiving-note-error"
            />
            <p
              v-if="receivingFormErrors.note || receivingError?.fieldErrors?.note"
              id="receiving-note-error"
              class="text-sm text-destructive"
            >
              {{ receivingFormErrors.note || receivingError?.fieldErrors?.note }}
            </p>
          </div>

          <p
            v-if="receivingMutation.isError.value"
            class="rounded-md bg-destructive-muted px-3 py-2 text-sm text-destructive"
            role="alert"
          >
            {{ receivingErrorMessage }}
          </p>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              :disabled="receivingMutation.isPending.value"
              @click="closeReceiving"
            >
              {{ t('common.cancel') }}
            </Button>
            <Button type="submit" :disabled="receivingMutation.isPending.value">
              {{
                receivingMutation.isPending.value
                  ? t('inventory.receivingDialog.submitting')
                  : t('inventory.receiveStock')
              }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog :open="thresholdItem !== null" @update:open="(open) => !open && closeThreshold()">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {{ t('inventory.thresholdDialog.title', { product: thresholdItem?.productName }) }}
          </DialogTitle>
          <DialogDescription>
            {{ t('inventory.thresholdDialog.description') }}
          </DialogDescription>
        </DialogHeader>

        <form class="grid gap-4" novalidate @submit.prevent="submitThreshold">
          <div class="grid gap-1.5">
            <label for="low-stock-threshold" class="text-sm font-medium">
              {{ t('inventory.thresholdDialog.label') }}
            </label>
            <Input
              id="low-stock-threshold"
              v-model="thresholdValue"
              type="number"
              step="1"
              inputmode="numeric"
              min="0"
              :placeholder="t('inventory.thresholdDialog.placeholder')"
              :aria-invalid="
                Boolean(
                  thresholdFormErrors.lowStockThreshold ||
                  thresholdError?.fieldErrors?.lowStockThreshold,
                )
              "
              aria-describedby="low-stock-threshold-error"
            />
            <p
              v-if="
                thresholdFormErrors.lowStockThreshold ||
                thresholdError?.fieldErrors?.lowStockThreshold
              "
              id="low-stock-threshold-error"
              class="text-sm text-destructive"
            >
              {{
                thresholdFormErrors.lowStockThreshold ||
                thresholdError?.fieldErrors?.lowStockThreshold
              }}
            </p>
          </div>

          <p
            v-if="thresholdMutation.isError.value"
            class="rounded-md bg-destructive-muted px-3 py-2 text-sm text-destructive"
            role="alert"
          >
            {{ thresholdErrorMessage }}
          </p>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              :disabled="thresholdMutation.isPending.value"
              @click="closeThreshold"
            >
              {{ t('common.cancel') }}
            </Button>
            <Button type="submit" :disabled="thresholdMutation.isPending.value">
              {{
                thresholdMutation.isPending.value
                  ? t('common.saving')
                  : t('inventory.thresholdDialog.submit')
              }}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </section>
</template>
