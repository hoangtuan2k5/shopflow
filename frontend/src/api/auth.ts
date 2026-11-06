import { httpClient, request } from './httpClient'
import { i18n } from '@/i18n'

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

async function authenticate(url: string, data: LoginRequest | { password: string }) {
  const response = await httpClient.post<AuthenticatedUser>(url, data)
  return { user: response.data, returnTo: response.headers.location as string | undefined }
}

export function login(credentials: LoginRequest) {
  return authenticate('/auth/login', credentials)
}

export async function linkGoogleAccount(password: string) {
  await getSession()
  return authenticate('/auth/google/link', { password })
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
  return request<ChallengeResponse>({
    method: 'POST',
    url: '/auth/registrations',
    data,
    headers: { 'Accept-Language': i18n.global.locale.value },
  })
}

export async function verifyRegistration(challengeId: string, otp: string) {
  await getSession()
  return request<void>({
    method: 'POST',
    url: '/auth/registrations/verify',
    data: { challengeId, otp },
  })
}

export async function resendOtp(challengeId: string) {
  await getSession()
  return request<ChallengeResponse>({
    method: 'POST',
    url: '/auth/otp/resend',
    headers: { 'Accept-Language': i18n.global.locale.value },
    data: { challengeId },
  })
}

export async function requestPasswordReset(email: string) {
  await getSession()
  return request<ChallengeResponse>({
    method: 'POST',
    url: '/auth/password/forgot',
    headers: { 'Accept-Language': i18n.global.locale.value },
    data: { email },
  })
}

export async function resetPassword(data: {
  challengeId: string
  otp: string
  newPassword: string
}) {
  await getSession()
  return request<void>({
    method: 'POST',
    url: '/auth/password/reset',
    data,
    headers: { 'Accept-Language': i18n.global.locale.value },
  })
}
