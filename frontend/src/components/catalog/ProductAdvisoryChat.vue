<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useMutation } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import {
  IconArrowUp,
  IconArrowUpRight,
  IconMessageCircle,
  IconRefresh,
  IconX,
} from '@tabler/icons-vue'
import {
  ApiClientError,
  sendChatMessage,
  type ChatMessage,
  type ChatRequest,
  type ProductListItem,
} from '@/api'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import ProductImage from './ProductImage.vue'

defineProps<{ cartVisible: boolean }>()
const emit = defineEmits<{ product: [id: number] }>()
const { t, tm } = useI18n()
const open = ref(false)
const draft = ref('')
const invalid = ref(false)
const messages = ref<(ChatMessage & { products?: ProductListItem[] })[]>([])
// shortcut: component-only history clears on reload; persist only if account-linked chat becomes a requirement.
const history = ref<ChatMessage[]>([])
const lastRequest = ref<ChatRequest | null>(null)
const log = ref<HTMLElement | null>(null)
const input = ref<HTMLTextAreaElement | null>(null)
const pendingProduct = ref<number | null>(null)
const blocked = ref(false)
let quotaTimer: ReturnType<typeof setTimeout> | undefined
onUnmounted(() => clearTimeout(quotaTimer))
const mutation = useMutation({
  mutationFn: sendChatMessage,
  retry: false,
  onSuccess(response, request) {
    messages.value.push({ role: 'assistant', content: response.reply, products: response.products })
    history.value = [
      ...request.history,
      { role: 'user' as const, content: request.question },
      { role: 'assistant' as const, content: response.reply },
    ]
      .slice(-6)
      .map((message) => ({ ...message, content: message.content.slice(0, 2000) }))
    lastRequest.value = null
    void nextTick(() => input.value?.focus())
  },
  onError(error) {
    if (error instanceof ApiClientError && error.status === 429) {
      blocked.value = true
      quotaTimer = setTimeout(
        () => {
          blocked.value = false
        },
        Math.min(error.retryAfter ?? 86400, 86400) * 1000,
      )
    }
  },
})
const errorMessage = computed(() => {
  const error = mutation.error.value
  if (error instanceof ApiClientError && error.status === 403) return t('chat.csrf')
  if (error instanceof ApiClientError && error.status === 429) return t('chat.busy')
  return t('chat.error')
})
const price = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' })
function chooseExample(example: string) {
  draft.value = example
  input.value?.focus()
}
function submit() {
  if (mutation.isPending.value || blocked.value) return
  const question = draft.value.trim()
  invalid.value = !question || question.length > 500
  if (invalid.value) return
  const request = { question, history: [...history.value].slice(-6) }
  messages.value.push({ role: 'user', content: question })
  lastRequest.value = request
  draft.value = ''
  mutation.mutate(request)
}
function retry() {
  if (lastRequest.value && !mutation.isPending.value && !blocked.value)
    mutation.mutate(lastRequest.value)
}
function chooseProduct(id: number) {
  pendingProduct.value = id
  open.value = false
}
function closeFocus(event: Event) {
  if (pendingProduct.value !== null) {
    event.preventDefault()
    emit('product', pendingProduct.value)
    pendingProduct.value = null
  }
}
watch(
  () => [messages.value.length, mutation.isPending.value, mutation.isError.value],
  async () => {
    await nextTick()
    if (log.value) log.value.scrollTop = log.value.scrollHeight
  },
)
</script>

