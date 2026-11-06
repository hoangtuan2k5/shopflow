import { expect, test } from '@playwright/test'
import { mockSession } from './session'

const challenge = {
  challengeId: '77646dc8-5cab-42c9-9c5f-99f49123c445',
  message: 'If eligible, a code will be sent to the supplied email',
  expiresInSeconds: 600,
  resendAfterSeconds: 60,
}

async function requestCode(page: import('@playwright/test').Page) {
  await page.getByLabel('Email', { exact: true }).fill('Buyer@Example.com')
  await page.getByRole('button', { name: 'Send reset code' }).click()
  await expect(page.getByRole('heading', { name: 'Choose a new password' })).toBeVisible()
}
async function fillReset(page: import('@playwright/test').Page) {
  await page.getByLabel('Verification code').fill('012345')
  await page.getByLabel('New password', { exact: true }).fill('Newpass2!')
  await page.getByLabel('Confirm new password', { exact: true }).fill('Newpass2!')
}

test.beforeEach(async ({ page }) => {
  await mockSession(page, null)
})

test('Forgot password link leads to a combined reset form without auto-login', async ({ page }) => {
  await page.route('**/api/auth/password/forgot', async (route) => {
    expect(route.request().headers()['accept-language']).toBe('en')
    expect(route.request().postDataJSON()).toEqual({ email: 'buyer@example.com' })
    await route.fulfill({ status: 202, json: challenge })
  })
  await page.route('**/api/auth/password/reset', async (route) => {
    expect(route.request().headers()['accept-language']).toBe('en')
    expect(route.request().postDataJSON()).toEqual({
      challengeId: challenge.challengeId,
      otp: '012345',
      newPassword: 'Newpass2!',
    })
    await route.fulfill({ status: 204 })
  })
  await page.goto('/login')
  await page.getByRole('link', { name: 'Forgot password?' }).click()
  await requestCode(page)
  await expect(page.getByLabel('Verification code')).toBeFocused()
  await expect(page.getByRole('button', { name: /Send another code in/ })).toBeDisabled()
  await fillReset(page)
  expect(
    await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage })),
  ).not.toMatch(/Newpass2|012345|77646dc8|buyer@example/i)
  await page.getByRole('button', { name: 'Reset password', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Password updated' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Back to sign in' })).toBeFocused()
  await expect(page).toHaveURL(/\/forgot-password$/)
  await page.getByRole('link', { name: 'Back to sign in' }).click()
  await expect(page).toHaveURL(/\/login$/)
})

test('Mobile validation supports Vietnamese and never submits weak passwords', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/auth/password/forgot', (route) =>
    route.fulfill({ status: 202, json: challenge }),
  )
  let resetCalls = 0
  await page.route('**/api/auth/password/reset', async (route) => {
    resetCalls++
    await route.fulfill({ status: 204 })
  })
  await page.goto('/forgot-password')
  await page.getByRole('button', { name: 'Send reset code' }).click()
  await expect(page.getByText('Enter a valid email address.')).toBeVisible()
  await requestCode(page)
  await fillReset(page)
  await page.getByLabel('New password', { exact: true }).fill('weak')
  await page.getByRole('button', { name: 'Reset password', exact: true }).click()
  await expect(page.getByText('Use the password requirements shown below.')).toBeVisible()
  expect(resetCalls).toBe(0)
  await page.getByRole('combobox', { name: 'Language' }).selectOption('vi')
  await expect(page.getByRole('heading', { name: 'Chọn mật khẩu mới' })).toBeVisible()
  await expect(page.getByLabel('Mật khẩu mới', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
})

test('Invalid code clears sensitive inputs; resend honors Retry-After; reload loses proof', async ({
  page,
}) => {
  await page.clock.install()
  await page.route('**/api/auth/password/forgot', (route) =>
    route.fulfill({ status: 202, json: challenge }),
  )
  await page.route('**/api/auth/password/reset', (route) =>
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
  await page.goto('/forgot-password')
  await requestCode(page)
  await fillReset(page)
  await page.getByRole('button', { name: 'Reset password', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText(
    'The code is invalid or expired. Check it or request another code.',
  )
  await expect(page.getByLabel('New password', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('Verification code')).toHaveValue('')
  await page.clock.fastForward(61_000)
  await page.getByRole('button', { name: 'Send another code', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText(
    'Too many attempts. Please wait before trying again.',
  )
  await expect(page.getByRole('button', { name: /Send another code in/ })).toBeDisabled()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Reset your password' })).toBeVisible()
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue('')
})

test('Unavailable configuration keeps the request generic and on the same page', async ({
  page,
}) => {
  await page.route('**/api/auth/password/forgot', (route) =>
    route.fulfill({
      status: 503,
      json: { message: 'Authentication service unavailable', status: 503, fieldErrors: {} },
    }),
  )
  await page.goto('/forgot-password')
  await page.getByLabel('Email', { exact: true }).fill('unknown@example.com')
  await page.getByRole('button', { name: 'Send reset code' }).click()
  await expect(page.getByRole('alert')).toHaveText(
    'We could not complete this request. Please try again later.',
  )
  await expect(page).toHaveURL(/\/forgot-password$/)
})
