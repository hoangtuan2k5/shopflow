import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
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

// Đọc phiên đang có trước điều hướng đầu tiên để tải lại trang không bị mất đăng nhập.
router.beforeEach(async (to) => {
  const session = useSessionStore()
  await session.restore()

  if (to.name === 'login' && session.isAuthenticated) {
    return session.homePath
  }
  return true
})

export default router
