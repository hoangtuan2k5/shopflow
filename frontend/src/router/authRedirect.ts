import type { RoleKey } from '@/stores/appShell'
import { roleRoutes } from './roleRoutes'

export function authRedirect(value: unknown, role: RoleKey | null): string | null {
  if (typeof value !== 'string' || /[%\\\r\n]/.test(value)) return null
  if (value === '/customer' || value === '/customer?checkout=1') return value
  const destination = roleRoutes.find((route) => `/${route.path}` === value)
  if (!destination || destination.meta.role === 'customer') return null
  return role === 'shop-owner' || role === destination.meta.role ? value : null
}
