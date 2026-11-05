import { expect, test } from '@playwright/test'
import { mockSession } from './session'

const challenge = {
  challengeId: '77646dc8-5cab-42c9-9c5f-99f49123c445',
  message: 'If eligible, a code will be sent to the supplied email',
  expiresInSeconds: 600,
  resendAfterSeconds: 60,
}

async function fillRegistration(page: import('@playwright/test').Page) {
  await page.getByLabel('Email', { exact: true }).fill('Buyer@Example.com')
  await page.getByLabel('Display name').fill('Buyer')
  await page.getByLabel('Password', { exact: true }).fill('Abcdef1!')
  await page.getByLabel('Confirm password').fill('Abcdef1!')
}

test.beforeEach(async ({ page }) => {
  await mockSession(page, null)
})

test('registration verifies without auto-login or storing credentials', async ({ page }) => {
  await page.route('**/api/auth/registrations', async (route) => {
    expect(route.request().postDataJSON()).toEqual({
      email: 'buyer@example.com',
      password: 'Abcdef1!',
      displayName: 'Buyer',
    })
    await route.fulfill({ json: challenge, status: 202 })
  })
  await page.route('**/api/auth/registrations/verify', async (route) => {
    expect(route.request().postDataJSON()).toEqual({
      challengeId: challenge.challengeId,
      otp: '012345',
    })
    await route.fulfill({ status: 204 })
  })
  await page.goto('/register?redirect=/customer?checkout=1')
  await fillRegistration(page)
  await page.getByRole('button', { name: 'Send verification code' }).click()
  await expect(page.getByRole('heading', { name: 'Verify your email' })).toBeVisible()
  await expect(page.getByLabel('Verification code')).toBeFocused()
  await expect(page.getByRole('button', { name: /Send another code in/ })).toBeDisabled()
  expect(
    await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage })),
  ).not.toMatch(/Abcdef1|77646dc8|buyer@example/)
  await page.getByLabel('Verification code').fill('012345')
  await page.getByRole('button', { name: 'Verify email', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Email verified' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Back to sign in' })).toBeFocused()
  await expect(page).toHaveURL(/register/)
  await page.getByRole('link', { name: 'Back to sign in' }).click()
  await expect(page).toHaveURL(/\/login\?redirect=/)
})

test('validation is local and translates on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  let calls = 0
  await page.route('**/api/auth/registrations', async () => {
    calls += 1
  })
  await page.goto('/register')
  await page.getByRole('button', { name: 'Send verification code' }).click()
  await expect(page.getByText('Enter a valid email address.')).toBeVisible()
  await expect(page.getByLabel('Email', { exact: true })).toBeFocused()
  await fillRegistration(page)
  await page.getByLabel('Password', { exact: true }).fill('abcdef1!')
  await page.getByRole('button', { name: 'Send verification code' }).click()
  await expect(page.getByText('Use the password requirements shown below.')).toBeVisible()
  expect(calls).toBe(0)
  await page.getByRole('combobox', { name: 'Language' }).selectOption('vi')
  await expect(page.getByRole('heading', { name: 'Tham gia ShopFlow' })).toBeVisible()
  await expect(page.getByText('Mật khẩu cần đáp ứng các yêu cầu bên dưới.')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
})

test('wrong code, rate limit, resend and reload preserve the contract', async ({ page }) => {
  await page.clock.install()
  await page.route('**/api/auth/registrations', (route) =>
    route.fulfill({ status: 202, json: challenge }),
  )
  await page.route('**/api/auth/registrations/verify', (route) =>
    route.fulfill({
      status: 400,
      json: { message: 'Invalid or expired code', status: 400, fieldErrors: {} },
    }),
  )
  await page.route('**/api/auth/otp/resend', (route) =>
    route.fulfill({
      status: 429,
      headers: { 'Retry-After': '60' },
      json: { message: 'Too many authentication attempts', status: 429, fieldErrors: {} },
    }),
  )
  await page.goto('/register')
  await fillRegistration(page)
  await page.getByRole('button', { name: 'Send verification code' }).click()
  await page.getByLabel('Verification code').fill('999999')
  await page.getByRole('button', { name: 'Verify email', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText(
    'The code is invalid or expired. Check it or request another code.',
  )
  await expect(page.getByLabel('Verification code')).toHaveValue('')
  await page.clock.fastForward(61_000)
  await page.getByRole('button', { name: 'Send another code', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText(
    'Too many attempts. Please wait before trying again.',
  )
  await expect(page.getByRole('button', { name: /Send another code in/ })).toBeDisabled()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Join ShopFlow' })).toBeVisible()
  await expect(page.getByLabel('Password', { exact: true })).toHaveValue('')
})

test('missing mail configuration shows a generic error without a redirect loop', async ({
  page,
}) => {
  await page.route('**/api/auth/registrations', (route) =>
    route.fulfill({
      status: 503,
      json: { message: 'Authentication service unavailable', status: 503, fieldErrors: {} },
    }),
  )
  await page.goto('/register')
  await fillRegistration(page)
  await page.getByRole('button', { name: 'Send verification code' }).click()
  await expect(page.getByRole('alert')).toHaveText(
    'We could not complete this request. Please try again later.',
  )
  await expect(page).toHaveURL(/\/register$/)
})
