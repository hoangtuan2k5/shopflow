<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { z } from 'zod'
import {
  ApiClientError,
  registerAccount,
  resendOtp,
  verifyRegistration,
  type ChallengeResponse,
} from '@/api'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { authRedirect } from '@/router/authRedirect'
import { validNewPassword } from '@/lib/passwordPolicy'

const { t } = useI18n()
const route = useRoute()
const email = ref('')
const displayName = ref('')
const password = ref('')
const confirmPassword = ref('')
const otp = ref('')
const challenge = ref<ChallengeResponse | null>(null)
const verified = ref(false)
const submitting = ref(false)
const errors = ref<Record<string, string>>({})
const errorKey = ref('')
const retryAt = ref(0)
const expiresAt = ref(0)
const now = ref(Date.now())
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

function acceptChallenge(value: ChallengeResponse) {
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

async function register() {
  errors.value = {}
  errorKey.value = ''
  const canonical = email.value.trim().toLowerCase()
  if (
    !z.string().email().max(254).safeParse(canonical).success ||
    [...canonical].some((c) => c.charCodeAt(0) > 127)
  )
    errors.value.email = 'invalidEmail'
  if (![...displayName.value.trim()].length || [...displayName.value.trim()].length > 255)
    errors.value.displayName = 'invalidName'
  const value = password.value
  if (!validNewPassword(value)) errors.value.password = 'invalidPassword'
  if (value !== confirmPassword.value) errors.value.confirm = 'mismatch'
  if (Object.keys(errors.value).length) {
    await nextTick()
    document.getElementById(`register-${Object.keys(errors.value)[0]}`)?.focus()
    return
  }
  submitting.value = true
  try {
    acceptChallenge(
      await registerAccount({
        email: canonical,
        password: value,
        displayName: displayName.value.trim(),
      }),
    )
    password.value = ''
    confirmPassword.value = ''
    await nextTick()
    document.getElementById('register-otp')?.focus()
  } catch (error) {
    reject(error)
  } finally {
    submitting.value = false
  }
}

async function verify() {
  errorKey.value = ''
  errors.value = {}
  if (!/^[0-9]{6}$/.test(otp.value)) {
    errors.value.otp = 'invalidOtp'
    return
  }
  if (!challenge.value) return
  submitting.value = true
  try {
    await verifyRegistration(challenge.value.challengeId, otp.value)
    verified.value = true
    otp.value = ''
    challenge.value = null
    await nextTick()
    document.getElementById('register-signin')?.focus()
  } catch (error) {
    reject(error)
    otp.value = ''
  } finally {
    submitting.value = false
  }
}

async function resend() {
  if (!challenge.value || resendSeconds.value || submitting.value) return
  submitting.value = true
  errorKey.value = ''
  try {
    acceptChallenge(await resendOtp(challenge.value.challengeId))
  } catch (error) {
    reject(error)
  } finally {
    submitting.value = false
  }
}

function restart() {
  challenge.value = null
  otp.value = ''
  errorKey.value = ''
  errors.value = {}
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
          <p class="text-xs font-bold uppercase tracking-[0.14em] text-success">
            {{ t('registration.create') }}
          </p>
          <h1 class="text-2xl font-semibold text-primary">
            {{
              t(
                verified
                  ? 'registration.success'
                  : challenge
                    ? 'registration.verifyTitle'
                    : 'registration.title',
              )
            }}
          </h1>
          <p class="text-sm text-muted-foreground">
            {{
              t(
                verified
                  ? 'registration.successHint'
                  : challenge
                    ? 'registration.sent'
                    : 'registration.subtitle',
              )
            }}
          </p>
        </header>

        <form
          v-if="!challenge && !verified"
          class="grid gap-4"
          novalidate
          @submit.prevent="register"
        >
          <div
            v-for="field in ['email', 'displayName', 'password', 'confirm'] as const"
            :key="field"
            class="grid gap-1.5"
          >
            <label :for="`register-${field}`" class="text-sm font-medium">{{
              t(`registration.${field === 'displayName' ? 'name' : field}`)
            }}</label>
            <Input
              v-if="field === 'email'"
              id="register-email"
              v-model="email"
              type="email"
              autocomplete="email"
              maxlength="254"
              :aria-invalid="Boolean(errors.email)"
              :aria-describedby="errors.email ? 'register-email-error' : undefined"
            />
            <Input
              v-else-if="field === 'displayName'"
              id="register-displayName"
              v-model="displayName"
              autocomplete="nickname"
              :aria-invalid="Boolean(errors.displayName)"
              :aria-describedby="errors.displayName ? 'register-displayName-error' : undefined"
            />
            <Input
              v-else-if="field === 'password'"
              id="register-password"
              v-model="password"
              type="password"
              autocomplete="new-password"
              :aria-invalid="Boolean(errors.password)"
              :aria-describedby="
                errors.password ? 'register-policy register-password-error' : 'register-policy'
              "
            />
            <Input
              v-else
              id="register-confirm"
              v-model="confirmPassword"
              type="password"
              autocomplete="new-password"
              :aria-invalid="Boolean(errors.confirm)"
              :aria-describedby="errors.confirm ? 'register-confirm-error' : undefined"
            />
            <p
              v-if="errors[field]"
              :id="`register-${field}-error`"
              class="text-sm text-destructive"
            >
              {{ t(`registration.${errors[field]}`) }}
            </p>
            <p
              v-if="field === 'password'"
              id="register-policy"
              class="text-xs leading-relaxed text-muted-foreground"
            >
              {{ t('registration.policy') }}
            </p>
          </div>
          <p v-if="errorKey" role="alert" class="text-sm text-destructive">
            {{ t(`registration.${errorKey}`) }}
          </p>
          <Button type="submit" :disabled="submitting">{{
            t(submitting ? 'registration.busy' : 'registration.submit')
          }}</Button>
        </form>

        <form v-else-if="challenge" class="grid gap-4" novalidate @submit.prevent="verify">
          <label for="register-otp" class="text-sm font-medium">{{ t('registration.otp') }}</label>
          <Input
            id="register-otp"
            v-model="otp"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            pattern="[0-9]{6}"
            :aria-invalid="Boolean(errors.otp)"
            :aria-describedby="errors.otp ? 'register-otp-error' : undefined"
          />
          <p v-if="errors.otp" id="register-otp-error" class="text-sm text-destructive">
            {{ t(`registration.${errors.otp}`) }}
          </p>
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
            t(submitting ? 'registration.busy' : 'registration.verify')
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
            id="register-signin"
            :to="loginTarget"
            class="font-medium text-primary underline underline-offset-4"
            >{{ t('registration.signIn') }}</RouterLink
          >
        </p>
      </section>
      <p class="mt-4 text-center text-sm text-muted-foreground">
        <RouterLink to="/customer">{{ t('registration.browse') }}</RouterLink>
      </p>
    </div>
  </div>
</template>
