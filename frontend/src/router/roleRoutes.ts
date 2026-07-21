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
    component: () => import('@/views/catalog/CatalogView.vue'),
    meta: {
      role: 'customer',
      title: 'Product catalog',
      description: 'Browse products currently available from ShopFlow.',
    },
  },
  {
    path: 'warehouse',
    name: 'warehouse',
    component: () => import('@/views/inventory/InventoryView.vue'),
    meta: {
      role: 'warehouse',
      title: 'Warehouse',
      description: 'Prepare inventory, receiving and fulfillment workflows.',
    },
  },
  {
    path: 'warehouse/deliveries',
    name: 'warehouse-deliveries',
    component: () => import('@/views/delivery/DeliveryView.vue'),
    meta: {
      role: 'warehouse',
      title: 'Delivery management',
      description: 'Prepare and complete paid-order deliveries.',
    },
  },
  {
    path: 'shop-owner',
    name: 'shop-owner',
    component: () => import('@/views/delivery/DeliveryView.vue'),
    meta: {
      role: 'shop-owner',
      title: 'Delivery management',
      description: 'Review and advance paid-order deliveries.',
    },
  },
]
