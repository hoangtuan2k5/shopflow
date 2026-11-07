<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useSessionStore } from '@/stores/session'
import { getAccountProfile } from '@/api/account'
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PasswordSettings from '@/components/account/PasswordSettings.vue'
const route = useRoute()
import AccountMenu from '@/components/account/AccountMenu.vue'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
const { t } = useI18n()
const session = useSessionStore()
const account = useQuery({
  queryKey: computed(() => ['account', session.user?.userId]),
  queryFn: getAccountProfile,
  retry: false,
})
</script>
<template>
  <div class="min-h-screen bg-muted/30">
    <header class="border-b bg-card">
      <div class="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-4 py-4">
        <RouterLink to="/customer"
          ><img src="/brand/shopflow-wordmark.png" alt="ShopFlow" class="h-7 w-auto"
        /></RouterLink>
        <div class="flex items-center gap-2"><LanguageSwitcher /><AccountMenu /></div>
      </div>
    </header>
    <main class="mx-auto max-w-2xl px-4 py-8">
      <RouterLink to="/customer" class="text-sm text-primary underline">{{
        t('account.back')
      }}</RouterLink>
      <h1 class="mt-5 text-2xl font-semibold">{{ t('account.title') }}</h1>
      <p v-if="account.isPending.value" role="status" class="mt-4">{{ t('account.loading') }}</p>
      <div v-else-if="account.isError.value" class="mt-4">
        <p role="alert">{{ t('account.loadError') }}</p>
        <button class="underline" @click="account.refetch()">{{ t('account.retry') }}</button>
      </div>
      <div v-else-if="account.data.value" class="mt-5 rounded-xl border bg-card p-6">
        <p class="break-words font-medium">{{ account.data.value.displayName }}</p>
        <p class="break-all text-sm text-muted-foreground">{{ account.data.value.email }}</p>
        <p v-if="!account.data.value.capabilities.manageDelivery" class="mt-4 text-sm">
          {{ t('account.demoHint') }}
        </p>
        <PasswordSettings
          v-if="
            route.name === 'account-password' &&
            (account.data.value.capabilities.setPassword ||
              account.data.value.capabilities.changePassword)
          "
          :key="session.user?.userId"
          :setup="account.data.value.capabilities.setPassword"
        />
      </div>
    </main>
  </div>
</template>
