import { expect, test, type Page } from '@playwright/test'
import type { ProductListItem } from '../src/api/catalog'
import { mockSession, type MockRole } from './session'

const listUrl = /\/api\/products(?:\?.*)?$/
const png = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=',
  'base64',
)

async function catalog(page: Page, role: MockRole | null = null) {
  await mockSession(page, role)
  await page.addInitScript(() => localStorage.setItem('shopflow.locale', 'en'))
  const products: ProductListItem[] = Array.from({ length: 45 }, (_, i) => ({
    id: i + 1,
    name: i === 44 ? 'Only final model' : `Máy lọc Sharp ${i + 1}`,
    price: 100000,
    imageUrl: null,
    stockStatus: 'IN_STOCK',
  }))
  const state = {
    products,
    requests: [] as { after: number; q: string }[],
    failNext: false,
    delayNext: 0,
    delaySearch: '',
  }
  await page.route(listUrl, async (route) => {
    const params = new URL(route.request().url()).searchParams
    const after = Number(params.get('after') ?? 0)
    const q = params.get('q') ?? ''
    state.requests.push({ after, q })
    if (after && state.delayNext)
      await new Promise((resolve) => setTimeout(resolve, state.delayNext))
    if (q && q === state.delaySearch) await new Promise((resolve) => setTimeout(resolve, 1000))
    if (after && state.failNext) {
      await route.fulfill({ status: 503, json: { message: 'Temporary failure' } })
      return
    }
    const matches = products.filter(
      (p) => p.id > after && p.name.toLocaleLowerCase('vi').includes(q),
    )
    const items = matches.slice(0, 20)
    await route.fulfill({
      json: { items, nextCursor: matches.length > 20 ? String(items.at(-1)!.id) : null },
    })
  })
  await page.route(/\/api\/products\/\d+$/, (route) => {
    const id = Number(new URL(route.request().url()).pathname.split('/').at(-1))
    return route.fulfill({
      json: { ...products.find((p) => p.id === id), description: 'Replacement filter' },
    })
  })
  return state
}

