import { expect, test, type Page } from '@playwright/test'
import { mockSession } from './session'

const product = {
  id: 1,
  name: 'Owned filter',
  price: 100000,
  stockStatus: 'IN_STOCK',
  imageUrl: null,
}
const orderRef = 'f5697b8f-b76e-480d-bc99-f96dad071a89'

async function setup(page: Page, signedIn = false) {
  await mockSession(page, signedIn ? 'CUSTOMER' : null)
  await page.route(/\/api\/products(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { items: [product], nextCursor: null } }),
  )
  await page.route('**/api/products/1', (route) =>
    route.fulfill({ json: { ...product, description: 'Filter' } }),
  )
}

async function checkout(page: Page) {
  await page.goto('/customer')
  await page.getByRole('button', { name: 'Add to cart', exact: true }).click()
  await page.getByRole('button', { name: 'Open cart', exact: true }).click()
  await page.getByRole('button', { name: 'Proceed to checkout' }).click()
}

async function fill(page: Page) {
  await page.getByLabel('Customer name').fill('Buyer')
  await page.getByLabel('Receiver name').fill('Receiver')
  await page.getByLabel('Receiver phone').fill('0900000000')
  await page.getByLabel('Address').fill('1 Test Street')
  await page.getByLabel('Province/City').fill('Test City')
}

function created(body: Record<string, unknown>) {
  return {
    id: 42,
    orderRef,
    status: 'PENDING_PAYMENT',
    deliveryStatus: 'NONE',
    paymentMethod: 'CARD',
    totalAmount: 100000,
    customer: body.customer,
    shippingAddress: body.shippingAddress,
    items: [
      {
        productId: 1,
        productName: product.name,
        unitPrice: 100000,
        quantity: 1,
        lineTotal: 100000,
      },
    ],
    createdAt: '2026-11-06T00:00:00Z',
  }
}

test('Guest keeps the cart and signs in before manually placing and paying an order', async ({
  page,
}) => {
  await setup(page)
  let orders = 0
  await page.route('**/api/auth/login', async (route) => {
    await mockSession(page, 'CUSTOMER')
    await route.fulfill({
      json: { userId: 1, username: 'customer', displayName: 'Buyer', role: 'CUSTOMER' },
    })
  })
  await page.route('**/api/orders', (route) => {
    orders += 1
    const body = route.request().postDataJSON()
    expect(body.items).toEqual([{ productId: 1, quantity: 1 }])
    expect(body).not.toHaveProperty('accountUserId')
    expect(body).not.toHaveProperty('role')
    return route.fulfill({ status: 201, json: created(body) })
  })
  await page.route(`**/api/orders/${orderRef}/payments`, (route) =>
    route.fulfill({
      json: {
        id: 1,
        orderId: 42,
        method: 'CARD',
        status: 'SUCCESS',
        amount: 100000,
        paidAt: '2026-11-06T00:00:00Z',
        failedReason: null,
        createdAt: '2026-11-06T00:00:00Z',
        orderStatus: 'PAID',
      },
    }),
  )
  await checkout(page)
  await expect(page).toHaveURL(/\/login/)
  expect(new URL(page.url()).searchParams.get('redirect')).toBe('/customer?checkout=1')
  expect(orders).toBe(0)
  await page.getByLabel('Username').fill('customer')
  await page.getByLabel('Password').fill('Customer@2026')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('dialog', { name: 'Checkout', exact: true })).toBeVisible()
  expect(orders).toBe(0)
  await fill(page)
  await page.getByRole('button', { name: 'Place order', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Pay', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Pay', exact: true }).click()
  await expect(page.getByText('Payment succeeded', { exact: true })).toBeVisible()
  expect(orders).toBe(1)
})

