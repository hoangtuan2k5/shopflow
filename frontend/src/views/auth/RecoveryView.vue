<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { z } from 'zod'
import {
  ApiClientError,
  requestPasswordReset,
  resetPassword,
  resendOtp,
  type ChallengeResponse,
} from '@/api'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { validNewPassword } from '@/lib/passwordPolicy'
import { authRedirect } from '@/router/authRedirect'

const { t } = useI18n()
const route = useRoute()
const email = ref('')
const otp = ref('')
const password = ref('')
const confirmPassword = ref('')
const challenge = ref<ChallengeResponse | null>(null)
const complete = ref(false)
const submitting = ref(false)
const errors = ref<Record<string, string>>({})
const errorKey = ref('')
const now = ref(Date.now())
const retryAt = ref(0)
const expiresAt = ref(0)
const timer = window.setInterval(() => {
  now.value = Date.now()
}, 1000)
onUnmounted(() => window.clearInterval(timer))
const resendSeconds = computed(() => Math.max(0, Math.ceil((retryAt.value - now.value) / 1000)))
const remaining = computed(() => Math.max(0, Math.ceil((expiresAt.value - now.value) / 1000)))
const loginTarget = computed(() => ({
  name: 'login',
  query: { redirect: authRedirect(route.query.redirect, 'customer') ?? undefined },
}))

function accept(value: ChallengeResponse) {
  challenge.value = value
  now.value = Date.now()
  retryAt.value = now.value + value.resendAfterSeconds * 1000
  expiresAt.value = now.value + value.expiresInSeconds * 1000
  otp.value = ''
}

function reject(error: unknown) {
  errorKey.value =
    error instanceof ApiClientError && error.status === 429
      ? 'tooMany'
      : error instanceof ApiClientError && error.status === 400 && challenge.value
        ? 'invalidCode'
        : 'unavailable'
  if (error instanceof ApiClientError && error.retryAfter)
    retryAt.value = Date.now() + error.retryAfter * 1000
}

async function focusError() {
  await nextTick()
  document.getElementById(`recovery-${Object.keys(errors.value)[0]}`)?.focus()
}

async function requestCode() {
  errors.value = {}
  errorKey.value = ''
  const canonical = email.value.trim().toLowerCase()
  if (
    !z.string().email().max(254).safeParse(canonical).success ||
    [...canonical].some((c) => c.charCodeAt(0) > 127)
  ) {
    errors.value.email = 'invalidEmail'
    await focusError()
    return
  }
  submitting.value = true
  try {
    accept(await requestPasswordReset(canonical))
    await nextTick()
    document.getElementById('recovery-otp')?.focus()
  } catch (error) {
    reject(error)
  } finally {
    submitting.value = false
  }
}

async function submitReset() {
  errors.value = {}
  errorKey.value = ''
  if (!/^[0-9]{6}$/.test(otp.value)) errors.value.otp = 'invalidOtp'
  if (!validNewPassword(password.value)) errors.value.password = 'invalidPassword'
  if (password.value !== confirmPassword.value) errors.value.confirm = 'mismatch'
  if (Object.keys(errors.value).length) {
    await focusError()
    return
  }
  if (!challenge.value) return
  submitting.value = true
  try {
    await resetPassword({
      challengeId: challenge.value.challengeId,
      otp: otp.value,
      newPassword: password.value,
    })
    complete.value = true
    challenge.value = null
    await nextTick()
    document.getElementById('recovery-signin')?.focus()
  } catch (error) {
    reject(error)
  } finally {
    otp.value = ''
    password.value = ''
    confirmPassword.value = ''
    submitting.value = false
  }
}

async function resend() {
  if (!challenge.value || resendSeconds.value || submitting.value) return
  submitting.value = true
  errorKey.value = ''
  try {
    accept(await resendOtp(challenge.value.challengeId))
  } catch (error) {
    reject(error)
  } finally {
    submitting.value = false
  }
}

function restart() {
  challenge.value = null
  otp.value = ''
  password.value = ''
  confirmPassword.value = ''
  errors.value = {}
  errorKey.value = ''
}
</script>