<template>
  <Dialog v-model:open="open">
    <DialogTrigger as-child>
      <Button
        :class="{ 'max-sm:bottom-20': cartVisible }"
        class="fixed bottom-5 right-4 z-40 gap-2 rounded-full bg-primary px-5 py-6 text-primary-foreground shadow-lg sm:bottom-7 sm:right-7"
        data-testid="chat-open"
      >
        <IconMessageCircle :size="21" aria-hidden="true" />{{ t('chat.open') }}
      </Button>
    </DialogTrigger>
    <DialogContent
      :aria-describedby="undefined"
      class="!left-0 !top-0 !translate-x-0 !translate-y-0 flex h-dvh !w-full !max-w-none flex-col gap-0 overflow-hidden rounded-none p-0 sm:!left-auto sm:!top-auto sm:bottom-6 sm:right-6 sm:h-[min(44rem,calc(100dvh-3rem))] sm:!w-[min(32.5rem,calc(100vw-3rem))] sm:rounded-2xl"
      @close-auto-focus="closeFocus"
      @open-auto-focus="
        (event) => {
          event.preventDefault()
          nextTick(() => input?.focus())
        }
      "
    >
      <header
        class="flex shrink-0 items-center justify-between gap-3 border-b border-border bg-primary px-4 py-2 text-primary-foreground sm:px-5"
      >
        <DialogTitle class="text-[10px] font-bold tracking-[0.15em] text-emerald-200">
          {{ t('chat.kicker') }}
        </DialogTitle>
        <button
          type="button"
          class="shrink-0 rounded-full p-2 hover:bg-white/10 focus-visible:outline focus-visible:outline-2"
          :aria-label="t('chat.close')"
          @click="open = false"
        >
          <IconX :size="20" aria-hidden="true" />
        </button>
      </header>
      <div
        ref="log"
        role="log"
        :aria-label="t('chat.conversation')"
        aria-live="polite"
        :aria-busy="mutation.isPending.value"
        class="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-contain bg-muted/25 p-4 sm:p-5"
        data-testid="chat-log"
      >
        <div v-if="!messages.length" class="py-5">
          <span
            class="mb-5 inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-emerald-200 bg-emerald-50 text-emerald-700"
            ><IconMessageCircle :size="25" aria-hidden="true"
          /></span>
          <h3 class="text-lg font-semibold text-foreground">{{ t('chat.welcome') }}</h3>
          <p class="mt-2 text-sm leading-relaxed text-muted-foreground">{{ t('chat.intro') }}</p>
          <div class="mt-5 grid gap-2">
            <button
              v-for="example in tm('chat.examples') as string[]"
              :key="example"
              type="button"
              class="flex items-center justify-between gap-3 rounded-xl border border-border bg-background p-3 text-left text-sm hover:border-primary"
              @click="chooseExample(example)"
            >
              {{ example
              }}<IconArrowUpRight
                class="shrink-0 text-muted-foreground"
                :size="18"
                aria-hidden="true"
              />
            </button>
          </div>
        </div>
        <article
          v-for="(message, index) in messages"
          :key="index"
          :class="message.role === 'user' ? 'ml-7' : 'mr-1'"
          :data-testid="`chat-${message.role}`"
        >
          <p
            class="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground"
          >
            {{ message.role === 'user' ? t('chat.you') : t('chat.assistant') }}
          </p>
          <p
            :class="
              message.role === 'user'
                ? 'rounded-2xl rounded-tr-sm bg-primary p-3.5 text-primary-foreground'
                : 'text-foreground'
            "
            class="whitespace-pre-wrap break-words text-sm leading-relaxed [overflow-wrap:anywhere]"
          >
            {{ message.content }}
          </p>
          <div v-if="message.role === 'assistant'" class="mt-3 space-y-2">
            <button
              v-for="product in message.products"
              :key="product.id"
              type="button"
              class="flex w-full items-center gap-3 rounded-xl border border-border bg-background p-3 text-left transition-colors hover:border-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
              :aria-label="t('chat.product', { name: product.name })"
              @click="chooseProduct(product.id)"
            >
              <span
                class="relative flex h-16 w-14 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-muted"
                ><ProductImage :src="product.imageUrl" :name="product.name" eager
              /></span>
              <span class="min-w-0 flex-1">
                <span class="line-clamp-2 block text-xs font-semibold leading-snug">{{
                  product.name
                }}</span>
                <span class="mt-1 block text-sm font-bold">{{ price.format(product.price) }}</span>
                <span
                  :class="
                    product.stockStatus === 'IN_STOCK' ? 'text-emerald-700' : 'text-destructive'
                  "
                  class="mt-1 block text-[11px]"
                  >{{ t(`catalog.stock.${product.stockStatus}`) }}</span
                >
              </span>
              <IconArrowUpRight
                :size="16"
                class="shrink-0 text-muted-foreground"
                aria-hidden="true"
              />
            </button>
            <p v-if="!message.products?.length" class="text-xs text-muted-foreground">
              {{ t('chat.empty') }}
            </p>
          </div>
        </article>
        <p
          v-if="mutation.isPending.value"
          role="status"
          class="flex items-center gap-2 text-sm text-muted-foreground"
        >
          <span
            class="h-2 w-2 rounded-full bg-emerald-600 motion-safe:animate-pulse"
            aria-hidden="true"
          />{{ t('chat.thinking') }}
        </p>
        <div
          v-if="mutation.isError.value"
          role="alert"
          class="rounded-xl border border-destructive/20 bg-destructive/5 p-3 text-sm"
        >
          <p>{{ errorMessage }}</p>
          <Button
            v-if="
              !(
                mutation.error.value instanceof ApiClientError &&
                mutation.error.value.status === 403
              )
            "
            type="button"
            variant="outline"
            size="sm"
            class="mt-3"
            :disabled="blocked || mutation.isPending.value"
            @click="retry"
            ><IconRefresh :size="15" aria-hidden="true" />{{ t('chat.retry') }}</Button
          >
        </div>
      </div>
      <footer
        class="shrink-0 border-t border-border bg-background p-4 pb-[max(1rem,env(safe-area-inset-bottom))]"
      >
        <p class="mb-3 text-[11px] leading-relaxed text-muted-foreground">{{ t('chat.note') }}</p>
        <form
          class="flex items-end gap-2 rounded-xl border border-input bg-muted/20 p-2 focus-within:ring-2 focus-within:ring-ring/30"
          @submit.prevent="submit"
        >
          <label class="sr-only" for="chat-question">{{ t('chat.input') }}</label>
          <textarea
            id="chat-question"
            ref="input"
            v-model="draft"
            rows="2"
            maxlength="500"
            class="max-h-28 min-h-12 min-w-0 flex-1 resize-none bg-transparent px-1 py-1 text-sm outline-none"
            :placeholder="t('chat.placeholder')"
            :aria-invalid="invalid"
            :aria-describedby="invalid ? 'chat-validation' : undefined"
            :disabled="mutation.isPending.value || blocked"
            @keydown.enter.exact.prevent="submit"
          />
          <Button
            type="submit"
            size="sm"
            class="h-10 w-10 shrink-0 rounded-lg p-0"
            :aria-label="t('chat.send')"
            :disabled="mutation.isPending.value || blocked || !draft.trim()"
            ><IconArrowUp :size="19" aria-hidden="true"
          /></Button>
        </form>
        <p v-if="invalid" id="chat-validation" role="alert" class="mt-2 text-xs text-destructive">
          {{ t('chat.max') }}
        </p>
      </footer>
    </DialogContent>
  </Dialog>
</template>
