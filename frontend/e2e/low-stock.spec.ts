import { expect, test, type Page, type Route } from '@playwright/test'

type InventoryItem = {
  productId: number
  productName: string
  onHandStock: number
  reservedStock: number
  availableStock: number
  lowStockThreshold: number | null
  lowStock: boolean
}

function inventoryItem(overrides: Partial<InventoryItem> = {}): InventoryItem {
  return {
    productId: 7,
    productName: 'Mechanical Keyboard',
    onHandStock: 10,
    reservedStock: 2,
    availableStock: 8,
    lowStockThreshold: null,
    lowStock: false,
    ...overrides,
  }
}

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

test('Warehouse sees low stock alerts and configures a threshold', async ({ page }) => {
  const browserErrors = collectBrowserErrors(page)
  const lowItem = inventoryItem({
    productId: 3,
    productName: 'USB Hub',
    onHandStock: 3,
    reservedStock: 0,
    availableStock: 3,
    lowStockThreshold: 5,
    lowStock: true,
  })

  await page.route('**/api/inventory', (route) => fulfillJson(route, [lowItem, inventoryItem()]))
  await page.route('**/api/inventory/7/threshold', async (route) => {
    expect(route.request().method()).toBe('PUT')
    expect(route.request().postDataJSON()).toEqual({ lowStockThreshold: 10 })
    await fulfillJson(route, inventoryItem({ lowStockThreshold: 10, lowStock: true }))
  })

  await page.goto('/warehouse')
  await expect(page.getByRole('alert')).toHaveText(/1 product is low on stock/)
  await expect(page.getByText('Low stock', { exact: true })).toBeVisible()
  await expect(page.getByText('Alerts at ≤ 5')).toBeVisible()

  await page
    .getByRole('listitem')
    .filter({ hasText: 'Mechanical Keyboard' })
    .getByRole('button', { name: 'Set alert' })
    .click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Alert threshold').fill('10')
  await dialog.getByRole('button', { name: 'Save alert' }).click()

  await expect(page.getByRole('status')).toHaveText(
    'Mechanical Keyboard alerts when available stock is 10 or less.',
  )
  await expect(page.getByText('Alerts at ≤ 10')).toBeVisible()
  await expect(page.getByRole('alert')).toHaveText(/2 products are low on stock/)
  expect(browserErrors).toEqual([])
})

test('Clearing the threshold removes the alert and rejects invalid values', async ({ page }) => {
  const trackedItem = inventoryItem({
    lowStockThreshold: 5,
    onHandStock: 3,
    reservedStock: 0,
    availableStock: 3,
    lowStock: true,
  })

  await page.route('**/api/inventory', (route) => fulfillJson(route, [trackedItem]))
  await page.route('**/api/inventory/7/threshold', async (route) => {
    expect(route.request().postDataJSON()).toEqual({ lowStockThreshold: null })
    await fulfillJson(
      route,
      inventoryItem({ onHandStock: 3, reservedStock: 0, availableStock: 3 }),
    )
  })

  await page.goto('/warehouse')
  await expect(page.getByRole('alert')).toHaveText(/1 product is low on stock/)

  await page.getByRole('button', { name: 'Set alert' }).click()
  const dialog = page.getByRole('dialog')

  await dialog.getByLabel('Alert threshold').fill('-1')
  await dialog.getByRole('button', { name: 'Save alert' }).click()
  await expect(dialog.getByText('Threshold cannot be negative.')).toBeVisible()

  await dialog.getByLabel('Alert threshold').fill('')
  await dialog.getByRole('button', { name: 'Save alert' }).click()

  await expect(page.getByRole('status')).toHaveText('Mechanical Keyboard low stock alert removed.')
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.getByText('Low stock', { exact: true })).toHaveCount(0)
})
