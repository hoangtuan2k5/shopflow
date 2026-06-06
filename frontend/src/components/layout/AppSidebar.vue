<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { cn } from '@/lib/utils'
import { useAppShellStore } from '@/stores/appShell'

const appShell = useAppShellStore()
</script>

<template>
  <aside
    :class="
      cn(
        'border-b border-border bg-secondary/30 px-6 py-5 lg:block lg:border-b-0 lg:border-r',
        appShell.sidebarOpen ? 'block' : 'hidden',
      )
    "
  >
    <nav aria-label="Role navigation" class="grid gap-2">
      <RouterLink
        v-for="role in appShell.roles"
        :key="role.key"
        :to="role.path"
        :class="
          cn(
            'rounded-md border border-transparent px-3 py-2 text-sm transition-colors hover:border-border hover:bg-card',
            role.key === appShell.activeRoleKey && 'border-border bg-card shadow-sm',
          )
        "
      >
        <span class="block font-medium">{{ role.label }}</span>
        <span class="block text-xs text-muted-foreground">{{ role.description }}</span>
      </RouterLink>
    </nav>
  </aside>
</template>
