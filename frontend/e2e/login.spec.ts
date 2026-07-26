import { expect, test, type Page, type Route } from '@playwright/test'
import { mockSession } from './session'

const WAREHOUSE_USER = {
  userId: 2,
  username: 'warehouse',
  displayName: 'Nhân viên kho demo',
  role: 'WAREHOUSE',
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

function collectBrowserErrors(page: Page) {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  return errors
}

test('Signing in lands on the warehouse workspace and shows who is signed in', async ({ page }) => {
  const browserErrors = collectBrowserErrors(page)
  await mockSession(page, null)
  await page.route('**/api/inventory', (route) => fulfillJson(route, []))

  let submitted: unknown = null
  await page.route('**/api/auth/login', async (route) => {
    submitted = route.request().postDataJSON()
    await mockSession(page, 'WAREHOUSE')
    await fulfillJson(route, WAREHOUSE_USER)
  })

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()

  await page.getByLabel('Username').fill('warehouse')
  await page.getByLabel('Password').fill('Warehouse@2026')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page).toHaveURL(/\/warehouse$/)
  await expect(page.getByRole('heading', { name: 'Inventory management' })).toBeVisible()
  await expect(page.getByText('Nhân viên kho demo')).toBeVisible()
  expect(submitted).toEqual({ username: 'warehouse', password: 'Warehouse@2026' })

  expect(browserErrors).toEqual([])
})

test('Rejected credentials keep the username and clear only the password', async ({ page }) => {
  await mockSession(page, null)
  await page.route('**/api/auth/login', (route) =>
    fulfillJson(
      route,
      { message: 'Invalid username or password', status: 401, fieldErrors: {} },
      401,
    ),
  )

  await page.goto('/login')
  await page.getByLabel('Username').fill('warehouse')
  await page.getByLabel('Password').fill('wrong-password')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page.getByRole('alert')).toHaveText('Invalid username or password.')
  await expect(page.getByLabel('Username')).toHaveValue('warehouse')
  await expect(page.getByLabel('Password')).toHaveValue('')
})

test('Empty credentials are rejected before any request leaves the browser', async ({ page }) => {
  await mockSession(page, null)
  let calls = 0
  await page.route('**/api/auth/login', async (route) => {
    calls += 1
    await fulfillJson(route, WAREHOUSE_USER)
  })

  await page.goto('/login')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page.getByText('Enter your username.')).toBeVisible()
  await expect(page.getByText('Enter your password.')).toBeVisible()
  expect(calls).toBe(0)
})

test('An existing session survives a reload and ends on sign out', async ({ page }) => {
  await mockSession(page, 'WAREHOUSE')
  await page.route('**/api/inventory', (route) => fulfillJson(route, []))
  await page.route('**/api/auth/logout', (route) => route.fulfill({ status: 204, body: '' }))

  await page.goto('/warehouse')
  await expect(page.getByText('Nhân viên kho demo')).toBeVisible()

  await page.reload()
  await expect(page.getByText('Nhân viên kho demo')).toBeVisible()

  await mockSession(page, null)
  await page.getByRole('button', { name: 'Sign out' }).click()

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
})

test('Signing in while already signed in redirects away from the login page', async ({ page }) => {
  await mockSession(page, 'SHOP_OWNER')
  await page.route('**/api/deliveries', (route) => fulfillJson(route, []))

  await page.goto('/login')

  await expect(page).toHaveURL(/\/shop-owner$/)
})

test('The login form is available in Vietnamese', async ({ page }) => {
  await mockSession(page, null)
  await page.goto('/login')

  await page.getByRole('combobox', { name: 'Language' }).selectOption('vi')

  await expect(page.getByRole('heading', { name: 'Chào mừng trở lại' })).toBeVisible()
  await expect(page.getByLabel('Tên đăng nhập')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Đăng nhập' })).toBeVisible()
})
