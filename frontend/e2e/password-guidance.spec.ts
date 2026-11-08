import { expect, test } from '@playwright/test'
import { mockSession } from './session'

const challenge = {
  challengeId: '77646dc8-5cab-42c9-9c5f-99f49123c445',
  message: 'Issued',
  expiresInSeconds: 600,
  resendAfterSeconds: 60,
}

for (const locale of ['en', 'vi'] as const) {
  for (const flow of ['register', 'recover', 'change', 'setup'] as const) {
    test(`${flow} gives plain ${locale} guidance for long Unicode passwords`, async ({ page }) => {
      await mockSession(page, flow === 'register' || flow === 'recover' ? null : 'CUSTOMER')
      await page.addInitScript((value) => localStorage.setItem('shopflow.locale', value), locale)
      await page.route('**/api/auth/account', (route) =>
        route.fulfill({
          json: {
            displayName: 'Buyer',
            email: 'buyer@example.com',
            capabilities: {
              manageDelivery: true,
              changePassword: flow === 'change',
              setPassword: flow === 'setup',
            },
          },
        }),
      )
      await page.route('**/api/auth/password/forgot', (route) =>
        route.fulfill({ status: 202, json: challenge }),
      )
      await page.route('**/api/auth/account/password/setup', (route) =>
        route.fulfill({ status: 202, json: challenge }),
      )
      let calls = 0
      const endpoint =
        flow === 'register'
          ? '/auth/registrations'
          : flow === 'recover'
            ? '/auth/password/reset'
            : `/auth/account/password/${flow === 'setup' ? 'setup/confirm' : 'change'}`
      await page.route('**/api' + endpoint, (route) => {
        calls++
        const data = route.request().postDataJSON()
        expect(data.password ?? data.newPassword).toBe('Aa1!' + 'á'.repeat(34))
        return route.fulfill(
          flow === 'register' ? { status: 202, json: challenge } : { status: 204 },
        )
      })
      let passwordId: string
      let confirmId: string
      if (flow === 'register') {
        await page.goto('/register')
        await page.locator('#register-email').fill('buyer@example.com')
        await page.locator('#register-displayName').fill('Buyer')
        passwordId = '#register-password'
        confirmId = '#register-confirm'
      } else if (flow === 'recover') {
        await page.goto('/forgot-password')
        await page.locator('#recovery-email').fill('buyer@example.com')
        await page.locator('button[type="submit"]').click()
        await page.locator('#recovery-otp').fill('123456')
        passwordId = '#recovery-password'
        confirmId = '#recovery-confirm'
      } else {
        await page.goto('/account/password')
        if (flow === 'setup') {
          await page
            .getByRole('button', {
              name: locale === 'en' ? 'Send verification code' : 'Gửi mã xác minh',
            })
            .click()
          await page.locator('#setup-otp').fill('123456')
        } else await page.locator('#current-password').fill('Oldpass1!')
        passwordId = '#new-password'
        confirmId = '#confirm-password'
      }
      await expect(page.locator('body')).not.toContainText(/byte|UTF-8/i)
      const tooLong = 'Aa1!' + 'á'.repeat(35)
      await page.locator(passwordId).fill(tooLong)
      await page.locator(confirmId).fill(tooLong)
      await page.locator('button[type="submit"]').last().click()
      await expect(
        page.getByText(
          locale === 'en'
            ? 'This password is too long. Please use a shorter password.'
            : 'Mật khẩu quá dài. Vui lòng dùng mật khẩu ngắn hơn.',
          { exact: true },
        ),
      ).toBeVisible()
      expect(calls).toBe(0)
      await page.locator(passwordId).fill('Aa1!' + 'á'.repeat(34))
      await page.locator(confirmId).fill('Aa1!' + 'á'.repeat(34))
      await page.locator('button[type="submit"]').last().click()
      await expect.poll(() => calls).toBe(1)
    })
  }
}
