import { expect, test, type Page, type Route } from '@playwright/test'

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

function collectBrowserErrors(page: Page) {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  return errors
}

test.describe('Vietnamese browser locale', () => {
  test.use({ locale: 'vi-VN' })

  test('Interface defaults to Vietnamese without a stored choice', async ({ page }) => {
    const browserErrors = collectBrowserErrors(page)
    await page.route('**/api/inventory', (route) => fulfillJson(route, []))

    await page.goto('/warehouse')

    await expect(page.locator('html')).toHaveAttribute('lang', 'vi')
    await expect(page.getByRole('heading', { name: 'Quản lý tồn kho' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Chưa có sản phẩm để quản lý' })).toBeVisible()
    await expect(page.getByRole('combobox', { name: 'Ngôn ngữ' })).toHaveValue('vi')

    expect(browserErrors).toEqual([])
  })
})

test('Interface defaults to English for non-Vietnamese browser locales', async ({ page }) => {
  const browserErrors = collectBrowserErrors(page)
  await page.route('**/api/products', (route) => fulfillJson(route, []))

  await page.goto('/customer')

  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  await expect(page.getByRole('heading', { name: 'Products for you' })).toBeVisible()
  await expect(page.getByText('No products yet')).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Language' })).toHaveValue('en')

  expect(browserErrors).toEqual([])
})

test('Language selector switches instantly and the choice survives a reload', async ({ page }) => {
  const browserErrors = collectBrowserErrors(page)
  await page.route('**/api/inventory', (route) => fulfillJson(route, []))

  await page.goto('/warehouse')
  await expect(page.getByRole('heading', { name: 'Inventory management' })).toBeVisible()

  await page.getByRole('combobox', { name: 'Language' }).selectOption('vi')

  await expect(page.getByRole('heading', { name: 'Quản lý tồn kho' })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi')

  await page.reload()

  await expect(page.getByRole('heading', { name: 'Quản lý tồn kho' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Ngôn ngữ' })).toHaveValue('vi')

  expect(browserErrors).toEqual([])
})
