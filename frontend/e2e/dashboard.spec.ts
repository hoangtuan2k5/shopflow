import { expect, test, type Page, type Route } from '@playwright/test'
import { mockSession } from './session'

function fixture(url: string) {
  const q = new URL(url).searchParams
  const from = q.get('from') ?? '2026-10-01',
    to = q.get('to') ?? '2026-10-02'
  return {
    from,
    to,
    groupBy: q.get('groupBy') ?? 'day',
    timeZone: 'Asia/Ho_Chi_Minh',
    paidAmount: '9007199254740993',
    orderCount: 3,
    processingOrders: 24,
    lowStockProducts: 8,
    series: [
      { date: from, paidAmount: '9007199254740993', orderCount: 2 },
      { date: to, paidAmount: '0', orderCount: 1 },
    ],
    statuses: [
      { status: 'PENDING_PAYMENT', count: 1 },
      { status: 'PAID', count: 2 },
      { status: 'PAYMENT_FAILED', count: 0 },
      { status: 'CANCELLED', count: 0 },
    ],
    topProducts: [
      {
        productId: 37,
        productName: 'Winix HEPA filter – purchase snapshot',
        quantity: 2,
        paidAmount: '9007199254740993',
      },
    ],
  }
}
async function fulfill(route: Route, body = fixture(route.request().url())) {
  await route.fulfill({ json: body })
}
async function owner(page: Page) {
  await mockSession(page, 'SHOP_OWNER')
  await page.route('**/api/dashboard?*', (route) => fulfill(route))
}