test('Order rejection retains the form/cart and does not blindly retry', async ({ page }) => {
  await setup(page, true)
  let orders = 0
  await page.route('**/api/orders', (route) => {
    orders += 1
    return route.fulfill({
      status: 400,
      json: {
        message: 'Insufficient stock',
        status: 400,
        unavailableProductIds: [],
        insufficientItems: [],
        fieldErrors: {},
      },
    })
  })
  await checkout(page)
  await fill(page)
  await page.getByRole('button', { name: 'Place order', exact: true }).click()
  await expect(page.getByText('Insufficient stock', { exact: true })).toBeVisible()
  await expect(page.getByLabel('Customer name')).toHaveValue('Buyer')
  expect(orders).toBe(1)
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('shopflow.cart.v1')!))).toEqual({
    version: 1,
    items: [{ productId: 1, quantity: 1 }],
  })
})

test('A generic payment 404 stays on the current order without creating another order', async ({
  page,
}) => {
  await setup(page, true)
  let orders = 0
  await page.route('**/api/orders', (route) => {
    orders += 1
    return route.fulfill({ status: 201, json: created(route.request().postDataJSON()) })
  })
  await page.route(`**/api/orders/${orderRef}/payments`, (route) =>
    route.fulfill({ status: 404, json: { message: 'Order not found', status: 404 } }),
  )
  await checkout(page)
  await fill(page)
  await page.getByRole('button', { name: 'Place order', exact: true }).click()
  await page.getByRole('button', { name: 'Pay', exact: true }).click()
  await expect(page.getByText('The payment could not be completed.', { exact: true })).toBeVisible()
  expect(orders).toBe(1)
})

const savedDelivery = {
  receiverName: 'Saved receiver',
  phone: '0123456789',
  addressLine: 'Saved street',
  district: 'Saved district',
  city: 'Saved city',
}
async function eligible(page: Page) {
  await setup(page, true)
  await page.route('**/api/auth/account', (route) =>
    route.fulfill({
      json: {
        displayName: 'Buyer',
        email: 'buyer@example.com',
        capabilities: { manageDelivery: true, changePassword: true, setPassword: false },
      },
    }),
  )
}

test('checkout prefills a saved address and keeps order edits separate', async ({ page }) => {
  await eligible(page)
  let writes = 0
  await page.route('**/api/auth/account/delivery', (route) => {
    if (route.request().method() !== 'GET') writes++
    return route.fulfill({ json: { delivery: savedDelivery } })
  })
  await page.route('**/api/orders', (route) => {
    const body = route.request().postDataJSON()
    expect(body.shippingAddress.addressLine).toBe('Only this order')
    return route.fulfill({ status: 201, json: created(body) })
  })
  await checkout(page)
  await expect(page.getByLabel('Receiver name')).toHaveValue('Saved receiver')
  await page.getByLabel('Customer name').fill('Buyer')
  await page.getByLabel('Address').fill('Only this order')
  await page.getByRole('button', { name: 'Place order', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Pay', exact: true })).toBeVisible()
  expect(writes).toBe(0)
  expect(
    await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage })),
  ).not.toContain('Saved street')
})

test('a late saved address cannot overwrite checkout edits', async ({ page }) => {
  await eligible(page)
  let release!: () => void
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/auth/account/delivery', async (route) => {
    await pending
    await route.fulfill({ json: { delivery: savedDelivery } })
  })
  await checkout(page)
  await page.getByLabel('Address').fill('Already typed')
  const loaded = page.waitForResponse('**/api/auth/account/delivery')
  release()
  await loaded
  await expect(page.getByLabel('Address')).toHaveValue('Already typed')
  await expect(page.getByLabel('Receiver name')).toHaveValue('')
})

test('delivery load failure permits manual checkout', async ({ page }) => {
  await eligible(page)
  await page.route('**/api/auth/account/delivery', (route) =>
    route.fulfill({ status: 503, json: { message: 'Unavailable' } }),
  )
  await checkout(page)
  await expect(
    page.getByText('Could not load your saved address. Enter your delivery details below.'),
  ).toBeVisible()
  await fill(page)
  await expect(page.getByLabel('Address')).toHaveValue('1 Test Street')
})
