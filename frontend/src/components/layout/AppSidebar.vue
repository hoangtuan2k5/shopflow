<script setup lang="ts">
import type { Component } from 'vue'
import { IconBuildingWarehouse, IconChartBar, IconShoppingBag } from '@tabler/icons-vue'
import { RouterLink } from 'vue-router'
import { cn } from '@/lib/utils'
import { type RoleKey, useAppShellStore } from '@/stores/appShell'

const appShell = useAppShellStore()

const roleIcons: Record<RoleKey, Component> = {
  customer: IconShoppingBag,
  warehouse: IconBuildingWarehouse,
  'shop-owner': IconChartBar,
}
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
            'flex items-center gap-3 rounded-md border border-transparent px-3 py-2 text-sm transition-colors hover:border-border hover:bg-card',
            role.key === appShell.activeRoleKey && 'border-border bg-card text-primary shadow-sm',
          )
        "
      >
        <component
          :is="roleIcons[role.key]"
          class="shrink-0"
          :size="20"
          :stroke-width="1.8"
          aria-hidden="true"
        />
        <span class="min-w-0">
          <span class="block font-medium">{{ role.label }}</span>
          <span class="block text-xs text-muted-foreground">{{ role.description }}</span>
        </span>
      </RouterLink>
    </nav>
  </aside>
</template>
