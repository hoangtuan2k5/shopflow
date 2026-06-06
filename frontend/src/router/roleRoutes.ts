import type { RouteRecordRaw } from 'vue-router'
import type { RoleKey } from '@/stores/appShell'

type RoleRoute = RouteRecordRaw & {
  meta: {
    role: RoleKey
    title: string
    description: string
  }
}

export const roleRoutes: RoleRoute[] = [
  {
    path: 'customer',
    name: 'customer',
    component: () => import('@/views/roles/RoleDashboardView.vue'),
    meta: {
      role: 'customer',
      title: 'Customer',
      description: 'Browse storefront and customer-facing order workflows.',
    },
  },
  {
    path: 'warehouse',
    name: 'warehouse',
    component: () => import('@/views/roles/RoleDashboardView.vue'),
    meta: {
      role: 'warehouse',
      title: 'Warehouse',
      description: 'Prepare inventory, receiving and fulfillment workflows.',
    },
  },
  {
    path: 'shop-owner',
    name: 'shop-owner',
    component: () => import('@/views/roles/RoleDashboardView.vue'),
    meta: {
      role: 'shop-owner',
      title: 'Shop Owner',
      description: 'Review sales, inventory and dashboard workflows.',
    },
  },
]
