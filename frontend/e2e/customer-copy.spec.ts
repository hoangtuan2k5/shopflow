import { expect, test } from '@playwright/test'
import { mockSession } from './session'

for (const locale of ['en', 'vi'] as const) {
  test(`Customer checkout and delivery use concise ${locale} copy`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.addInitScript((value) => localStorage.setItem('shopflow.locale', value), locale)
    await mockSession(page, 'CUSTOMER')
    await page.route('**/api/auth/session', (route) =>
      route.fulfill({
        json: {
          authenticated: true,
          user: { userId: 1, username: 'buyer', displayName: 'Buyer', role: 'CUSTOMER' },
        },
      }),
    )
    await page.route('**/api/auth/account', (route) =>
      route.fulfill({
        json: {
          displayName: 'Buyer',
          email: 'buyer@example.com',
          capabilities: { manageDelivery: true, changePassword: true, setPassword: false },
        },
      }),
    )
    await page.route('**/api/auth/account/delivery', (route) =>
      route.fulfill({ json: { delivery: null } }),
    )
    const product = {
      id: 1,
      name: 'Air filter',
      price: 100000,
      stockStatus: 'IN_STOCK',
      imageUrl: null,
    }
    await page.route(/\/api\/products(?:\?.*)?$/, (route) =>
      route.fulfill({ json: { items: [product], nextCursor: null } }),
    )
    await page.route('**/api/products/1', (route) => route.fulfill({ json: product }))
    await page.goto('/customer')
    await expect(
      page.getByRole('heading', {
        name: locale === 'vi' ? 'Sản phẩm dành cho bạn' : 'Products for you',
        level: 1,
      }),
    ).toBeVisible()
    await expect(page.locator('main > section').first()).toHaveAttribute('id', 'products')
    await expect(
      page.getByRole('button', {
        name: locale === 'vi' ? 'Khám phá sản phẩm' : 'Browse products',
        exact: true,
      }),
    ).toHaveCount(0)
    await page.screenshot({ path: testInfo.outputPath('storefront.png'), fullPage: true })
    await page
      .getByRole('button', { name: locale === 'vi' ? 'Thêm vào giỏ' : 'Add to cart', exact: true })
      .click()
    await page
      .getByRole('button', { name: locale === 'vi' ? 'Mở giỏ hàng' : 'Open cart', exact: true })
      .click()
    await page
      .getByRole('button', { name: locale === 'vi' ? 'Tiến hành đặt hàng' : 'Proceed to checkout' })
      .click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog).not.toContainText(
      /demo|backend|simulat|mô phỏng|dữ liệu quyết định|apply only to this order/i,
    )
    await page.screenshot({ path: testInfo.outputPath('checkout.png') })
    await page.keyboard.press('Escape')
    await page.goto('/account/delivery')
    await expect(page.locator('#delivery-receiverName')).toBeVisible()
    await expect(page.locator('body')).not.toContainText(
      /demo|backend|orders already placed|không ảnh hưởng đơn đã đặt/i,
    )
    await page.screenshot({ path: testInfo.outputPath('delivery.png'), fullPage: true })
  })
}
