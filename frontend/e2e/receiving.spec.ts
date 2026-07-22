import { expect, test, type Page, type Route } from '@playwright/test'

type InventoryItem = {
  productId: number
  productName: string
  onHandStock: number
  reservedStock: number
  availableStock: number
}

function inventoryItem(overrides: Partial<InventoryItem> = {}): InventoryItem {
  return {
    productId: 7,
    productName: 'Mechanical Keyboard',
    onHandStock: 10,
    reservedStock: 2,
    availableStock: 8,
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

test('Warehouse receives stock with only the contract fields and sees refreshed inventory', async ({
  page,
}) => {
  const browserErrors = collectBrowserErrors(page)
  let items = [inventoryItem()]
  let inventoryRequests = 0
  let releaseReceipt!: () => void
  const receiptHeld = new Promise<void>((resolve) => {
    releaseReceipt = resolve
  })

  await page.route('**/api/inventory', async (route) => {
    inventoryRequests += 1
    await fulfillJson(route, items)
  })
  await page.route('**/api/receivings', async (route) => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().postDataJSON()).toEqual({
      productId: 7,
      quantity: 5,
      supplierName: 'Acme Distribution',
      note: 'Invoice INV-1',
    })
    await receiptHeld
    items = [inventoryItem({ onHandStock: 15, availableStock: 13 })]
    await fulfillJson(route, {
      id: 42,
      productId: 7,
      productName: 'Mechanical Keyboard',
      quantity: 5,
      supplierName: 'Acme Distribution',
      note: 'Invoice INV-1',
      createdAt: '2026-07-22T12:00:00Z',
      createdBy: null,
      onHandStock: 15,
      reservedStock: 2,
      availableStock: 13,
    })
  })

  await page.goto('/warehouse')
  await expect(page.getByRole('heading', { name: 'Inventory management' })).toBeVisible()
  await page.getByRole('button', { name: 'Receive stock' }).click()

  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Quantity').fill('5')
  await dialog.getByLabel('Supplier name (optional)').fill('Acme Distribution')
  await dialog.getByLabel('Note (optional)').fill('Invoice INV-1')
  const submit = dialog.getByRole('button', { name: 'Receive stock' })
  await submit.click()

  await expect(dialog.getByRole('button', { name: 'Receiving…' })).toBeVisible()
  releaseReceipt()

  await expect(page.getByRole('status')).toHaveText('Received 5 units for Mechanical Keyboard.')
  await expect(page.getByText('15', { exact: true })).toBeVisible()
  await expect.poll(() => inventoryRequests).toBeGreaterThan(1)
  expect(browserErrors).toEqual([])
})

test('Client validation rejects invalid receiving fields without truncating them', async ({ page }) => {
  await page.route('**/api/inventory', (route) => fulfillJson(route, [inventoryItem()]))

  await page.goto('/warehouse')
  await page.getByRole('button', { name: 'Receive stock' }).click()

  const dialog = page.getByRole('dialog')
  const submit = dialog.getByRole('button', { name: 'Receive stock' })
  await dialog.getByLabel('Quantity').fill('0')
  await submit.click()
  await expect(dialog.getByText('Quantity must be greater than zero.')).toBeVisible()

  await dialog.getByLabel('Quantity').fill('1')
  await dialog.getByLabel('Supplier name (optional)').fill('  ')
  await submit.click()
  await expect(dialog.getByText('Supplier name cannot be blank.')).toBeVisible()

  const longNote = 'x'.repeat(501)
  await dialog.getByLabel('Supplier name (optional)').fill('Acme')
  await dialog.getByLabel('Note (optional)').fill(longNote)
  await submit.click()
  await expect(dialog.getByText('Use 500 characters or fewer.')).toBeVisible()
  await expect(dialog.getByLabel('Note (optional)')).toHaveValue(longNote)
})

test('Rejected and network receipts retain entered values until a successful retry', async ({ page }) => {
  let attempts = 0
  await page.route('**/api/inventory', (route) => fulfillJson(route, [inventoryItem()]))
  await page.route('**/api/receivings', async (route) => {
    attempts += 1
    if (attempts === 1) {
      await fulfillJson(route, { message: 'Inventory limit reached', status: 409 }, 409)
      return
    }
    if (attempts === 2) {
      await fulfillJson(route, { message: 'Product no longer exists', status: 404 }, 404)
      return
    }
    if (attempts === 3) {
      await route.abort('failed')
      return
    }
    await fulfillJson(route, {
      id: 43,
      productId: 7,
      productName: 'Mechanical Keyboard',
      quantity: 4,
      supplierName: 'Acme',
      note: null,
      createdAt: '2026-07-22T12:05:00Z',
      createdBy: null,
      onHandStock: 14,
      reservedStock: 2,
      availableStock: 12,
    })
  })

  await page.goto('/warehouse')
  await page.getByRole('button', { name: 'Receive stock' }).click()

  const dialog = page.getByRole('dialog')
  const quantity = dialog.getByLabel('Quantity')
  const supplier = dialog.getByLabel('Supplier name (optional)')
  const submit = dialog.getByRole('button', { name: 'Receive stock' })
  await quantity.fill('4')
  await supplier.fill('Acme')

  await submit.click()
  await expect(dialog.getByRole('alert')).toHaveText('Inventory limit reached')
  await expect(quantity).toHaveValue('4')
  await expect(supplier).toHaveValue('Acme')

  await submit.click()
  await expect(dialog.getByRole('alert')).toHaveText('Product no longer exists')
  await expect(quantity).toHaveValue('4')

  await submit.click()
  await expect(dialog.getByRole('alert')).toBeVisible()
  await expect(quantity).toHaveValue('4')

  await submit.click()
  await expect(page.getByRole('status')).toHaveText('Received 4 units for Mechanical Keyboard.')
  await expect.poll(() => attempts).toBe(4)
})

test('Inventory loading, retry, empty state, and mobile dialog focus remain usable', async ({ page }) => {
  let requests = 0
  let releaseFirst!: () => void
  const firstRequestHeld = new Promise<void>((resolve) => {
    releaseFirst = resolve
  })

  await page.route('**/api/inventory', async (route) => {
    requests += 1
    if (requests === 1) {
      await firstRequestHeld
    }
    if (requests <= 4) {
      await fulfillJson(route, { message: 'Service unavailable' }, 503)
      return
    }
    await fulfillJson(route, [])
  })

  await page.goto('/warehouse')
  await expect(page.locator('.animate-pulse')).toHaveCount(4)
  releaseFirst()
  await expect(page.getByRole('heading', { name: 'Inventory could not be loaded' })).toBeVisible({
    timeout: 15_000,
  })
  await page.getByRole('button', { name: 'Try again' }).click()
  await expect(page.getByRole('heading', { name: 'No products to manage' })).toBeVisible()

  await page.unroute('**/api/inventory')
  await page.route('**/api/inventory', (route) => fulfillJson(route, [inventoryItem()]))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()

  const receiveButton = page.getByRole('button', { name: 'Receive stock' })
  await receiveButton.click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toBeHidden()
  await expect(receiveButton).toBeFocused()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true)
})
