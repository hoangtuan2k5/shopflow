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

test('An anonymous visitor is sent to sign in and returns to where they were headed', async ({
  page,
}) => {
  await mockSession(page, null)
  await page.route('**/api/inventory', (route) => fulfillJson(route, []))
  await page.route('**/api/auth/login', async (route) => {
    await mockSession(page, 'WAREHOUSE')
    await fulfillJson(route, WAREHOUSE_USER)
  })

  await page.goto('/warehouse/deliveries')

  await expect(page).toHaveURL(/\/login\?redirect=\/warehouse\/deliveries$/)

  await page.getByLabel('Username').fill('warehouse')
  await page.getByLabel('Password').fill('Warehouse@2026')
  await page.getByRole('button', { name: 'Sign in' }).click()

  await expect(page).toHaveURL(/\/warehouse\/deliveries$/)
})

test('Warehouse staff cannot reach shop owner screens and see no link to them', async ({
  page,
}) => {
  await mockSession(page, 'WAREHOUSE')
  await page.route('**/api/inventory', (route) => fulfillJson(route, []))
  await page.route('**/api/deliveries', (route) => fulfillJson(route, []))

  await page.goto('/shop-owner')

  await expect(page).toHaveURL(/\/warehouse$/)
  await expect(page.getByRole('link', { name: /Shop Owner/ })).toHaveCount(0)
  await expect(page.getByRole('link', { name: /Warehouse/ })).toBeVisible()
})

test('A shop owner reaches warehouse screens because their role covers them', async ({ page }) => {
  await mockSession(page, 'SHOP_OWNER')
  await page.route('**/api/inventory', (route) => fulfillJson(route, []))

  await page.goto('/warehouse')

  await expect(page).toHaveURL(/\/warehouse$/)
  await expect(page.getByRole('heading', { name: 'Inventory management' })).toBeVisible()
})

test('The storefront stays open to guests', async ({ page }) => {
  await mockSession(page, null)
  await page.route('**/api/products', (route) => fulfillJson(route, []))

  await page.goto('/customer')

  await expect(page).toHaveURL(/\/customer$/)
  await expect(page.getByRole('heading', { name: 'Products for you' })).toBeVisible()
})

test('A session that expires mid-use lands the operator back on the sign in page', async ({
  page,
}) => {
  await mockSession(page, 'WAREHOUSE')
  await page.route('**/api/inventory', (route) =>
    fulfillJson(route, { message: 'Unauthorized', status: 401, fieldErrors: {} }, 401),
  )

  await page.goto('/warehouse')

  await expect(page).toHaveURL(/\/login\?redirect=\/warehouse$/)
})

test('The storefront offers a way in for staff and a way back for signed-in users', async ({
  page,
}) => {
  await mockSession(page, null)
  await page.route('**/api/products', (route) => fulfillJson(route, []))

  await page.goto('/customer')
  await page.getByRole('link', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/login$/)

  await mockSession(page, 'SHOP_OWNER')
  await page.route('**/api/deliveries', (route) => fulfillJson(route, []))
  await page.goto('/customer')

  await page.getByRole('link', { name: 'Workspace' }).click()
  await expect(page).toHaveURL(/\/shop-owner$/)
})
