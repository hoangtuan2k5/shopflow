<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { ApiClientError, uploadProductImage } from '@/api'
import { useProductPages } from '@/composables/useProductPages'
import ProductImage from '@/components/catalog/ProductImage.vue'
import { Button, buttonVariants } from '@/components/ui/button'

const { t } = useI18n()
const queryClient = useQueryClient()
const success = ref('')
const selected = ref<Record<number, File | undefined>>({})
const products = useProductPages()
const items = computed(() => products.data.value?.pages.flatMap((page) => page.items) ?? [])
function loadMore() {
  if (products.hasNextPage.value && !products.isFetching.value) {
    void products.fetchNextPage({ cancelRefetch: false })
  }
}
const upload = useMutation({
  mutationFn: ({ id, file }: { id: number; file: File }) => uploadProductImage(id, file),
  onSuccess: async (_, variables) => {
    const name = items.value.find((product) => product.id === variables.id)?.name ?? ''
    success.value = t('productImages.success', { name })
    await queryClient.invalidateQueries({ queryKey: ['products'] })
  },
})
const error = computed(() => {
  const failure = upload.error.value
  if (!failure) return ''
  if (
    failure instanceof ApiClientError &&
    typeof failure.details === 'object' &&
    failure.details &&
    'message' in failure.details &&
    typeof failure.details.message === 'string'
  ) {
    return failure.details.message
  }
  return t('productImages.error')
})
function choose(id: number, event: Event) {
  selected.value[id] = (event.target as HTMLInputElement).files?.[0]
  success.value = ''
  upload.reset()
}
function submit(id: number) {
  const file = selected.value[id]
  if (!file) return
  success.value = ''
  upload.mutate({ id, file })
}
</script>

<template>
  <section class="grid gap-6">
    <header class="flex flex-wrap items-start justify-between gap-4 border-b border-border pb-6">
      <div class="space-y-2">
        <h1 class="text-3xl font-semibold text-primary">{{ t('productImages.title') }}</h1>
        <p class="max-w-2xl text-sm text-muted-foreground">{{ t('productImages.subtitle') }}</p>
        <p class="text-sm text-muted-foreground">{{ t('productImages.hint') }}</p>
      </div>
      <RouterLink to="/shop-owner" :class="buttonVariants({ variant: 'outline' })">{{
        t('productImages.back')
      }}</RouterLink>
    </header>
    <p v-if="success" role="status" class="rounded-lg bg-success-muted p-4 text-success">
      {{ success }}
    </p>
    <p v-if="error" role="alert" class="rounded-lg bg-destructive-muted p-4 text-destructive">
      {{ error }}
    </p>
    <p v-if="products.isPending.value" role="status">{{ t('catalog.loadingLabel') }}</p>
    <div
      v-else-if="products.isError.value && !products.data.value"
      role="alert"
      class="grid justify-items-start gap-3"
    >
      <p>{{ t('productImages.loadError') }}</p>
      <Button variant="outline" @click="products.refetch()">{{ t('common.tryAgain') }}</Button>
    </div>
    <p v-else-if="!items.length">{{ t('productImages.empty') }}</p>
    <article
      v-for="product in items"
      v-else
      :key="product.id"
      class="grid min-w-0 gap-4 rounded-xl border border-border bg-card p-4 sm:grid-cols-[6rem_minmax(0,1fr)] sm:gap-6 sm:p-6"
    >
      <div
        class="relative grid size-24 place-items-center overflow-hidden rounded-lg bg-gradient-to-br from-secondary to-accent text-primary"
      >
        <ProductImage :src="product.imageUrl" :name="product.name" />
      </div>
      <div class="grid min-w-0 gap-3">
        <h2 class="break-words text-lg font-semibold text-primary">{{ product.name }}</h2>
        <form class="flex min-w-0 flex-wrap items-end gap-3" @submit.prevent="submit(product.id)">
          <label class="grid w-full min-w-0 gap-2 text-sm sm:w-auto sm:flex-1">
            <span>{{ t('productImages.choose', { name: product.name }) }}</span>
            <input
              type="file"
              accept="image/png,image/jpeg"
              :disabled="upload.isPending.value"
              class="block w-full min-w-0 rounded-md border border-border p-2 text-sm file:mr-3 file:rounded file:border-0 file:bg-secondary file:px-3 file:py-1"
              @change="choose(product.id, $event)"
            />
          </label>
          <Button type="submit" :disabled="!selected[product.id] || upload.isPending.value">
            {{
              upload.isPending.value && upload.variables.value?.id === product.id
                ? t('productImages.uploading')
                : t('productImages.upload')
            }}
          </Button>
        </form>
      </div>
    </article>
    <p v-if="products.isFetchNextPageError.value" role="alert">{{ t('catalog.loadMoreError') }}</p>
    <Button
      v-if="products.hasNextPage.value"
      type="button"
      variant="outline"
      :aria-disabled="products.isFetching.value"
      @click="loadMore"
    >
      {{
        products.isFetchingNextPage.value
          ? t('catalog.loadingMore')
          : products.isFetchNextPageError.value
            ? t('common.tryAgain')
            : t('catalog.loadMore')
      }}
    </Button>
    <p v-else-if="items.length" role="status">{{ t('catalog.endOfProducts') }}</p>
  </section>
</template>
