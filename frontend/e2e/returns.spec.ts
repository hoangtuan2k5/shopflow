import { expect, test, type Page, type Route } from '@playwright/test'

type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'RESTOCKED' | 'REJECTED'

type ReturnItem = {
  orderItemId: number
  productId: number
  productName: string
  quantity: number
}

type ReturnRequest = {
  id: number
  orderId: number
  status: ReturnStatus
  reason: string | null
  restockable: boolean
  createdAt: string
  updatedAt: string
  items: ReturnItem[]
}

type ReturnableOrder = {
  orderId: number
  receiverName: string
  city: string
  totalAmount: number
  createdAt: string
  items: Array<{
    orderItemId: number
    productId: number
    productName: string
    quantity: number
    returnedQuantity: number
    returnableQuantity: number
  }>
}

function returnRequest(overrides: Partial<ReturnRequest> = {}): ReturnRequest {
  return {
    id: 12,
    orderId: 42,
    status: 'REQUESTED',
    reason: 'Damaged box',
    restockable: false,
    createdAt: '2026-07-25T09:00:00Z',
    updatedAt: '2026-07-25T09:00:00Z',
    items: [{ orderItemId: 7, productId: 3, productName: 'Mechanical Keyboard', quantity: 2 }],
    ...overrides,
  }
}

function returnableOrder(overrides: Partial<ReturnableOrder> = {}): ReturnableOrder {
  return {
    orderId: 42,
    receiverName: 'Nguyen Van A',
    city: 'Hanoi',
    totalAmount: 4000000,
    createdAt: '2026-07-20T08:00:00Z',
    items: [
      {
        orderItemId: 7,
        productId: 3,
        productName: 'Mechanical Keyboard',
        quantity: 3,
        returnedQuantity: 0,
        returnableQuantity: 3,
      },
    ],
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

test('Warehouse registers, approves and restocks a return for a delivered order', async ({
  page,
}) => {
  const browserErrors = collectBrowserErrors(page)
  let returns: ReturnRequest[] = []
  let patches = 0

  await page.route('**/api/returns', async (route) => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toEqual({
        orderId: 42,
        reason: 'Damaged box',
        items: [{ orderItemId: 7, quantity: 2 }],
      })
      returns = [returnRequest()]
      await fulfillJson(route, returnRequest(), 201)
      return
    }
    await fulfillJson(route, returns)
  })
  await page.route('**/api/returns/orders', (route) => fulfillJson(route, [returnableOrder()]))
  await page.route('**/api/returns/12', async (route) => {
    expect(route.request().method()).toBe('PATCH')
    patches += 1
    if (patches === 1) {
      expect(route.request().postDataJSON()).toEqual({ toStatus: 'APPROVED', restockable: true })
      returns = [returnRequest({ status: 'APPROVED', restockable: true })]
      await fulfillJson(route, returnRequest({ status: 'APPROVED', restockable: true }))
      return
    }
    expect(route.request().postDataJSON()).toEqual({ toStatus: 'RESTOCKED' })
    returns = [returnRequest({ status: 'RESTOCKED', restockable: true })]
    await fulfillJson(route, returnRequest({ status: 'RESTOCKED', restockable: true }))
  })

  await page.goto('/warehouse/returns')
  await expect(page.getByRole('heading', { name: 'Return management' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'No return requests' })).toBeVisible()

  await page.getByRole('button', { name: 'New return' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Delivered order').selectOption('42')
  await dialog.getByLabel('Quantity for Mechanical Keyboard').fill('2')
  await dialog.getByLabel('Reason (optional)').fill('Damaged box')
  await dialog.getByRole('button', { name: 'Request return' }).click()

  await expect(page.getByRole('status')).toHaveText('Return #12 requested for order #42.')
  await expect(page.getByText('Return #12 · Order #42')).toBeVisible()
  await expect(page.getByText('Requested', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Review request' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Approve' }).click()
  await expect(page.getByRole('status')).toHaveText('Return #12 is now approved.')
  await expect(page.getByText('Approved', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Restock items' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Confirm restock' }).click()
  await expect(page.getByRole('status')).toHaveText('Return #12 is now restocked.')
  await expect(page.getByText('Restocked', { exact: true })).toBeVisible()
  expect(patches).toBe(2)
  expect(browserErrors).toEqual([])
})

test('Shop owner rejects a request and cannot restock approved returns', async ({ page }) => {
  let returns = [
    returnRequest(),
    returnRequest({ id: 13, status: 'APPROVED', restockable: true }),
  ]

  await page.route('**/api/returns', (route) => fulfillJson(route, returns))
  await page.route('**/api/returns/orders', (route) => fulfillJson(route, []))
  await page.route('**/api/returns/12', async (route) => {
    expect(route.request().method()).toBe('PATCH')
    expect(route.request().postDataJSON()).toEqual({ toStatus: 'REJECTED' })
    returns = [returnRequest({ status: 'REJECTED' }), returns[1]!]
    await fulfillJson(route, returnRequest({ status: 'REJECTED' }))
  })

  await page.goto('/shop-owner/returns')
  await expect(page.getByText('Shop owner', { exact: true })).toBeVisible()
  await expect(page.getByText('Waiting for warehouse restock.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Restock items' })).toHaveCount(0)

  await page.getByRole('button', { name: 'Review request' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Reject' }).click()
  await expect(page.getByRole('status')).toHaveText('Return #12 is now rejected.')
  await expect(page.getByText('Rejected', { exact: true })).toBeVisible()
})

test('Validation and API conflicts keep the return form recoverable', async ({ page }) => {
  let attempts = 0

  await page.route('**/api/returns', async (route) => {
    if (route.request().method() === 'POST') {
      attempts += 1
      if (attempts === 1) {
        await fulfillJson(
          route,
          { message: 'Return exceeds purchased quantity', status: 409, fieldErrors: {} },
          409,
        )
        return
      }
      await fulfillJson(route, returnRequest({ reason: null, items: [] }), 201)
      return
    }
    await fulfillJson(route, [])
  })
  await page.route('**/api/returns/orders', (route) => fulfillJson(route, [returnableOrder()]))

  await page.goto('/warehouse/returns')
  await page.getByRole('button', { name: 'New return' }).click()
  const dialog = page.getByRole('dialog')

  await dialog.getByRole('button', { name: 'Request return' }).click()
  await expect(dialog.getByText('Choose a delivered order.')).toBeVisible()

  await dialog.getByLabel('Delivered order').selectOption('42')
  await dialog.getByRole('button', { name: 'Request return' }).click()
  await expect(dialog.getByText('Enter a quantity for at least one item.')).toBeVisible()

  await dialog.getByLabel('Quantity for Mechanical Keyboard').fill('4')
  await dialog.getByRole('button', { name: 'Request return' }).click()
  await expect(dialog.getByText('At most 3 can be returned.')).toBeVisible()

  await dialog.getByLabel('Quantity for Mechanical Keyboard').fill('2')
  await dialog.getByRole('button', { name: 'Request return' }).click()
  await expect(dialog.getByRole('alert')).toHaveText('Return exceeds purchased quantity')
  await expect(dialog.getByLabel('Quantity for Mechanical Keyboard')).toHaveValue('2')

  await dialog.getByRole('button', { name: 'Request return' }).click()
  await expect(page.getByRole('status')).toHaveText('Return #12 requested for order #42.')
  await expect.poll(() => attempts).toBe(2)
})
