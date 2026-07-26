<script setup lang="ts">
import { IconLogout, IconMenu2 } from '@tabler/icons-vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { Button, buttonVariants } from '@/components/ui/button'
import { useAppShellStore } from '@/stores/appShell'
import { useSessionStore } from '@/stores/session'

const { t } = useI18n()
const appShell = useAppShellStore()
const session = useSessionStore()
const router = useRouter()
const queryClient = useQueryClient()

async function signOut() {
  await session.signOut()
  // Dữ liệu đã cache thuộc về người dùng vừa đăng xuất, không được để lộ sang phiên sau.
  queryClient.clear()
  await router.push('/login')
}
</script>

<template>
  <header class="border-b border-border bg-card">
    <div class="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-6">
      <div class="flex min-w-0 items-center gap-4">
        <img class="h-8 w-auto" src="/brand/shopflow-wordmark.png" alt="ShopFlow" />
        <p class="hidden border-l border-border pl-4 text-sm text-muted-foreground sm:block">
          {{ t('shell.tagline') }}
        </p>
      </div>

      <div class="flex items-center gap-2">
        <p v-if="session.user" class="hidden text-sm sm:block">
          <span class="font-medium">{{ session.user.displayName }}</span>
        </p>
        <LanguageSwitcher />
        <Button
          v-if="session.isAuthenticated"
          class="gap-2"
          variant="outline"
          :title="t('auth.signOut')"
          @click="signOut"
        >
          <IconLogout :size="18" :stroke-width="1.8" aria-hidden="true" />
          <span class="hidden sm:inline">{{ t('auth.signOut') }}</span>
        </Button>
        <RouterLink v-else to="/login" :class="buttonVariants({ variant: 'outline' })">
          {{ t('auth.signIn') }}
        </RouterLink>
        <Button
          class="size-10 p-0 lg:hidden"
          variant="outline"
          :aria-label="t('shell.toggleNav')"
          :title="t('shell.toggleNav')"
          @click="appShell.toggleSidebar"
        >
          <IconMenu2 :size="20" :stroke-width="1.8" aria-hidden="true" />
        </Button>
      </div>
    </div>
  </header>
</template>
