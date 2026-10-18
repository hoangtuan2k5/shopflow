import { expect, test, type Page } from '@playwright/test'
import { mockSession } from './session'

const product = {
  id: 110,
  name: 'Winix Filter H for 5500-2',
  price: 900000,
  stockStatus: 'IN_STOCK',
  imageUrl: null,
}
async function setup(page: Page, language = 'en') {
  await mockSession(page, null)
  await page.addInitScript((locale) => localStorage.setItem('shopflow.locale', locale), language)
  await page.route(/\/api\/products(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { items: [product], nextCursor: null } }),
  )
  await page.route('**/api/products/110', (route) =>
    route.fulfill({ json: { ...product, description: 'Replacement for Winix 5500-2 and AM80.' } }),
  )
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  return errors
}
async function open(page: Page) {
  await page.goto('/customer')
  await page.getByTestId('chat-open').click()
  await expect(page.locator('#chat-question')).toBeFocused()
}
async function ask(page: Page, question: string) {
  await page.locator('#chat-question').fill(question)
  await page.locator('#chat-question').press('Enter')
}

test('Guest question returns real product cards and opens the existing detail dialog', async ({
  page,
}) => {
  const errors = await setup(page)
  await page.route('**/api/chat/messages', async (route) => {
    expect(route.request().postDataJSON()).toEqual({ question: 'A filter for 5500-2', history: [] })
    await route.fulfill({
      json: { reply: 'Try this compatible filter.\n— Claude Opus 5.5', products: [product] },
    })
  })
  await open(page)
  await ask(page, 'A filter for 5500-2')
  await expect(page.getByTestId('chat-assistant')).toContainText('900.000')
  await expect(page.getByTestId('chat-assistant')).toContainText('In stock')
  await page.getByRole('button', { name: `View product: ${product.name}` }).click()
  await expect(page.getByRole('dialog')).toContainText('Replacement for Winix 5500-2')
  await expect(page.locator('#chat-question')).toHaveCount(0)
  expect(errors).toEqual([])
})

test('Chat product thumbnails load eagerly while catalog thumbnails keep lazy loading', async ({
  page,
}) => {
  await setup(page)
  const image =
    'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII='
  await page.route('**/api/chat/messages', (route) =>
    route.fulfill({
      json: { reply: 'See this filter.', products: [{ ...product, imageUrl: image }] },
    }),
  )
  await open(page)
  await ask(page, 'Filter')
  const img = page.getByTestId('chat-assistant').getByRole('img', { name: product.name })
  await expect(img).toHaveAttribute('loading', 'eager')
  await expect
    .poll(() => img.evaluate((element) => (element as HTMLImageElement).naturalWidth))
    .toBeGreaterThan(0)
})

test('Only the last six successful messages enter history; reload clears conversation', async ({
  page,
}) => {
  await setup(page)
  const requests: { question: string; history: unknown[] }[] = []
  await page.route('**/api/chat/messages', async (route) => {
    requests.push(route.request().postDataJSON())
    await route.fulfill({ json: { reply: `Reply ${requests.length}`, products: [] } })
  })
  await open(page)
  for (let i = 0; i < 5; i++) {
    await ask(page, `Question ${i}`)
    await expect(page.getByTestId('chat-assistant')).toHaveCount(i + 1)
  }
  expect(requests[4]!.history).toHaveLength(6)
  expect(requests[4]!.history[0]).toEqual({ role: 'user', content: 'Question 1' })
  await expect(page.getByText('No product suggestions for this reply.')).toHaveCount(5)
  await page.reload()
  await page.getByTestId('chat-open').click()
  await expect(page.getByTestId('chat-assistant')).toHaveCount(0)
})

test('Long assistant replies stay visible but history content is bounded', async ({ page }) => {
  await setup(page)
  const requests: { history: { content: string }[] }[] = []
  await page.route('**/api/chat/messages', async (route) => {
    requests.push(route.request().postDataJSON())
    await route.fulfill({ json: { reply: 'A'.repeat(3000), products: [] } })
  })
  await open(page)
  await ask(page, 'First question')
  await expect(page.getByTestId('chat-assistant')).toHaveCount(1)
  await ask(page, 'Follow up')
  await expect(page.getByTestId('chat-assistant')).toHaveCount(2)
  expect(requests[1]!.history[1]!.content).toHaveLength(2000)
})

