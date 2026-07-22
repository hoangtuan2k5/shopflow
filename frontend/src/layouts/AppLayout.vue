<script setup lang="ts">
import { computed, watchEffect } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppContent from '@/components/layout/AppContent.vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import { type RoleKey, useAppShellStore } from '@/stores/appShell'

const route = useRoute()
const appShell = useAppShellStore()

const currentRole = computed(() => route.meta.role as RoleKey | undefined)

watchEffect(() => {
  if (currentRole.value) {
    appShell.setActiveRole(currentRole.value)
  }
})
</script>

<template>
  <div v-if="currentRole === 'customer'" class="min-h-screen bg-background text-foreground">
    <RouterView />
  </div>
  <div v-else class="min-h-screen bg-background text-foreground">
    <AppHeader />
    <div
      class="mx-auto grid min-h-[calc(100vh-4rem)] max-w-7xl grid-cols-1 lg:grid-cols-[16rem_1fr]"
    >
      <AppSidebar />
      <AppContent />
    </div>
  </div>
</template>
