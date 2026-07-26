<script setup lang="ts">
import { computed, type Component } from 'vue'
import { IconBuildingWarehouse, IconChartBar, IconShoppingBag } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { canReach } from '@/router'
import { cn } from '@/lib/utils'
import { type RoleKey, useAppShellStore } from '@/stores/appShell'
import { useSessionStore } from '@/stores/session'

const { t } = useI18n()
const appShell = useAppShellStore()
const session = useSessionStore()

// Hiện link tới nơi người dùng sẽ bị bật ra ngay là điều hướng lừa người dùng.
const visibleRoles = computed(() =>
  appShell.roles.filter((role) => canReach(session.roleKey, role.key)),
)

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
    <nav :aria-label="t('shell.roleNav')" class="grid gap-2">
      <RouterLink
        v-for="role in visibleRoles"
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
          <span class="block font-medium">{{ t(`shell.roles.${role.key}.label`) }}</span>
          <span class="block text-xs text-muted-foreground">
            {{ t(`shell.roles.${role.key}.description`) }}
          </span>
        </span>
      </RouterLink>
    </nav>
  </aside>
</template>