test('Scroll loads 20, 40, 45 products without duplicate requests or moving existing cards', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 720 })
  const state = await catalog(page)
  state.delayNext = 500
  await page.goto('/customer')
  const cards = page.locator('article')
  await expect(cards).toHaveCount(20)
  await cards.last().scrollIntoViewIfNeeded()
  await expect.poll(() => state.requests.some((r) => r.after === 20)).toBe(true)
  const before = await cards.nth(19).boundingBox()
  await page.evaluate(() => window.dispatchEvent(new Event('scroll')))
  await expect(cards).toHaveCount(40)
  expect((await cards.nth(19).boundingBox())!.y).toBeCloseTo(before!.y, 0)
  expect(state.requests.filter((r) => r.after === 20)).toHaveLength(1)
  await cards.last().scrollIntoViewIfNeeded()
  await expect(cards).toHaveCount(45)
  await expect(page.getByText('You have reached the end of the products.')).toBeVisible()
  expect(state.requests.map((r) => r.after)).toEqual([0, 20, 40])
  expect(new Set(await cards.locator('button.text-left').allTextContents()).size).toBe(45)
  await expect(page.getByRole('button', { name: 'Load more', exact: true })).toHaveCount(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('A failed next batch preserves cards and retries the same cursor explicitly', async ({
  page,
}) => {
  const state = await catalog(page)
  state.failNext = true
  await page.goto('/customer')
  await expect(page.locator('article')).toHaveCount(20)
  await page.locator('article').last().scrollIntoViewIfNeeded()
  await expect(page.getByRole('alert')).toContainText('Could not load more products')
  await expect(page.locator('article')).toHaveCount(20)
  const count = state.requests.length
  await page.mouse.wheel(0, -500)
  await page.mouse.wheel(0, 500)
  await expect(page.getByRole('button', { name: 'Try again', exact: true })).toBeEnabled()
  expect(state.requests).toHaveLength(count)
  state.failNext = false
  await page.getByRole('button', { name: 'Try again', exact: true }).click()
  await expect(page.locator('article')).toHaveCount(40)
  expect(state.requests.at(-1)!.after).toBe(20)
})

test('Searching the entire catalog preserves cart items and clearing search starts from the first batch', async ({
  page,
}) => {
  const state = await catalog(page)
  await page.goto('/customer')
  await expect(page.locator('article')).toHaveCount(20)
  await page
    .locator('article')
    .first()
    .getByRole('button', { name: 'Add to cart', exact: true })
    .click()
  await page.locator('article').last().scrollIntoViewIfNeeded()
  await expect(page.locator('article')).toHaveCount(40)
  await page.getByRole('searchbox', { name: 'Search products' }).fill('Only final')
  await expect(page.locator('article')).toHaveCount(1)
  await expect(page.locator('article')).toContainText('Only final model')
  await page.locator('article').getByRole('button', { name: 'Add to cart', exact: true }).click()
  await page.getByRole('button', { name: 'Open cart', exact: true }).click()
  const cart = page.getByRole('dialog')
  await expect(cart.locator('article')).toHaveCount(2)
  await expect(cart).toContainText('Máy lọc Sharp 1')
  await expect(cart).toContainText('Only final model')
  await page.keyboard.press('Escape')
  await page.getByRole('searchbox', { name: 'Search products' }).fill('No such item')
  await expect(page.getByText('No products found')).toBeVisible()
  await page.getByRole('button', { name: 'Clear search', exact: true }).first().click()
  await expect(page.locator('article')).toHaveCount(20)
  expect(state.requests.at(-1)).toEqual({ after: 0, q: '' })
  await page.getByRole('button', { name: 'Open cart', exact: true }).click()
  await expect(cart.locator('article')).toHaveCount(2)
})

test('A slow previous search cannot overwrite a newer result', async ({ page }) => {
  const state = await catalog(page)
  state.delaySearch = 'sharp'
  await page.goto('/customer')
  await expect(page.locator('article')).toHaveCount(20)
  const search = page.getByRole('searchbox', { name: 'Search products' })
  await search.fill('sharp')
  await expect.poll(() => state.requests.some((r) => r.q === 'sharp')).toBe(true)
  await search.fill('Only final')
  await expect(page.locator('article')).toHaveCount(1)
  await expect(page.locator('article')).toContainText('Only final model')
  // Wait for the deliberately slow old response, not an arbitrary rendering delay.
  await page.waitForTimeout(1100)
  await expect(page.locator('article')).toHaveCount(1)
  await expect(page.locator('article')).toContainText('Only final model')
})

test('The mobile load-more button works by keyboard without an observer', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 700 })
  await page.addInitScript(() => {
    window.IntersectionObserver = class {
      observe() {}
      disconnect() {}
      unobserve() {}
      takeRecords() {
        return []
      }
      root = null
      rootMargin = ''
      thresholds = []
    }
  })
  await catalog(page)
  await page.goto('/customer')
  await expect(page.locator('article')).toHaveCount(20)
  const load = page.getByRole('button', { name: 'Load more', exact: true })
  await load.focus()
  await page.keyboard.press('Enter')
  await expect(page.locator('article')).toHaveCount(40)
  await expect(load).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.locator('article')).toHaveCount(45)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('Owner can load all batches and replace the image of a product beyond the first batch', async ({
  page,
}) => {
  const state = await catalog(page, 'SHOP_OWNER')
  await page.route('**/test-image.png', (route) =>
    route.fulfill({ contentType: 'image/png', body: png }),
  )
  await page.route('**/api/products/45/image', (route) => {
    state.products[44]!.imageUrl = '/test-image.png'
    return route.fulfill({ json: { imageUrl: '/test-image.png' } })
  })
  await page.goto('/shop-owner/products')
  await expect(page.locator('input[type=file]')).toHaveCount(20)
  await page.getByRole('button', { name: 'Load more', exact: true }).click()
  await expect(page.locator('input[type=file]')).toHaveCount(40)
  await page.getByRole('button', { name: 'Load more', exact: true }).click()
  await expect(page.locator('input[type=file]')).toHaveCount(45)
  const row = page.locator('article').filter({ hasText: 'Only final model' })
  await row
    .getByLabel('Choose an image for Only final model')
    .setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: png })
  await row.getByRole('button', { name: 'Upload image' }).click()
  await expect(row.getByRole('img', { name: 'Only final model', exact: true })).toBeVisible()
  await expect(page.locator('input[type=file]')).toHaveCount(45)
})
