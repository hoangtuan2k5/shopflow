import { request } from './httpClient'

export type UserRole = 'CUSTOMER' | 'WAREHOUSE' | 'SHOP_OWNER'

export interface AuthenticatedUser {
  userId: number
  username: string
  displayName: string
  role: UserRole
}

export interface LoginRequest {
  username: string
  password: string
}

export interface SessionResponse {
  authenticated: boolean
  user: AuthenticatedUser | null
}

export interface AuthErrorDetails {
  message: string
  status: number
  fieldErrors: Record<string, string>
}

export function login(credentials: LoginRequest) {
  return request<AuthenticatedUser>({
    method: 'POST',
    url: '/auth/login',
    data: credentials,
  })
}

export function logout() {
  return request<void>({
    method: 'POST',
    url: '/auth/logout',
  })
}

export function getSession() {
  return request<SessionResponse>({
    method: 'GET',
    url: '/auth/session',
  })
}
