import { request } from './httpClient'

export interface AccountProfile {
  displayName: string
  email: string | null
  capabilities: { manageDelivery: boolean; changePassword: boolean; setPassword: boolean }
}
export function getAccountProfile() {
  return request<AccountProfile>({ method: 'GET', url: '/auth/account' })
}
