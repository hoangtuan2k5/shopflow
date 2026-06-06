<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { useAppShellStore } from '@/stores/appShell'

const appShell = useAppShellStore()
</script>

<template>
  <header class="border-b border-border bg-card">
    <div class="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-6">
      <div>
        <p class="text-sm font-medium text-primary">Shopflow</p>
        <h1 class="text-lg font-semibold tracking-normal">Frontend workspace</h1>
      </div>

      <Button class="lg:hidden" variant="outline" size="sm" @click="appShell.toggleSidebar">
        Menu
      </Button>
    </div>
  </header>
</template>