<template>
  <div class="grid min-h-screen place-items-center bg-secondary/30 px-4 py-10">
    <div class="w-full max-w-md">
      <div class="mb-6 flex items-center justify-between gap-4">
        <img class="h-8 w-auto" src="/brand/shopflow-wordmark.png" alt="ShopFlow" />
        <LanguageSwitcher />
      </div>
      <section
        class="rounded-xl border border-border bg-card p-6 shadow-sm"
        :aria-busy="submitting"
      >
        <header class="mb-6 space-y-2">
          <p class="text-xs font-bold uppercase tracking-[0.14em] text-success">ShopFlow</p>
          <h1 class="text-2xl font-semibold text-primary">
            {{
              t(
                complete
                  ? 'recovery.success'
                  : challenge
                    ? 'recovery.resetTitle'
                    : 'recovery.title',
              )
            }}
          </h1>
          <p class="text-sm text-muted-foreground">
            {{
              t(
                complete
                  ? 'recovery.successHint'
                  : challenge
                    ? 'recovery.sent'
                    : 'recovery.subtitle',
              )
            }}
          </p>
        </header>
        <form
          v-if="!challenge && !complete"
          class="grid gap-4"
          novalidate
          @submit.prevent="requestCode"
        >
          <label for="recovery-email" class="text-sm font-medium">{{
            t('registration.email')
          }}</label>
          <Input
            id="recovery-email"
            v-model="email"
            type="email"
            autocomplete="email"
            maxlength="254"
            :aria-invalid="Boolean(errors.email)"
            :aria-describedby="errors.email ? 'recovery-email-error' : undefined"
          />
          <p v-if="errors.email" id="recovery-email-error" class="text-sm text-destructive">
            {{ t(`registration.${errors.email}`) }}
          </p>
          <p v-if="errorKey" role="alert" class="text-sm text-destructive">
            {{ t(`registration.${errorKey}`) }}
          </p>
          <Button type="submit" :disabled="submitting">{{
            t(submitting ? 'registration.busy' : 'recovery.send')
          }}</Button>
        </form>
        <form v-else-if="challenge" class="grid gap-4" novalidate @submit.prevent="submitReset">
          <p class="break-all text-sm text-muted-foreground">{{ email }}</p>
          <div class="grid gap-1.5">
            <label for="recovery-otp" class="text-sm font-medium">{{
              t('registration.otp')
            }}</label>
            <Input
              id="recovery-otp"
              v-model="otp"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
              pattern="[0-9]{6}"
              :aria-invalid="Boolean(errors.otp)"
              :aria-describedby="errors.otp ? 'recovery-otp-error' : undefined"
            />
            <p v-if="errors.otp" id="recovery-otp-error" class="text-sm text-destructive">
              {{ t(`registration.${errors.otp}`) }}
            </p>
          </div>
          <div class="grid gap-1.5">
            <label for="recovery-password" class="text-sm font-medium">{{
              t('recovery.newPassword')
            }}</label>
            <Input
              id="recovery-password"
              v-model="password"
              type="password"
              autocomplete="new-password"
              :aria-invalid="Boolean(errors.password)"
              :aria-describedby="
                errors.password ? 'recovery-policy recovery-password-error' : 'recovery-policy'
              "
            />
            <p v-if="errors.password" id="recovery-password-error" class="text-sm text-destructive">
              {{ t(`registration.${errors.password}`) }}
            </p>
            <p id="recovery-policy" class="text-xs leading-relaxed text-muted-foreground">
              {{ t('registration.policy') }}
            </p>
          </div>
          <div class="grid gap-1.5">
            <label for="recovery-confirm" class="text-sm font-medium">{{
              t('recovery.confirm')
            }}</label>
            <Input
              id="recovery-confirm"
              v-model="confirmPassword"
              type="password"
              autocomplete="new-password"
              :aria-invalid="Boolean(errors.confirm)"
              :aria-describedby="errors.confirm ? 'recovery-confirm-error' : undefined"
            />
            <p v-if="errors.confirm" id="recovery-confirm-error" class="text-sm text-destructive">
              {{ t(`registration.${errors.confirm}`) }}
            </p>
          </div>
          <p class="text-sm text-muted-foreground">
            {{
              remaining
                ? t('registration.remaining', {
                    minutes: Math.floor(remaining / 60),
                    seconds: String(remaining % 60).padStart(2, '0'),
                  })
                : t('registration.expired')
            }}
          </p>
          <p v-if="errorKey" role="alert" class="text-sm text-destructive">
            {{ t(`registration.${errorKey}`) }}
          </p>
          <Button type="submit" :disabled="submitting || !remaining">{{
            t(submitting ? 'registration.busy' : 'recovery.reset')
          }}</Button>
          <Button
            type="button"
            variant="outline"
            :disabled="submitting || resendSeconds > 0"
            @click="resend"
            >{{
              resendSeconds
                ? t('registration.resendWait', { seconds: resendSeconds })
                : t('registration.resend')
            }}</Button
          >
          <button
            type="button"
            class="text-sm text-muted-foreground underline underline-offset-4"
            :disabled="submitting"
            @click="restart"
          >
            {{ t('registration.restart') }}
          </button>
        </form>
        <p class="mt-5 text-center text-sm">
          <RouterLink
            id="recovery-signin"
            :to="loginTarget"
            class="font-medium text-primary underline underline-offset-4"
            >{{ t('registration.signIn') }}</RouterLink
          >
        </p>
      </section>
    </div>
  </div>
</template>
