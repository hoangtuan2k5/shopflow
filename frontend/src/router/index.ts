import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { roleRoutes } from './roleRoutes'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/customer',
    },
    {
      path: '/',
      component: AppLayout,
      children: roleRoutes,
    },
  ] satisfies RouteRecordRaw[],
})

export default router
