import { expect, test } from '@playwright/test'
import { mockSession } from './session'

test.beforeEach(async ({ page }) => {
  await mockSession(page, null)
  await page.route('**/api/products*', (route) =>
    route.fulfill({ json: { items: [], hasMore: false, nextCursor: null } }),
  )
})

test('Google start contains only a validated internal destination', async ({ page }) => {
  await page.goto('/login?redirect=/customer?checkout=1')
  const google = page.getByRole('link', { name: 'Continue with Google' })
  await expect(google.locator('img')).toHaveAttribute('src', '/brand/google-g.png')
  await expect(google.locator('img')).toHaveJSProperty('naturalWidth', 200)
  await page.screenshot({ path: '/tmp/shopflow-google-button.png' })
  await expect(google).toHaveAttribute(
    'href',
    '/api/oauth2/authorization/google?returnTo=%2Fcustomer%3Fcheckout%3D1',
  )
  await page.goto('/login?redirect=https://evil.example')
  await expect(google).toHaveAttribute(
    'href',
    '/api/oauth2/authorization/google?returnTo=%2Fcustomer',
  )
})

test('Google collision stays visible during account switching and requires consent', async ({
  page,
}) => {
  await mockSession(page, 'WAREHOUSE')
  let attempts = 0
  await page.route('**/api/auth/google/link', async (route) => {
    attempts += 1
    expect(route.request().postDataJSON()).toEqual({
      password: attempts === 1 ? 'wrong' : 'Existing1!',
    })
    if (attempts === 1) {
      await route.fulfill({
        status: 401,
        json: { message: 'Invalid username or password', status: 401, fieldErrors: {} },
      })
      return
    }
    await mockSession(page, 'CUSTOMER')
    await route.fulfill({
      headers: { Location: '/customer?checkout=1' },
      json: { userId: 27, username: 'u_google', displayName: 'Linked buyer', role: 'CUSTOMER' },
    })
  })
  await page.goto('/login?google=link_required')
  await expect(page.getByRole('heading', { name: 'Link your Google account' })).toBeVisible()
  await expect(page.getByLabel('Username')).toHaveCount(0)
  await expect(page.getByText(/Enter its current password/)).toBeVisible()
  await page.getByLabel('Password').fill('wrong')
  await page.getByLabel('Password').press('Enter')
  await expect(page.getByRole('alert')).toHaveText('Invalid username or password.')
  await expect(page.getByLabel('Password')).toHaveValue('')
  await page.getByLabel('Password').fill('Existing1!')
  await page.getByRole('button', { name: 'Link Google and sign in' }).click()
  await expect(page).toHaveURL(/\/customer\?checkout=1$/)
  expect(attempts).toBe(2)
})

test('Expired link clears the password and never stores proof or credentials', async ({ page }) => {
  await page.route('**/api/auth/google/link', (route) =>
    route.fulfill({
      status: 400,
      json: { message: 'Invalid or expired code', status: 400, fieldErrors: {} },
    }),
  )
  await page.goto('/login?google=link_required')
  await page.getByLabel('Password').fill('Existing1!')
  await page.getByRole('button', { name: 'Link Google and sign in' }).click()
  await expect(page.getByRole('alert')).toContainText('Start Google sign-in again.')
  await expect(page.getByLabel('Password')).toHaveValue('')
  expect(
    await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage })),
  ).not.toMatch(/Existing1|subject|nonce|access_token|id_token/i)
  await page.getByRole('link', { name: 'Use another sign-in method' }).click()
  await expect(page.getByRole('link', { name: 'Continue with Google' })).toBeVisible()
})

test('Password fallback honors a safe server Location after Google cancellation', async ({
  page,
}) => {
  await page.route('**/api/auth/login', (route) =>
    route.fulfill({
      headers: { Location: '/customer?checkout=1' },
      json: { userId: 27, username: 'buyer', displayName: 'Buyer', role: 'CUSTOMER' },
    }),
  )
  await page.goto('/login?google=failed')
  await expect(page.getByRole('alert')).toContainText('Google sign-in could not be completed.')
  await page.getByLabel('Username').fill('buyer@example.com')
  await page.getByLabel('Password').fill('Existing1!')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL(/\/customer\?checkout=1$/)
})

test('Vietnamese Google link form fits mobile and validates before sending', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  let calls = 0
  await page.route('**/api/auth/google/link', () => {
    calls += 1
  })
  await page.goto('/login?google=link_required')
  await page.getByRole('combobox', { name: 'Language' }).selectOption('vi')
  await expect(page.getByRole('heading', { name: 'Liên kết tài khoản Google' })).toBeVisible()
  await page.getByRole('button', { name: 'Liên kết Google và đăng nhập' }).click()
  expect(calls).toBe(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
