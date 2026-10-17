import { expect, test } from '@playwright/test'
import { mockSession } from './session'

const name = '5 Tấm Màng lọc thô chắn bụi cho máy lọc không khí Sharp FZ-PF51F1 / FZ-PF80K1'
const description =
  'Lớp lọc thô giúp giữ bụi lớn trước khi không khí đi vào bộ lọc của máy. '.repeat(20) +
  'Đối chiếu kích thước trước khi chọn mua.'
const product = { id: 7, name, price: 100000, stockStatus: 'IN_STOCK', imageUrl: null }

for (const viewport of [
  { width: 1533, height: 696 },
  { width: 1366, height: 650 },
  { width: 390, height: 700 },
  { width: 360, height: 640 },
]) {
  test(`Long product details stay inside ${viewport.width}x${viewport.height}`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport)
    await page.addInitScript(() => localStorage.setItem('shopflow.locale', 'en'))
    await mockSession(page, null)
    await page.route('**/api/products', (route) => route.fulfill({ json: [product] }))
    await page.route('**/api/products/7', (route) =>
      route.fulfill({ json: { ...product, description } }),
    )
    await page.goto('/customer')
    const trigger = page.getByRole('button', { name: `View details for ${name}`, exact: true })
    await trigger.click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByText(description, { exact: true })).toBeVisible()
    const gap = viewport.width >= 640 ? 24 : 16
    await expect
      .poll(async () => {
        const box = await dialog.boundingBox()
        return Boolean(box && box.y >= gap - 1 && box.y + box.height <= viewport.height - gap + 1)
      })
      .toBe(true)
    const close = dialog.getByRole('button', { name: 'Close product details' })
    const add = dialog.getByRole('button', { name: 'Add to cart' })
    await expect(close).toBeInViewport({ ratio: 1 })
    await expect(add).toBeInViewport({ ratio: 1 })
    const region = dialog.getByRole('region', { name, exact: true })
    await expect.poll(() => region.evaluate((e) => e.scrollHeight > e.clientHeight)).toBe(true)
    await region.focus()
    await page.keyboard.press('Control+End')
    await expect
      .poll(() => region.evaluate((e) => e.scrollTop + e.clientHeight >= e.scrollHeight - 1))
      .toBe(true)
    await expect(close).toBeInViewport({ ratio: 1 })
    await expect(add).toBeInViewport({ ratio: 1 })
    expect(await dialog.evaluate((e) => e.scrollWidth <= e.clientWidth)).toBe(true)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await close.click()
    await expect(dialog).not.toBeVisible()
    await expect(trigger).toBeFocused()
    await trigger.click()
    await page.keyboard.press('Escape')
    await expect(dialog).not.toBeVisible()
    await trigger.click()
    await dialog.getByRole('button', { name: 'Add to cart' }).click()
    await page.getByRole('button', { name: 'Open cart', exact: true }).click()
    await expect(page.getByRole('dialog').getByText(name, { exact: true })).toBeVisible()
  })
}

test('Loading and failed product details keep their close control inside the viewport', async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 640 })
  await page.addInitScript(() => localStorage.setItem('shopflow.locale', 'en'))
  await mockSession(page, null)
  await page.route('**/api/products', (route) => route.fulfill({ json: [product] }))
  let release!: () => void
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/products/7', async (route) => {
    await pending
    await route.fulfill({ status: 500, json: { message: 'Product unavailable' } })
  })
  await page.goto('/customer')
  const trigger = page.getByRole('button', { name: `View details for ${name}`, exact: true })
  await trigger.click()
  const dialog = page.getByRole('dialog')
  const close = dialog.getByRole('button', { name: 'Close product details' })
  await expect(close).toBeInViewport({ ratio: 1 })
  const loadingBox = await dialog.boundingBox()
  expect(loadingBox!.y).toBeGreaterThanOrEqual(16)
  expect(loadingBox!.y + loadingBox!.height).toBeLessThanOrEqual(624)
  release()
  await expect(dialog.getByRole('alert')).toBeVisible()
  await expect(close).toBeInViewport({ ratio: 1 })
  const errorBox = await dialog.boundingBox()
  expect(errorBox!.y).toBeGreaterThanOrEqual(16)
  expect(errorBox!.y + errorBox!.height).toBeLessThanOrEqual(624)
  await close.click()
  await expect(trigger).toBeFocused()
})
