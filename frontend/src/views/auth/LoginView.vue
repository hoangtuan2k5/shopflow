<script setup lang="ts">
import { computed, ref } from 'vue'
import { IconLock } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { z } from 'zod'
import { apiBaseUrl, ApiClientError, type AuthErrorDetails } from '@/api'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useSessionStore } from '@/stores/session'
import { authRedirect } from '@/router/authRedirect'
import { useCartStore } from '@/stores/cart'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const cart = useCartStore()

const username = ref('')
const password = ref('')
const formErrors = ref<Record<string, string>>({})
const submitting = ref(false)
const failed = ref(false)
const linking = computed(() => route.query.google === 'link_required')
const linkExpired = ref(false)
const googleUrl = computed(() => {
  const returnTo = authRedirect(route.query.redirect, 'customer') ?? '/customer'
  return `${apiBaseUrl}/oauth2/authorization/google?${new URLSearchParams({ returnTo })}`
})

const schema = z.object({
  username: z.string().min(1, t('auth.form.usernameRequired')),
  password: z.string().min(1, t('auth.form.passwordRequired')),
})

const errorMessage = computed(() => {
  if (linkExpired.value) return t('google.expired')
  if (failed.value) return t('auth.rejected')
  return route.query.google === 'failed' ? t('google.failed') : ''
})

function isAuthErrorDetails(value: unknown): value is AuthErrorDetails {
  if (!value || typeof value !== 'object') return false
  const details = value as Partial<AuthErrorDetails>
  return typeof details.message === 'string' && typeof details.status === 'number'
}

async function submit() {
  const result = schema.safeParse({
    username: linking.value ? 'link' : username.value,
    password: password.value,
  })
  if (!result.success) {
    formErrors.value = Object.fromEntries(
      result.error.issues.map((issue) => [String(issue.path[0]), issue.message]),
    )
    return
  }

  formErrors.value = {}
  failed.value = false
  linkExpired.value = false
  submitting.value = true
  try {
    const returnTo = linking.value
      ? await session.linkGoogle(password.value)
      : await session.signIn({ username: username.value, password: password.value })
    password.value = ''
    const redirect =
      authRedirect(returnTo, session.roleKey) ?? authRedirect(route.query.redirect, session.roleKey)
    await router.replace(redirect ?? session.homePath)
  } catch (error) {
    // Backend cố tình không nói sai ở đâu, nên giao diện cũng chỉ hiển thị một thông báo chung.
    failed.value = error instanceof ApiClientError && isAuthErrorDetails(error.details)
    if (!failed.value) failed.value = true
    linkExpired.value = linking.value && error instanceof ApiClientError && error.status === 400
    password.value = ''
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="grid min-h-screen place-items-center bg-secondary/30 px-4 py-10">
    <div class="w-full max-w-sm">
      <div class="mb-6 flex items-center justify-between gap-4">
        <img class="h-8 w-auto" src="/brand/shopflow-wordmark.png" alt="ShopFlow" />
        <LanguageSwitcher />
      </div>

      <p
        v-if="cart.storageWarning && cart.itemCount > 0"
        class="mb-4 rounded-lg border border-border bg-card p-3 text-sm"
        role="alert"
      >
        {{ t('cartPersistence.storageWarning') }}
      </p>
      <section class="rounded-xl border border-border bg-card p-6 shadow-sm">
        <header class="mb-6 space-y-1.5">
          <p
            class="flex items-center gap-2 text-xs font-bold uppercase tracking-[0.14em] text-success"
          >
            <IconLock :size="15" :stroke-width="1.9" aria-hidden="true" />
            {{ t('auth.kicker') }}
          </p>
          <h1 class="text-2xl font-semibold text-primary">
            {{ linking ? t('google.linkTitle') : t('auth.title') }}
          </h1>
          <p class="text-sm text-muted-foreground">
            {{ linking ? t('google.linkHint') : t('auth.subtitle') }}
          </p>
        </header>

        <form class="grid gap-4" novalidate @submit.prevent="submit">
          <div v-if="!linking" class="grid gap-1.5">
            <label for="login-username" class="text-sm font-medium">
              {{ t('auth.fields.username') }}
            </label>
            <Input
              id="login-username"
              v-model="username"
              autocomplete="username"
              maxlength="254"
              :aria-invalid="Boolean(formErrors.username)"
              aria-describedby="login-username-error"
            />
            <p
              v-if="formErrors.username"
              id="login-username-error"
              class="text-sm text-destructive"
            >
              {{ formErrors.username }}
            </p>
          </div>

          <div class="grid gap-1.5">
            <label for="login-password" class="text-sm font-medium">
              {{ t('auth.fields.password') }}
            </label>
            <Input
              id="login-password"
              v-model="password"
              type="password"
              autocomplete="current-password"
              maxlength="200"
              :aria-invalid="Boolean(formErrors.password)"
              aria-describedby="login-password-error"
            />
            <p
              v-if="formErrors.password"
              id="login-password-error"
              class="text-sm text-destructive"
            >
              {{ formErrors.password }}
            </p>
          </div>

          <p
            v-if="errorMessage"
            class="rounded-md bg-destructive-muted px-3 py-2 text-sm text-destructive"
            role="alert"
          >
            {{ errorMessage }}
          </p>

          <Button type="submit" :disabled="submitting">
            {{
              submitting
                ? t('auth.submitting')
                : linking
                  ? t('google.linkSubmit')
                  : t('auth.submit')
            }}
          </Button>
        </form>
        <Button v-if="!linking" as-child variant="outline" class="mt-4 w-full">
          <a :href="googleUrl">{{ t('google.continue') }}</a>
        </Button>
        <p v-else class="mt-4 text-center text-sm">
          <RouterLink to="/login" class="text-primary underline underline-offset-4">
            {{ t('google.cancelLink') }}
          </RouterLink>
        </p>
        <p class="mt-4 text-center text-sm">
          <RouterLink
            :to="{
              name: 'password-recovery',
              query: { redirect: authRedirect(route.query.redirect, 'customer') ?? undefined },
            }"
            class="font-medium text-primary underline underline-offset-4"
            >{{ t('recovery.forgot') }}</RouterLink
          >
        </p>
        <p class="mt-5 text-center text-sm">
          <RouterLink
            :to="{
              name: 'register',
              query: { redirect: authRedirect(route.query.redirect, 'customer') ?? undefined },
            }"
            class="font-medium text-primary underline underline-offset-4"
          >
            {{ t('registration.create') }}
          </RouterLink>
        </p>
      </section>

      <p class="mt-4 text-center text-xs text-muted-foreground">{{ t('auth.guestHint') }}</p>
    </div>
  </div>
</template>
