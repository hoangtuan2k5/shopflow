import { expect, test } from '@playwright/test'
import { mockSession } from './session'

const product = {
  id: 1,
  name: '20 Bộ Tem song ngữ hướng dẫn tiếng việt NỒI CƠM ĐIỆN nhật bãi Tiger, Toshiba, Panasonic, Sanyo, Mitsubishi....',
  price: 14250,
  stockStatus: 'IN_STOCK',
  imageUrl: null,
}

for (const locale of ['en', 'vi'] as const) {
  for (const [width, height] of [
    [320, 568],
    [390, 844],
    [768, 1024],
    [1280, 720],
    [1440, 900],
  ] as const) {
    test(`Checkout contains long product names and reachable controls: ${locale} ${width}`, async ({
      page,
    }, testInfo) => {
      await page.setViewportSize({ width, height })
      await page.addInitScript((value) => localStorage.setItem('shopflow.locale', value), locale)
      await mockSession(page, 'CUSTOMER')
      await page.route(/\/api\/products(?:\?.*)?$/, (route) =>
        route.fulfill({ json: { items: [product], nextCursor: null } }),
      )
      await page.route('**/api/products/1', (route) => route.fulfill({ json: product }))
      let orders = 0
      await page.route('**/api/orders', (route) => {
        orders += 1
        return route.fulfill({
          status: 400,
          json: { message: 'Layout fixture rejection', fieldErrors: {} },
        })
      })
      await page.goto('/customer')
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
      const dialog = page.getByRole('dialog')
      const form = page.locator('#checkout form')
      await expect(form).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('checkout-top.png') })
      // Clipped descendants can overflow even when the page/dialog scrollWidth is correct.
      const overflow = await form.evaluate((el) => {
        const outer = el.getBoundingClientRect()
        return [...el.querySelectorAll('article, input, button, form > div')].flatMap((node) => {
          const box = node.getBoundingClientRect()
          return box.left < outer.left - 1 || box.right > outer.right + 1 ? [node.tagName] : []
        })
      })
      expect(overflow).toEqual([])
      const card = await form.locator('article').boundingBox()
      const review = await form.locator(':scope > div').first().boundingBox()
      expect(card!.width).toBeLessThanOrEqual(review!.width + 1)
      const close = dialog.getByRole('button', {
        name: locale === 'vi' ? 'Đóng đặt hàng' : 'Close checkout',
        exact: true,
      })
      const closeBox = await close.boundingBox()
      const summaryBox = await page.locator('#checkout > header > div').last().boundingBox()
      expect(
        closeBox!.x >= summaryBox!.x + summaryBox!.width ||
          closeBox!.y + closeBox!.height <= summaryBox!.y,
      ).toBeTruthy()
      for (const [id, value] of Object.entries({
        'full-name': 'Layout Buyer',
        'receiver-name': 'Layout Receiver',
        'receiver-phone': '0900000000',
        city: 'Test City',
        address: '1 Test Street',
      }))
        await page.locator(`#checkout-${id}`).fill(value)
      const submit = form.getByRole('button', {
        name: locale === 'vi' ? 'Đặt hàng' : 'Place order',
        exact: true,
      })
      await submit.scrollIntoViewIfNeeded()
      await expect(submit).toBeInViewport({ ratio: 1 })
      await page.screenshot({ path: testInfo.outputPath('checkout-bottom.png') })
      await submit.click()
      await expect(page.getByText('Layout fixture rejection', { exact: true })).toBeVisible()
      expect(orders).toBe(1)
      await page.keyboard.press('Escape')
      await expect(dialog).not.toBeVisible()
    })
  }
}
