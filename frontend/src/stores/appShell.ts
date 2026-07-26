import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type RoleKey = 'customer' | 'warehouse' | 'shop-owner'

export interface NavigationRole {
  key: RoleKey
  path: string
}

const roles: NavigationRole[] = [
  { key: 'customer', path: '/customer' },
  { key: 'warehouse', path: '/warehouse' },
  { key: 'shop-owner', path: '/shop-owner' },
]

export const useAppShellStore = defineStore('appShell', () => {
  const activeRoleKey = ref<RoleKey>('customer')
  const sidebarOpen = ref(false)

  const activeRole = computed(
    () => roles.find((role) => role.key === activeRoleKey.value) ?? roles[0],
  )

  function setActiveRole(role: RoleKey) {
    activeRoleKey.value = role
  }

  function toggleSidebar() {
    sidebarOpen.value = !sidebarOpen.value
  }

  return {
    activeRole,
    activeRoleKey,
    roles,
    setActiveRole,
    sidebarOpen,
    toggleSidebar,
  }
})
