<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import {
  IconAlertCircle,
  IconArrowRight,
  IconCircleCheck,
  IconCircleX,
  IconInbox,
  IconPackage,
  IconRefresh,
  IconShoppingBag,
  IconX,
} from '@tabler/icons-vue'
import { getProductById, getProducts, type StockStatus } from '@/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

const selectedProductId = ref<number | null>(null)

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
const inStockCount = computed(
  () => products.value.filter((product) => product.stockStatus === 'IN_STOCK').length,
)

const stockStatus = {
  IN_STOCK: {
    label: 'In stock',
    classes: 'bg-success-muted text-success',
    icon: IconCircleCheck,
  },
  OUT_OF_STOCK: {
    label: 'Out of stock',
    classes: 'bg-destructive-muted text-destructive',
    icon: IconCircleX,
  },
} satisfies Record<StockStatus, { label: string; classes: string; icon: Component }>

const currency = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
})

function closeProduct() {
  selectedProductId.value = null
}
</script>

<template>
  <section class="grid gap-6">
    <header class="grid gap-4 border-b border-border pb-6 lg:grid-cols-[1fr_auto] lg:items-end">
      <div>
        <p class="flex items-center gap-2 text-sm font-semibold text-success">
          <IconShoppingBag :size="18" :stroke-width="1.8" aria-hidden="true" />
          Customer catalog
        </p>
        <h1 class="mt-2 text-4xl font-bold leading-tight tracking-normal">Product catalog</h1>
        <p class="mt-2 max-w-2xl text-muted-foreground">
          Live pricing and availability from ShopFlow inventory.
        </p>
      </div>

      <div
        v-if="!productsQuery.isPending.value && !productsQuery.isError.value"
        class="flex items-center gap-3 text-sm"
        aria-label="Catalog summary"
      >
        <span class="font-medium text-foreground">{{ products.length }} products</span>
        <span class="h-4 w-px bg-border" aria-hidden="true" />
        <span class="font-medium text-success">{{ inStockCount }} available</span>
      </div>
    </header>

    <div v-if="productsQuery.isPending.value" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="index in 3"
        :key="index"
        class="min-h-72 overflow-hidden rounded-lg border border-border bg-card"
        aria-hidden="true"
      >
        <div class="h-28 animate-pulse border-b border-border bg-muted" />
        <div class="grid gap-3 p-6">
          <div class="h-3 w-16 animate-pulse rounded-sm bg-muted" />
          <div class="h-5 w-3/4 animate-pulse rounded-sm bg-muted" />
          <div class="mt-3 h-7 w-24 animate-pulse rounded-sm bg-muted" />
          <div class="mt-3 h-10 animate-pulse rounded-md bg-muted" />
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
      <div class="min-w-0">
        <p class="font-semibold">Catalog unavailable</p>
        <p class="mt-1 text-sm text-muted-foreground">
          We could not load products. Check the backend and try again.
        </p>
        <Button class="mt-4" variant="outline" @click="productsQuery.refetch()">
          <IconRefresh :size="18" :stroke-width="1.8" aria-hidden="true" />
          Try again
        </Button>
      </div>
    </div>

    <div
      v-else-if="products.length === 0"
      class="grid justify-items-center gap-3 py-16 text-center"
    >
      <div class="grid size-12 place-items-center rounded-lg bg-secondary text-primary">
        <IconInbox :size="26" :stroke-width="1.6" aria-hidden="true" />
      </div>
      <div>
        <p class="font-semibold">No products available</p>
        <p class="mt-1 text-sm text-muted-foreground">The active catalog is currently empty.</p>
      </div>
    </div>

    <div v-else class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <Card
        v-for="product in products"
        :key="product.id"
        class="group flex min-h-72 flex-col overflow-hidden border-border shadow-sm transition-[border-color,box-shadow] duration-150 hover:border-primary/30 hover:shadow-md"
      >
        <div
          class="relative flex h-28 items-center justify-center border-b border-border bg-secondary/70"
        >
          <IconPackage
            class="text-primary/70 transition-colors duration-150 group-hover:text-primary"
            :size="42"
            :stroke-width="1.5"
            aria-hidden="true"
          />
          <span
            :class="[
              'absolute right-4 top-4 flex items-center gap-1.5 rounded-sm px-2 py-1 text-xs font-semibold',
              stockStatus[product.stockStatus].classes,
            ]"
          >
            <component
              :is="stockStatus[product.stockStatus].icon"
              :size="15"
              :stroke-width="2"
              aria-hidden="true"
            />
            {{ stockStatus[product.stockStatus].label }}
          </span>
        </div>

        <CardHeader class="flex-1 gap-2 pb-4">
          <p class="text-xs font-medium text-muted-foreground">Product #{{ product.id }}</p>
          <CardTitle class="text-lg leading-snug">{{ product.name }}</CardTitle>
        </CardHeader>
        <CardContent class="pb-5">
          <p class="text-2xl font-semibold leading-none tabular-nums">
            {{ currency.format(product.price) }}
          </p>
        </CardContent>
        <CardFooter class="border-t border-border bg-secondary/30 px-6 py-4">
          <Button
            class="w-full justify-between"
            variant="outline"
            @click="selectedProductId = product.id"
          >
            View details
            <IconArrowRight :size="18" :stroke-width="1.8" aria-hidden="true" />
          </Button>
        </CardFooter>
      </Card>
    </div>

    <Dialog :open="selectedProductId !== null" @update:open="(open) => !open && closeProduct()">
      <DialogContent class="overflow-hidden p-0">
        <DialogClose as-child>
          <Button
            class="absolute right-4 top-4 z-10 size-10 bg-card/90 p-0"
            variant="outline"
            aria-label="Close product details"
            title="Close product details"
          >
            <IconX :size="20" :stroke-width="1.8" aria-hidden="true" />
          </Button>
        </DialogClose>

        <div v-if="productQuery.isPending.value" class="grid min-h-72 place-items-center p-6">
          <DialogHeader class="sr-only">
            <DialogTitle>Product details</DialogTitle>
            <DialogDescription>Loading product details.</DialogDescription>
          </DialogHeader>
          <div class="grid justify-items-center gap-3 text-sm text-muted-foreground">
            <IconPackage class="animate-pulse" :size="36" :stroke-width="1.5" aria-hidden="true" />
            Loading product details...
          </div>
        </div>

        <div v-else-if="productQuery.isError.value" class="p-6 pr-16" role="alert">
          <DialogHeader>
            <DialogTitle>Product unavailable</DialogTitle>
            <DialogDescription>This product could not be loaded.</DialogDescription>
          </DialogHeader>
        </div>

        <template v-else-if="productQuery.data.value">
          <div class="grid h-32 place-items-center border-b border-border bg-secondary/70">
            <IconPackage
              class="text-primary/75"
              :size="48"
              :stroke-width="1.5"
              aria-hidden="true"
            />
          </div>
          <div class="grid gap-5 p-6">
            <DialogHeader>
              <div class="flex items-center justify-between gap-4 pr-10">
                <p class="text-xs font-medium text-muted-foreground">
                  Product #{{ productQuery.data.value.id }}
                </p>
                <span
                  :class="[
                    'flex items-center gap-1.5 rounded-sm px-2 py-1 text-xs font-semibold',
                    stockStatus[productQuery.data.value.stockStatus].classes,
                  ]"
                >
                  <component
                    :is="stockStatus[productQuery.data.value.stockStatus].icon"
                    :size="15"
                    :stroke-width="2"
                    aria-hidden="true"
                  />
                  {{ stockStatus[productQuery.data.value.stockStatus].label }}
                </span>
              </div>
              <DialogTitle class="text-xl leading-snug">
                {{ productQuery.data.value.name }}
              </DialogTitle>
              <DialogDescription class="leading-relaxed">
                {{ productQuery.data.value.description || 'No description available.' }}
              </DialogDescription>
            </DialogHeader>
            <p class="border-t border-border pt-5 text-2xl font-semibold tabular-nums">
              {{ currency.format(productQuery.data.value.price) }}
            </p>
          </div>
        </template>
      </DialogContent>
    </Dialog>
  </section>
</template>
