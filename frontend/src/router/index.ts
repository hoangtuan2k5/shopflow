import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import type { RoleKey } from '@/stores/appShell'
import { useSessionStore } from '@/stores/session'
import { roleRoutes } from './roleRoutes'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/customer',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
    },
    {
      path: '/',
      component: AppLayout,
      children: roleRoutes,
    },
  ] satisfies RouteRecordRaw[],
})

/** Chủ shop bao trùm quyền của kho (BR-17), nên vào được cả hai nhóm màn hình. */
export function canReach(roleKey: RoleKey | null, routeRole: RoleKey) {
  if (routeRole === 'customer') return true
  if (roleKey === 'shop-owner') return true
  return roleKey === routeRole
}

router.beforeEach(async (to) => {
  const session = useSessionStore()
  await session.restore()

  if (to.name === 'login') {
    return session.isAuthenticated ? session.homePath : true
  }

  const routeRole = to.meta.role as RoleKey | undefined
  if (!routeRole || routeRole === 'customer') return true

  if (!session.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return canReach(session.roleKey, routeRole) ? true : session.homePath
})

export default router
