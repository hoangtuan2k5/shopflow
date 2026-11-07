<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { setupPassword, confirmPasswordSetup } from '@/api/account'
import { useSessionStore } from '@/stores/session'
const { t } = useI18n()
const session = useSessionStore()
const router = useRouter()
const challenge = ref('')
const otp = ref('')
const password = ref('')
const confirmation = ref('')
const busy = ref(false)
const error = ref('')
const retryAt = ref(0)
const seconds = ref(0)
const timer = window.setInterval(() => {
  seconds.value = Math.max(0, Math.ceil((retryAt.value - Date.now()) / 1000))
}, 1000)
onUnmounted(() => window.clearInterval(timer))
function failure(e: unknown) {
  const status = (e as { response?: { status?: number } }).response?.status
  error.value = t(
    status === 429
      ? 'account.rateLimited'
      : status === 503
        ? 'account.mailUnavailable'
        : 'account.passwordError',
  )
}
async function send() {
  busy.value = true
  error.value = ''
  try {
    const result = await setupPassword(challenge.value || undefined)
    challenge.value = result.challengeId
    retryAt.value = Date.now() + result.resendAfterSeconds * 1000
    seconds.value = result.resendAfterSeconds
    otp.value = ''
  } catch (e) {
    failure(e)
  } finally {
    busy.value = false
  }
}
async function submit() {
  error.value = ''
  if (password.value !== confirmation.value) {
    error.value = t('account.passwordMismatch')
    return
  }
  busy.value = true
  try {
    await confirmPasswordSetup(challenge.value, otp.value, password.value)
    password.value = ''
    confirmation.value = ''
    otp.value = ''
    session.forget()
    await router.replace('/login')
  } catch (e) {
    failure(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="mt-6 border-t pt-6">
    <h2 class="text-xl font-semibold">{{ t('account.setupPassword') }}</h2>
    <p class="mt-2 text-sm text-muted-foreground">{{ t('account.setupHint') }}</p>
    <p v-if="error" role="alert" class="mt-4 text-sm text-destructive">{{ error }}</p>
    <Button
      class="mt-4"
      type="button"
      variant="outline"
      :disabled="busy || seconds > 0"
      @click="send"
    >
      {{ t(challenge ? 'account.resendCode' : 'account.sendCode')
      }}<span v-if="seconds"> ({{ seconds }}s)</span>
    </Button>
    <form v-if="challenge" class="mt-5 space-y-4" @submit.prevent="submit">
      <div>
        <label for="setup-otp" class="text-sm font-medium">{{ t('account.otp') }}</label>
        <Input
          id="setup-otp"
          v-model="otp"
          class="mt-1"
          inputmode="numeric"
          autocomplete="one-time-code"
          pattern="[0-9]{6}"
          maxlength="6"
          required
        />
      </div>
      <p class="text-sm text-muted-foreground">{{ t('account.passwordPolicy') }}</p>
      <div>
        <label for="new-password" class="text-sm font-medium">{{ t('account.newPassword') }}</label>
        <Input
          id="new-password"
          v-model="password"
          class="mt-1"
          type="password"
          autocomplete="new-password"
          required
        />
      </div>
      <div>
        <label for="confirm-password" class="text-sm font-medium">{{
          t('account.confirmPassword')
        }}</label>
        <Input
          id="confirm-password"
          v-model="confirmation"
          class="mt-1"
          type="password"
          autocomplete="new-password"
          required
        />
      </div>
      <Button type="submit" :disabled="busy">{{
        t(busy ? 'account.loading' : 'account.savePassword')
      }}</Button>
    </form>
  </section>
</template>
