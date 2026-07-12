import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type RoleKey = 'customer' | 'warehouse' | 'shop-owner'

export interface NavigationRole {
  key: RoleKey
  label: string
  path: string
  description: string
}

const roles: NavigationRole[] = [
  {
    key: 'customer',
    label: 'Customer',
    path: '/customer',
    description: 'Storefront',
  },
  {
    key: 'warehouse',
    label: 'Warehouse',
    path: '/warehouse',
    description: 'Operations',
  },
  {
    key: 'shop-owner',
    label: 'Shop Owner',
    path: '/shop-owner',
    description: 'Management',
  },
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