test('Dashboard shows exact money, charts and keyboard-accessible table', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await owner(page)
  await page.goto('/shop-owner/dashboard')
  await expect(page.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible()
  await expect(page.getByTestId('dashboard-cards')).toContainText('9,007,199,254,740,993')
  await expect(page.locator('[data-slot="chart"] svg')).toHaveCount(3)
  const chart = page.locator('[data-slot="chart"]').first()
  await chart.hover({ position: { x: 70, y: 80 } })
  await expect(
    page.locator('[class*="tooltip"]').filter({ hasText: '9,007,199,254,740,993' }),
  ).toBeVisible()
  await expect(page.getByText('Winix HEPA filter – purchase snapshot')).toBeVisible()
  await page.locator('summary').filter({ hasText: 'View time-series data table' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('table')).toBeVisible()
  await expect(page.getByRole('table')).toContainText('9,007,199,254,740,993')
  expect(errors).toEqual([])
})

test('Dashboard filters submit dates, grouping, presets and reject oversized ranges', async ({
  page,
}) => {
  await owner(page)
  await page.goto('/shop-owner/dashboard')
  await page.getByLabel('From date', { exact: true }).fill('2026-07-01')
  await page.getByLabel('To date', { exact: true }).fill('2026-10-18')
  await page.getByLabel('Group by', { exact: true }).selectOption('month')
  const req = page.waitForRequest(
    (r) => r.url().includes('/api/dashboard?') && r.url().includes('groupBy=month'),
  )
  await page.getByRole('button', { name: 'Apply', exact: true }).click()
  const url = new URL((await req).url())
  expect(url.searchParams.get('from')).toBe('2026-07-01')
  expect(url.searchParams.get('to')).toBe('2026-10-18')
  await expect(page.getByTestId('dashboard-applied-period')).toHaveText('2026-07-01 — 2026-10-18')
  await expect(page.getByTestId('dashboard-cards')).toContainText('24')
  await page.getByLabel('From date', { exact: true }).fill('2020-01-01')
  await page.getByRole('button', { name: 'Apply', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('at most 366 days')
  await expect(page.getByTestId('dashboard-applied-period')).toHaveText('2026-07-01 — 2026-10-18')
  await page.getByRole('button', { name: '7 days', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('Dashboard loading, failed request, explicit retry and empty state', async ({ page }) => {
  await mockSession(page, 'SHOP_OWNER')
  let release!: () => void
  const hold = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/dashboard?*', async (route) => {
    await hold
    await route.fulfill({ status: 500, json: { message: 'Unavailable', status: 500 } })
  })
  await page.goto('/shop-owner/dashboard')
  await expect(page.getByRole('status')).toHaveText('Preparing your overview…')
  release()
  await expect(page.getByRole('alert')).toContainText('Unable to load dashboard data')
  await page.route('**/api/dashboard?*', (route) => {
    const d = fixture(route.request().url())
    return fulfill(route, {
      ...d,
      paidAmount: '0',
      orderCount: 0,
      series: d.series.map((p) => ({ ...p, paidAmount: '0', orderCount: 0 })),
      statuses: d.statuses.map((s) => ({ ...s, count: 0 })),
      topProducts: [],
    })
  })
  await page.getByRole('button', { name: 'Try again', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText(
    'No orders or successful payments in this period.',
  )
  await expect(page.getByText('No paid products in this period.')).toBeVisible()
  await expect(page.getByTestId('dashboard-cards')).toContainText('24')
})

test('Dashboard ignores an obsolete slow response after filters change', async ({ page }) => {
  await owner(page)
  await page.goto('/shop-owner/dashboard')
  await expect(page.getByTestId('dashboard-cards')).toBeVisible()
  let release!: () => void
  const hold = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/dashboard?*', async (route) => {
    if (new URL(route.request().url()).searchParams.get('from') === '2026-01-01') await hold
    await fulfill(route)
  })
  await page.getByLabel('From date', { exact: true }).fill('2026-01-01')
  await page.getByLabel('To date', { exact: true }).fill('2026-01-02')
  const req = page.waitForRequest((r) => r.url().includes('from=2026-01-01'))
  await page.getByRole('button', { name: 'Apply', exact: true }).click()
  await req
  await page.getByLabel('From date', { exact: true }).fill('2026-02-01')
  await page.getByLabel('To date', { exact: true }).fill('2026-02-02')
  await page.getByRole('button', { name: 'Apply', exact: true }).click()
  await expect(page.getByTestId('dashboard-applied-period')).toHaveText('2026-02-01 — 2026-02-02')
  release()
  await expect(page.getByTestId('dashboard-applied-period')).toHaveText('2026-02-01 — 2026-02-02')
})

for (const width of [360, 390, 1440])
  test(`Dashboard Vietnamese layout at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 })
    await page.addInitScript(() => localStorage.setItem('shopflow.locale', 'vi'))
    await owner(page)
    await page.goto('/shop-owner/dashboard')
    await expect(page.getByRole('heading', { name: 'Tổng quan', exact: true })).toBeVisible()
    await expect(page.getByText('Winix HEPA filter – purchase snapshot')).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await expect(page.getByTestId('dashboard-cards')).toContainText('không lọc theo ngày')
    const bounds = await page.locator('[data-slot="chart"]').evaluateAll((elements) =>
      elements.map((element) => {
        const svg = element.querySelector('svg')
        return {
          wrapper: element.getBoundingClientRect().height,
          svg: svg?.getBoundingClientRect().height ?? 0,
        }
      }),
    )
    for (const bound of bounds) expect(bound.svg).toBeLessThanOrEqual(bound.wrapper + 1)
  })

test('Dashboard never fetches data for anonymous, customer or warehouse callers', async ({
  page,
}) => {
  let calls = 0
  await page.route('**/api/dashboard?*', (route) => {
    calls++
    return fulfill(route)
  })
  await page.route('**/api/inventory', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/products?*', (route) =>
    route.fulfill({ json: { items: [], nextCursor: null } }),
  )
  for (const role of [null, 'CUSTOMER', 'WAREHOUSE'] as const) {
    await mockSession(page, role)
    await page.goto('/shop-owner/dashboard')
    await expect(page).not.toHaveURL((url) => url.pathname === '/shop-owner/dashboard')
    await expect(page.getByRole('link', { name: 'Overview', exact: true })).toHaveCount(0)
  }
  expect(calls).toBe(0)
})
