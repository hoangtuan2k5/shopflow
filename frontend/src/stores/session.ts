import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getSession,
  login as loginRequest,
  linkGoogleAccount,
  logout as logoutRequest,
  type AuthenticatedUser,
  type LoginRequest,
  type UserRole,
} from '@/api'
import type { RoleKey } from './appShell'
import { queryClient } from '@/plugins/query'

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

  async function acceptSignIn(result: Awaited<ReturnType<typeof loginRequest>>) {
    queryClient.clear()
    user.value = result.user
    await getSession()
    resolved.value = true
    return result.returnTo
  }

  async function signIn(credentials: LoginRequest) {
    return acceptSignIn(await loginRequest(credentials))
  }

  async function linkGoogle(password: string) {
    return acceptSignIn(await linkGoogleAccount(password))
  }

  /** Xoá phiên phía client khi server đã coi nó không còn hợp lệ; không gọi lại API. */
  function forget() {
    user.value = null
    resolved.value = true
  }

  async function signOut() {
    try {
      await logoutRequest()
      await getSession()
    } finally {
      user.value = null
      resolved.value = true
    }
  }

  return {
    user,
    resolved,
    isAuthenticated,
    roleKey,
    homePath,
    restore,
    signIn,
    linkGoogle,
    signOut,
    forget,
  }
})
