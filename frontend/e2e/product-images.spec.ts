import { expect, test, type Page } from '@playwright/test'
import { mockSession } from './session'

const name = 'Bộ lọc không khí gia đình có màng lọc bụi mịn thay thế'
const png = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=',
  'base64',
)
const product = {
  id: 7,
  name,
  price: 100000,
  stockStatus: 'IN_STOCK',
  imageUrl: '/test-image.png' as string | null,
}

async function catalog(page: Page, imageUrl: string | null = '/test-image.png') {
  const state = { ...product, imageUrl }
  await page.route('**/test-image.png', (route) =>
    route.fulfill({ contentType: 'image/png', body: png }),
  )
  await page.route('**/api/products', (route) => route.fulfill({ json: [state] }))
  await page.route('**/api/products/7', (route) =>
    route.fulfill({ json: { ...state, description: 'Màng lọc thay thế' } }),
  )
  return state
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('shopflow.locale', 'en'))
})

test('Owner uploads multipart with CSRF and refreshed images reach the catalog, detail and cart', async ({
  page,
  context,
}) => {
  await mockSession(page, 'SHOP_OWNER')
  const state = await catalog(page, null)
  await context.addCookies([
    { name: 'XSRF-TOKEN', value: 'image-test-csrf', url: 'http://127.0.0.1:4173' },
  ])
  await page.route('**/api/products/7/image', async (route) => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().headers()['x-xsrf-token']).toBe('image-test-csrf')
    expect(route.request().headers()['content-type']).toContain('multipart/form-data; boundary=')
    expect(route.request().postDataBuffer()?.toString()).toContain(
      'name="file"; filename="photo.png"',
    )
    state.imageUrl = '/test-image.png'
    await route.fulfill({ json: { imageUrl: state.imageUrl } })
  })
  await page.goto('/shop-owner/products')
  await page
    .getByLabel(`Choose an image for ${name}`)
    .setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: png })
  await page.getByRole('button', { name: 'Upload image' }).click()
  await expect(page.getByRole('status')).toContainText('Image updated')
  await expect(page.getByRole('img', { name, exact: true })).toBeVisible()
  await page.goto('/customer')
  const card = page.locator('article').first()
  await expect(card.getByRole('img', { name, exact: true })).toBeVisible()
  await page.getByRole('button', { name: `View details for ${name}`, exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('img', { name, exact: true })).toBeVisible()
  await page.getByRole('dialog').getByRole('button', { name: 'Add to cart' }).click()
  await page.getByRole('button', { name: 'Open cart', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('img', { name, exact: true })).toBeVisible()
})

test('Backend rejection preserves the previous image and shows its reason', async ({ page }) => {
  await mockSession(page, 'SHOP_OWNER')
  await catalog(page)
  await page.route('**/api/products/7/image', (route) =>
    route.fulfill({
      status: 415,
      json: {
        message: 'Only valid JPEG or PNG images up to 16 megapixels are supported',
        status: 415,
      },
    }),
  )
  await page.goto('/shop-owner/products')
  await page
    .getByLabel(`Choose an image for ${name}`)
    .setInputFiles({ name: 'fake.png', mimeType: 'image/png', buffer: Buffer.from('fake') })
  await page.getByRole('button', { name: 'Upload image' }).click()
  await expect(page.getByRole('alert')).toHaveText(
    'Only valid JPEG or PNG images up to 16 megapixels are supported',
  )
  await expect(page.getByRole('img', { name, exact: true })).toHaveAttribute(
    'src',
    '/test-image.png',
  )
})

test('Null and failed images retain the existing placeholder', async ({ page }) => {
  await mockSession(page, null)
  await catalog(page, null)
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/customer')
  const card = page.locator('article').first()
  await expect(card.getByRole('img')).toHaveCount(0)
  await expect(card.locator('.tabler-icon-package')).toBeVisible()
  await catalog(page, '/missing-image.png')
  await page.route('**/missing-image.png', (route) => route.fulfill({ status: 404, body: '' }))
  await page.reload()
  await expect(card.locator('.tabler-icon-package')).toBeVisible()
  await expect(card.getByRole('img')).toHaveCount(0)
  expect(errors).toEqual([])
})

test('Owner has an entry link while warehouse and guests cannot reach image management', async ({
  page,
}) => {
  await mockSession(page, 'SHOP_OWNER')
  await catalog(page)
  await page.route('**/api/deliveries', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/inventory', (route) => route.fulfill({ json: [] }))
  await page.goto('/shop-owner')
  await page.getByRole('link', { name: 'Product images', exact: true }).click()
  await expect(page).toHaveURL(/\/shop-owner\/products$/)
  await mockSession(page, 'WAREHOUSE')
  await page.goto('/warehouse/deliveries')
  await expect(page.getByRole('link', { name: 'Product images', exact: true })).toHaveCount(0)
  await page.goto('/shop-owner/products')
  await expect(page).toHaveURL(/\/warehouse$/)
  await mockSession(page, null)
  await page.goto('/shop-owner/products')
  await expect(page).toHaveURL(/\/login\?redirect=/)
})