test('503 retry sends identical input once and does not duplicate user messages', async ({
  page,
}) => {
  const errors = await setup(page)
  const requests: unknown[] = []
  await page.route('**/api/chat/messages', async (route) => {
    requests.push(route.request().postDataJSON())
    if (requests.length === 1)
      await route.fulfill({ status: 503, json: { message: 'Unavailable', status: 503 } })
    else await route.fulfill({ json: { reply: 'Please provide the exact model.', products: [] } })
  })
  await open(page)
  await ask(page, 'Which filter?')
  await expect(page.getByRole('alert')).toContainText('could not answer')
  await page.getByRole('button', { name: 'Try the same question again' }).click()
  await expect(page.getByTestId('chat-assistant')).toHaveCount(1)
  expect(requests[0]).toEqual(requests[1])
  await expect(page.getByTestId('chat-user')).toHaveCount(1)
  await page.getByRole('button', { name: 'Close product assistant' }).click()
  await expect(page.getByRole('button', { name: product.name, exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Add to cart' }).first().click()
  expect(errors).toEqual([])
})

test('Quota Retry-After blocks repeat requests while storefront stays usable', async ({ page }) => {
  await setup(page)
  let calls = 0
  await page.route('**/api/chat/messages', async (route) => {
    calls++
    await route.fulfill({
      status: 429,
      headers: { 'Retry-After': '60' },
      json: { status: 429, message: 'Limit' },
    })
  })
  await open(page)
  await ask(page, 'Filter')
  await expect(page.getByRole('alert')).toContainText('daily limit')
  await expect(page.getByRole('button', { name: 'Try the same question again' })).toBeDisabled()
  await expect(page.locator('#chat-question')).toBeDisabled()
  expect(calls).toBe(1)
  await page.getByRole('button', { name: 'Close product assistant' }).click()
  await expect(page.getByRole('button', { name: product.name, exact: true })).toBeVisible()
})

test('403 explains token refresh, invalid payload is rejected and text is never rendered as HTML', async ({
  page,
}) => {
  await setup(page)
  let calls = 0
  await page.route('**/api/chat/messages', async (route) => {
    calls++
    if (calls === 1) await route.fulfill({ status: 403, json: {} })
    else if (calls === 2)
      await route.fulfill({ json: { reply: 'bad', products: [{ ...product, price: 1.5 }] } })
    else await route.fulfill({ json: { reply: '<img src=x onerror=alert(1)>', products: [] } })
  })
  await open(page)
  await ask(page, 'First')
  await expect(page.getByRole('alert')).toContainText('session token')
  await ask(page, 'Second')
  await expect(page.getByRole('alert')).toContainText('could not answer')
  await page.getByRole('button', { name: 'Try the same question again' }).click()
  await expect(page.getByTestId('chat-assistant')).toContainText('<img src=x')
  await expect(page.getByTestId('chat-assistant').locator('img')).toHaveCount(0)
})

test('Loading prevents duplicate sends, blank input is disabled, Escape restores focus', async ({
  page,
}) => {
  await setup(page)
  let resolve: (() => void) | undefined
  const gate = new Promise<void>((r) => {
    resolve = r
  })
  let calls = 0
  await page.route('**/api/chat/messages', async (route) => {
    calls++
    await gate
    await route.fulfill({ json: { reply: 'Hello', products: [] } })
  })
  await open(page)
  await expect(page.getByRole('button', { name: 'Send question' })).toBeDisabled()
  await expect(page.locator('#chat-question')).toHaveAttribute('maxlength', '500')
  await ask(page, 'Question')
  await expect(page.getByText('Looking through the catalog…')).toBeVisible()
  await expect(page.locator('#chat-question')).toBeDisabled()
  expect(calls).toBe(1)
  resolve!()
  await expect(page.getByTestId('chat-assistant')).toHaveCount(1)
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('chat-open')).toBeFocused()
})

test('Mobile assistant trigger leaves the cart bar and checkout accessible', async ({ page }) => {
  await setup(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/customer')
  await page.getByRole('button', { name: 'Add to cart' }).first().click()
  const cart = page.getByRole('button', { name: /View cart ·/ })
  await expect(cart).toBeVisible()
  const chatBox = (await page.getByTestId('chat-open').boundingBox())!
  const cartBox = (await cart.boundingBox())!
  expect(chatBox.y + chatBox.height).toBeLessThanOrEqual(cartBox.y)
  await cart.click()
  await page.getByRole('button', { name: 'Proceed to checkout' }).click()
  await expect(page.getByRole('dialog')).toContainText('Checkout')
})

for (const width of [360, 390, 640, 1440]) {
  test(`Vietnamese chat geometry at ${width}px`, async ({ page }) => {
    const errors = await setup(page, 'vi')
    const warnings: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'warning') warnings.push(message.text())
    })
    await page.setViewportSize({ width, height: 844 })
    await page.route('**/api/chat/messages', (route) =>
      route.fulfill({
        json: { reply: 'Bạn cho biết mã máy nhé.\n— Claude Opus 5.5', products: [product] },
      }),
    )
    await open(page)
    await ask(page, 'Tìm màng lọc')
    await expect(page.getByTestId('chat-assistant')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Gửi câu hỏi' })).toBeVisible()
    const box = await page.locator('#chat-question').boundingBox()
    expect(box!.y).toBeGreaterThanOrEqual(0)
    expect(box!.y + box!.height).toBeLessThanOrEqual(844)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    const dialog = page.getByRole('dialog')
    const panel = (await dialog.boundingBox())!
    expect(panel.width).toBeCloseTo(width >= 640 ? 520 : width, 0)
    await expect(dialog).toHaveAccessibleName('SHOPFLOW / TƯ VẤN SẢN PHẨM')
    await expect(dialog).not.toHaveAttribute('aria-describedby')
    await expect(dialog).not.toContainText('Cùng bạn chọn món phù hợp')
    await expect(dialog).not.toContainText(
      'Cho biết nhu cầu, để trợ lý tìm trong cửa hàng giúp bạn.',
    )
    await expect(dialog).not.toContainText('Hội thoại tạm thời, tải lại trang sẽ xóa nội dung.')
    const header = (await dialog.locator('header').boundingBox())!
    expect(header.height).toBeLessThan(72)
    expect(errors).toEqual([])
    expect(warnings).toEqual([])
  })
}
