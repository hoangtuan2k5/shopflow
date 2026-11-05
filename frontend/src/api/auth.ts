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

export interface RegistrationRequest {
  email: string
  password: string
  displayName: string
}

export interface ChallengeResponse {
  challengeId: string
  message: string
  expiresInSeconds: number
  resendAfterSeconds: number
}

export async function registerAccount(data: RegistrationRequest) {
  await getSession()
  return request<ChallengeResponse>({ method: 'POST', url: '/auth/registrations', data })
}

export async function verifyRegistration(challengeId: string, otp: string) {
  await getSession()
  return request<void>({
    method: 'POST',
    url: '/auth/registrations/verify',
    data: { challengeId, otp },
  })
}

export async function resendRegistrationCode(challengeId: string) {
  await getSession()
  return request<ChallengeResponse>({
    method: 'POST',
    url: '/auth/otp/resend',
    data: { challengeId },
  })
}
