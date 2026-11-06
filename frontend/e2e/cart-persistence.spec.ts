import { expect, test, type Page } from '@playwright/test'
import { mockSession } from './session'

const key = 'shopflow.cart.v1'
const first = { id: 1, name: 'Filter one', price: 100000, stockStatus: 'IN_STOCK', imageUrl: null }
const later = { ...first, id: 45, name: 'Filter outside first page', price: 200000 }

async function catalog(page: Page) {
  await mockSession(page, null)
  await page.route(/\/api\/products(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { items: [first], nextCursor: null } }),
  )
  const details: number[] = []
  await page.route(/\/api\/products\/\d+$/, (route) => {
    const id = Number(new URL(route.request().url()).pathname.split('/').at(-1))
    details.push(id)
    return route.fulfill({
      json: { ...(id === 45 ? later : first), description: 'Fresh API data' },
    })
  })
  return details
}

async function add(page: Page) {
  await page
    .locator('article')
    .filter({ hasText: first.name })
    .getByRole('button', { name: 'Add to cart' })
    .click()
}

async function cart(page: Page) {
  await page.getByRole('button', { name: 'Open cart', exact: true }).click()
  return page.getByRole('dialog')
}

test('Cart survives password login, reload and a full-page OAuth cancellation', async ({
  page,
}) => {
  await catalog(page)
  await page.goto('/customer')
  await add(page)
  await add(page)
  expect(await page.evaluate((key) => JSON.parse(localStorage.getItem(key)!), key)).toEqual({
    version: 1,
    items: [{ productId: 1, quantity: 2 }],
  })
  await page.route('**/api/auth/login', async (route) => {
    await mockSession(page, 'CUSTOMER')
    await route.fulfill({
      json: { userId: 1, username: 'buyer', displayName: 'Buyer', role: 'CUSTOMER' },
    })
  })
  await page.getByRole('link', { name: 'Sign in', exact: true }).click()
  await page.getByLabel('Username').fill('buyer@example.com')
  await page.getByLabel('Password').fill('Buyerpass1!')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL(/\/customer$/)
  await page.reload()
  const dialog = await cart(page)
  await expect(dialog.getByText('2 products selected')).toBeVisible()
  await page.keyboard.press('Escape')
  await mockSession(page, null)
  await page.goto('/login')
  await page.route('**/api/oauth2/authorization/google*', (route) =>
    route.fulfill({ status: 303, headers: { Location: '/login?google=failed' }, body: '' }),
  )
  await page.getByRole('link', { name: 'Continue with Google' }).click()
  await expect(page.getByRole('alert')).toContainText('Google sign-in could not be completed.')
  await page.goto('/customer')
  await expect((await cart(page)).getByText('2 products selected')).toBeVisible()
  expect(
    await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage })),
  ).not.toMatch(/Buyerpass|buyer@example|orderRef|addressLine|password|token/i)
})

test('Restores items outside the first page and uses current API prices instead of saved prices', async ({
  page,
}) => {
  await page.addInitScript(
    ({ key, id }) =>
      localStorage.setItem(
        key,
        JSON.stringify({
          version: 1,
          items: [{ productId: id, quantity: 3, price: 1, name: 'Forged product' }],
        }),
      ),
    { key, id: 45 },
  )
  const details = await catalog(page)
  await page.goto('/customer')
  const dialog = await cart(page)
  await expect(dialog.getByText(later.name, { exact: true })).toBeVisible()
  await expect(dialog.getByText(/600.000/)).toBeVisible()
  await expect(dialog.getByText('Forged product')).toHaveCount(0)
  expect(details).toEqual([45])
})

test('Checkout return opens the restored form without creating an order automatically', async ({
  page,
}) => {
  await page.addInitScript(
    (key) =>
      localStorage.setItem(
        key,
        JSON.stringify({ version: 1, items: [{ productId: 1, quantity: 2 }] }),
      ),
    key,
  )
  await catalog(page)
  let orders = 0
  await page.route('**/api/orders', () => {
    orders += 1
  })
  await page.goto('/customer?checkout=1')
  await expect(page.getByRole('dialog', { name: 'Checkout', exact: true })).toBeVisible()
  await expect(page.getByLabel('Customer name')).toHaveValue('')
  expect(orders).toBe(0)
  await expect(page).toHaveURL(/\/customer\?checkout=1$/)
})

