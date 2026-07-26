<script setup lang="ts">
import { IconMenu2 } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import { Button } from '@/components/ui/button'
import { useAppShellStore } from '@/stores/appShell'

const { t } = useI18n()
const appShell = useAppShellStore()
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
        <LanguageSwitcher />
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
