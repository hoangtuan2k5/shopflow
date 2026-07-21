import { expect, test, type Page, type Route } from '@playwright/test'

type DeliveryStatus = 'NONE' | 'PREPARING' | 'SHIPPED' | 'DELIVERED'

function deliveryOrder(status: DeliveryStatus = 'NONE') {
  const stages: DeliveryStatus[] = ['NONE', 'PREPARING', 'SHIPPED', 'DELIVERED']
  const reachedStages = stages.slice(1, stages.indexOf(status) + 1)

  return {
    orderId: 42,
    orderStatus: 'PAID',
    deliveryStatus: status,
    receiverName: 'Nguyen An',
    city: 'Ho Chi Minh City',
    totalAmount: 2_190_000,
    createdAt: '2026-07-21T08:00:00Z',
    items: [{ productId: 7, productName: 'Mechanical Keyboard', quantity: 1 }],
    history: reachedStages.map((toStatus, index) => ({
      fromStatus: stages[index],
      toStatus,
      changedAt: `2026-07-21T${String(9 + index).padStart(2, '0')}:00:00Z`,
      changedBy: null,
    })),
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

test('Warehouse advances an order and sees the recorded history', async ({ page }) => {
  const browserErrors = collectBrowserErrors(page)
  let order = deliveryOrder()

  await page.route('**/api/deliveries', (route) => fulfillJson(route, [order]))
  await page.route('**/api/orders/42/delivery', async (route) => {
    expect(route.request().method()).toBe('PATCH')
    expect(route.request().postDataJSON()).toEqual({ toStatus: 'PREPARING' })
    order = deliveryOrder('PREPARING')
    await fulfillJson(route, order)
  })

  await page.goto('/warehouse/deliveries')
  await expect(page.getByRole('heading', { name: 'Delivery management' })).toBeVisible()
  await expect(page.getByText('Warehouse operations')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Order #42' })).toBeVisible()

  await page.getByRole('button', { name: 'Start preparing' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('button', { name: 'Confirm transition' }).click()

  await expect(page.getByRole('status')).toContainText('Order #42 is now preparing.')
  await expect(page.getByText('Preparing', { exact: true }).first()).toBeVisible()
  await page.getByText('Status history · 1 events').click()
  await expect(page.getByText('21 Jul 2026, 09:00')).toBeVisible()
  expect(browserErrors).toEqual([])
})

test('Rejected transition keeps the current state and can be retried', async ({ page }) => {
  const order = deliveryOrder()
  let attempts = 0

  await page.route('**/api/deliveries', (route) => fulfillJson(route, [order]))
  await page.route('**/api/orders/42/delivery', async (route) => {
    attempts += 1
    await fulfillJson(
      route,
      { message: 'The order was updated by another operator.', status: 409 },
      409,
    )
  })

  await page.goto('/warehouse/deliveries')
  await page.getByRole('button', { name: 'Start preparing' }).click()
  await page.getByRole('button', { name: 'Confirm transition' }).click()

  await expect(page.getByRole('alert')).toHaveText('The order was updated by another operator.')
  await expect(page.getByText('Ready to prepare', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: 'Confirm transition' }).click()
  await expect.poll(() => attempts).toBe(2)
})

test('Loading, API failure, retry, and empty states are recoverable', async ({ page }) => {
  let requestCount = 0
  let releaseFirst!: () => void
  const firstRequestHeld = new Promise<void>((resolve) => {
    releaseFirst = resolve
  })

  await page.route('**/api/deliveries', async (route) => {
    requestCount += 1
    if (requestCount === 1) {
      await firstRequestHeld
    }
    if (requestCount <= 4) {
      await fulfillJson(route, { message: 'Service unavailable' }, 503)
      return
    }
    await fulfillJson(route, [])
  })

  await page.goto('/warehouse/deliveries')
  await expect(page.getByLabel('Loading deliveries')).toBeVisible()
  releaseFirst()

  await expect(page.getByRole('heading', { name: 'Deliveries could not be loaded' })).toBeVisible({
    timeout: 15_000,
  })
  await page.getByRole('button', { name: 'Try again' }).click()
  await expect(page.getByRole('heading', { name: 'No deliveries in this view' })).toBeVisible()
})

test('Shop Owner uses the shared mobile workflow with focus restored after the dialog', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/deliveries', (route) => fulfillJson(route, [deliveryOrder('SHIPPED')]))

  await page.goto('/shop-owner')
  await expect(page.getByText('Shop owner', { exact: true })).toBeVisible()
  await expect(page.getByText('Ready to prepare', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('Preparing', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('Shipped', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('Delivered', { exact: true }).first()).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true)

  const completeButton = page.getByRole('button', { name: 'Complete delivery' })
  await completeButton.click()
  await expect(page.getByText(/reduces on-hand and reserved stock/)).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toBeHidden()
  await expect(completeButton).toBeFocused()
})
