import { request } from './httpClient'

export interface DashboardPoint {
  date: string
  paidAmount: string
  orderCount: number
}
export interface DashboardData {
  from: string
  to: string
  groupBy: 'day' | 'month'
  timeZone: string
  paidAmount: string
  orderCount: number
  processingOrders: number
  lowStockProducts: number
  series: DashboardPoint[]
  statuses: { status: string; count: number }[]
  topProducts: { productId: number; productName: string; quantity: number; paidAmount: string }[]
}
export interface DashboardFilter {
  from: string
  to: string
  groupBy: 'day' | 'month'
}
export function getDashboard(filter: DashboardFilter, signal?: AbortSignal) {
  return request<DashboardData>({ method: 'GET', url: '/dashboard', params: filter, signal })
}