test('Malformed, overflow and oversized saved carts fail safely without fetching forged ids', async ({
  page,
}) => {
  const details = await catalog(page)
  for (const raw of [
    '{broken',
    JSON.stringify({ version: 1, items: [{ productId: 1, quantity: 2147483648 }] }),
    JSON.stringify({ version: 1, items: [{ productId: 9007199254740992, quantity: 1 }] }),
    ' '.repeat(65537),
  ]) {
    await page.goto('/customer')
    await page.evaluate(({ key, raw }) => localStorage.setItem(key, raw), { key, raw })
    await page.reload()
    await expect(page.getByRole('alert')).toContainText('This cart cannot be saved')
    await expect((await cart(page)).getByText('Your cart is empty')).toBeVisible()
    await page.keyboard.press('Escape')
  }
  expect(details).toEqual([])
})

test('Unavailable storage keeps the RAM cart and warns before an OAuth navigation', async ({
  page,
}) => {
  await page.addInitScript((key) => {
    const get = Storage.prototype.getItem
    const set = Storage.prototype.setItem
    Storage.prototype.getItem = function (name) {
      if (name === key) throw new DOMException('Blocked', 'SecurityError')
      return get.call(this, name)
    }
    Storage.prototype.setItem = function (name, value) {
      if (name === key) throw new DOMException('Quota', 'QuotaExceededError')
      return set.call(this, name, value)
    }
  }, key)
  await catalog(page)
  await page.goto('/customer')
  await add(page)
  await expect((await cart(page)).getByText('1 product selected')).toBeVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('link', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('continuing to Google may lose it')
  await expect(page.getByRole('link', { name: 'Continue with Google' })).toBeVisible()
})

test('Deleted or out-of-stock items remain visible as issues until removed or refreshed', async ({
  page,
}) => {
  await page.addInitScript(
    (key) =>
      localStorage.setItem(
        key,
        JSON.stringify({
          version: 1,
          items: [
            { productId: 1, quantity: 1 },
            { productId: 45, quantity: 1 },
          ],
        }),
      ),
    key,
  )
  await catalog(page)
  await page.route('**/api/products/1', (route) =>
    route.fulfill({ status: 404, json: { message: 'Not found' } }),
  )
  await page.route('**/api/products/45', (route) =>
    route.fulfill({ json: { ...later, stockStatus: 'OUT_OF_STOCK' } }),
  )
  await page.goto('/customer?checkout=1')
  const dialog = page.getByRole('dialog', { name: 'Cart', exact: true })
  await expect(dialog.getByRole('alert')).toContainText('Some items could not be loaded')
  await expect(dialog.getByRole('button', { name: 'Proceed to checkout' })).toBeDisabled()
  await dialog.getByRole('button', { name: 'Remove', exact: true }).first().click()
  await page.route('**/api/products/45', (route) => route.fulfill({ json: later }))
  await dialog.getByRole('button', { name: 'Refresh cart' }).click()
  await expect(page.getByRole('dialog', { name: 'Checkout', exact: true })).toBeVisible()
  await expect(page.getByLabel('Customer name')).toHaveValue('')
})

test('A 401 clears checkout PII but retains cart items for the next account', async ({ page }) => {
  await catalog(page)
  await mockSession(page, 'CUSTOMER')
  await page.goto('/customer')
  await add(page)
  const dialog = await cart(page)
  await dialog.getByRole('button', { name: 'Proceed to checkout' }).click()
  await page.getByLabel('Customer name').fill('Private Buyer')
  await page.getByLabel('Receiver name').fill('Private Receiver')
  await page.getByLabel('Receiver phone').fill('0900000000')
  await page.getByLabel('Address').fill('Private address')
  await page.getByLabel('City').fill('Private city')
  await page.route('**/api/orders', (route) =>
    route.fulfill({ status: 401, json: { message: 'Unauthorized' } }),
  )
  await page.getByRole('button', { name: 'Place order', exact: true }).click()
  await expect(page).toHaveURL(/\/login/)
  expect(new URL(page.url()).searchParams.get('redirect')).toBe('/customer?checkout=1')
  await mockSession(page, null)
  await page.goto('/customer?checkout=1')
  await expect(page.getByLabel('Customer name')).toHaveValue('')
  await expect(page.getByLabel('Address')).toHaveValue('')
  expect(await page.evaluate((key) => localStorage.getItem(key), key)).not.toMatch(
    /Private|orderRef|address|phone/i,
  )
})

test('A cart at the integer quantity boundary rejects increment without wrapping or truncation', async ({
  page,
}) => {
  await page.addInitScript(
    (key) =>
      localStorage.setItem(
        key,
        JSON.stringify({ version: 1, items: [{ productId: 1, quantity: 2147483647 }] }),
      ),
    key,
  )
  await catalog(page)
  await page.goto('/customer')
  const dialog = await cart(page)
  await dialog.getByRole('button', { name: 'Increase quantity of Filter one', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('2,147,483,647')
  expect(await page.evaluate((key) => JSON.parse(localStorage.getItem(key)!), key)).toEqual({
    version: 1,
    items: [{ productId: 1, quantity: 2147483647 }],
  })
})
