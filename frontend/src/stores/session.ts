import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getSession,
  login as loginRequest,
  logout as logoutRequest,
  type AuthenticatedUser,
  type LoginRequest,
  type UserRole,
} from '@/api'
import type { RoleKey } from './appShell'

const roleKeys: Record<UserRole, RoleKey> = {
  CUSTOMER: 'customer',
  WAREHOUSE: 'warehouse',
  SHOP_OWNER: 'shop-owner',
}

export const useSessionStore = defineStore('session', () => {
  const user = ref<AuthenticatedUser | null>(null)
  const resolved = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const roleKey = computed<RoleKey | null>(() => (user.value ? roleKeys[user.value.role] : null))
  const homePath = computed(() => (roleKey.value ? `/${roleKey.value}` : '/customer'))

  /** Đọc phiên hiện có một lần khi ứng dụng khởi động. Lỗi mạng coi như chưa đăng nhập. */
  async function restore() {
    if (resolved.value) return
    try {
      const session = await getSession()
      user.value = session.authenticated ? session.user : null
    } catch {
      user.value = null
    } finally {
      resolved.value = true
    }
  }

  async function signIn(credentials: LoginRequest) {
    user.value = await loginRequest(credentials)
    resolved.value = true
    return user.value
  }

  /** Xoá phiên phía client khi server đã coi nó không còn hợp lệ; không gọi lại API. */
  function forget() {
    user.value = null
    resolved.value = true
  }

  async function signOut() {
    try {
      await logoutRequest()
    } finally {
      user.value = null
      resolved.value = true
    }
  }

  return { user, resolved, isAuthenticated, roleKey, homePath, restore, signIn, signOut, forget }
})
