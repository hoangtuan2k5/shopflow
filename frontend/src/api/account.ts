import { i18n } from '@/i18n'
import { request } from './httpClient'

export interface AccountProfile {
  displayName: string
  email: string | null
  capabilities: { manageDelivery: boolean; changePassword: boolean; setPassword: boolean }
}
export function getAccountProfile() {
  return request<AccountProfile>({ method: 'GET', url: '/auth/account' })
}

export function setupPassword(challengeId?: string) {
  return request<import('./auth').ChallengeResponse>({
    method: 'POST',
    url: '/auth/account/password/setup' + (challengeId ? '/resend' : ''),
    data: challengeId ? { challengeId } : {},
    headers: { 'Accept-Language': i18n.global.locale.value },
  })
}
export function confirmPasswordSetup(challengeId: string, otp: string, newPassword: string) {
  return request<void>({
    method: 'POST',
    url: '/auth/account/password/setup/confirm',
    data: { challengeId, otp, newPassword },
    headers: { 'Accept-Language': i18n.global.locale.value },
  })
}

export function changePassword(currentPassword: string, newPassword: string) {
  return request<void>({
    method: 'POST',
    url: '/auth/account/password/change',
    data: { currentPassword, newPassword },
    headers: { 'Accept-Language': i18n.global.locale.value },
  })
}

export interface DeliveryDetails {
  receiverName: string
  phone: string
  addressLine: string
  district: string | null
  city: string
}
export interface DeliveryProfile {
  delivery: DeliveryDetails | null
}
export function getDeliveryProfile() {
  return request<DeliveryProfile>({ method: 'GET', url: '/auth/account/delivery' })
}
export function saveDeliveryProfile(data: DeliveryDetails) {
  return request<DeliveryProfile>({ method: 'PUT', url: '/auth/account/delivery', data })
}
export function deleteDeliveryProfile() {
  return request<void>({ method: 'DELETE', url: '/auth/account/delivery' })
}
