import { expect, test } from '@playwright/test'
import { mockSession } from './session'

const product = {
  id: 1,
  name: 'Air filter',
  price: 100000,
  stockStatus: 'IN_STOCK',
  imageUrl: null,
}
const orderRef = 'f5697b8f-b76e-480d-bc99-f96dad071a89'

for (const locale of ['en', 'vi'] as const) {
  for (const result of ['SUCCESS', 'FAILED', 'EXPIRED', 'NETWORK'] as const) {
    test(`Payment summary and actions: ${locale} ${result}`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width: result === 'SUCCESS' ? 1280 : 390, height: 900 })
      await page.addInitScript((value) => localStorage.setItem('shopflow.locale', value), locale)
      await mockSession(page, 'CUSTOMER')
      await page.route(/\/api\/products(?:\?.*)?$/, (route) =>
        route.fulfill({ json: { items: [product], nextCursor: null } }),
      )
      await page.route('**/api/products/1', (route) => route.fulfill({ json: product }))
      let orders = 0
      let payments = 0
      await page.route('**/api/orders', (route) => {
        orders += 1
        const body = route.request().postDataJSON()
        return route.fulfill({
          status: 201,
          json: {
            ...body,
            id: 41 + orders,
            orderRef,
            status: 'PENDING_PAYMENT',
            deliveryStatus: 'NONE',
            totalAmount: 100000,
            createdAt: '2026-11-09T00:00:00Z',
          },
        })
      })
      let releasePayment: () => void = () => {}
      await page.route(`**/api/orders/${orderRef}/payments`, async (route) => {
        payments += 1
        expect(route.request().postDataJSON()).toEqual({ result: 'SUCCESS' })
        await new Promise<void>((resolve) => {
          releasePayment = resolve
        })
        if (result === 'NETWORK') return route.abort('failed')
        return route.fulfill({
          json: {
            id: 1,
            orderId: 42,
            method: 'CARD',
            status: result,
            amount: 100000,
            paidAt: null,
            failedReason: null,
            createdAt: '2026-11-09T00:00:00Z',
            orderStatus: result === 'SUCCESS' ? 'PAID' : 'PAYMENT_FAILED',
          },
        })
      })
      async function order() {
        await page
          .getByRole('button', {
            name: locale === 'vi' ? 'Thêm vào giỏ' : 'Add to cart',
            exact: true,
          })
          .click()
        await page
          .getByRole('button', { name: locale === 'vi' ? 'Mở giỏ hàng' : 'Open cart', exact: true })
          .click()
        await page
          .getByRole('button', {
            name: locale === 'vi' ? 'Tiến hành đặt hàng' : 'Proceed to checkout',
          })
          .click()
        for (const [key, value] of Object.entries({
          'full-name': 'Buyer',
          'receiver-name': 'Receiver',
          'receiver-phone': '0900000000',
          city: 'Test City',
          address: '1 Test Street',
        }))
          await page.locator(`#checkout-${key}`).fill(value)
        await page
          .getByRole('button', { name: locale === 'vi' ? 'Đặt hàng' : 'Place order', exact: true })
          .click()
      }
      await page.goto('/customer')
      await order()
      const dialog = page.getByRole('dialog')
      const summary = dialog.locator('dl')
      const pending = locale === 'vi' ? 'Chờ thanh toán' : 'Pending payment'
      await expect(summary).toContainText(pending)
      const pay = dialog.getByRole('button', {
        name: locale === 'vi' ? 'Thanh toán' : 'Pay',
        exact: true,
      })
      await expect(pay).toHaveCount(1)
      await expect(dialog).not.toContainText(/demo|simulat|mô phỏng|PENDING_PAYMENT|backend/i)
      expect(payments).toBe(0)
      await pay.click()
      await expect(pay).toBeDisabled()
      await expect.poll(() => payments).toBe(1)
      await pay.evaluate((button: HTMLButtonElement) => button.click())
      expect(payments).toBe(1)
      releasePayment()
      if (result === 'NETWORK') {
        await expect(dialog.getByRole('alert')).toContainText(
          locale === 'vi' ? 'Chưa thể hoàn tất thanh toán.' : 'The payment could not be completed.',
        )
        await expect(summary).toContainText(pending)
        await expect(pay).toBeEnabled()
      } else {
        const status =
          result === 'SUCCESS'
            ? locale === 'vi'
              ? 'Đã thanh toán'
              : 'Paid'
            : locale === 'vi'
              ? 'Thanh toán lỗi'
              : 'Payment failed'
        await expect(summary).toContainText(status)
        const message =
          result === 'SUCCESS'
            ? locale === 'vi'
              ? 'Thanh toán thành công'
              : 'Payment succeeded'
            : result === 'EXPIRED'
              ? locale === 'vi'
                ? 'Thanh toán đã hết hạn'
                : 'Payment expired'
              : locale === 'vi'
                ? 'Thanh toán thất bại'
                : 'Payment failed'
        await expect(dialog.getByRole('status')).toContainText(message)
        await expect(pay).toHaveCount(0)
      }
      await expect(dialog).not.toContainText(
        /PENDING_PAYMENT|PAYMENT_FAILED|Payment SUCCESS|Order PAID/,
      )
      expect(orders).toBe(1)
      expect(payments).toBe(1)
      await page.screenshot({ path: testInfo.outputPath('payment-result.png') })
      if (result === 'SUCCESS') {
        await dialog
          .getByRole('button', { name: locale === 'vi' ? 'Tiếp tục mua sắm' : 'Continue shopping' })
          .click()
        await order()
        await expect(summary).toContainText(pending)
        expect(orders).toBe(2)
        expect(payments).toBe(1)
      }
    })
  }
}
