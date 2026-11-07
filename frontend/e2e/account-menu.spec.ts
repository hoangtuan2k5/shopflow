import { expect, test } from '@playwright/test'

async function fixture(page: import('@playwright/test').Page, fails = false) {
  let signedIn = true
  const user = { userId: 27, username: 'u_buyer', displayName: 'Google Buyer', role: 'CUSTOMER' }
  await page.route('**/api/auth/session', (route) =>
    route.fulfill({ json: { authenticated: signedIn, user: signedIn ? user : null } }),
  )
  await page.route('**/api/auth/account', (route) =>
    route.fulfill({
      json: {
        displayName: user.displayName,
        email: 'buyer@example.com',
        capabilities: { manageDelivery: true, changePassword: false, setPassword: true },
      },
    }),
  )
  await page.route('**/api/products*', (route) =>
    route.fulfill({ json: { items: [], nextCursor: null } }),
  )
  await page.route('**/api/auth/logout', (route) => {
    if (fails) return route.fulfill({ status: 503, json: { message: 'Unavailable' } })
    signedIn = false
    return route.fulfill({ status: 204 })
  })
}

test('account menu has settings and keyboard dismissal; logout preserves the cart', async ({
  page,
}) => {
  await fixture(page)
  await page.goto('/customer')
  await page.evaluate(() =>
    localStorage.setItem(
      'shopflow.cart.v1',
      JSON.stringify({ version: 1, items: [{ productId: 1, quantity: 2 }] }),
    ),
  )
  const trigger = page.getByRole('button', { name: 'Google Buyer', exact: true })
  await trigger.click()
  await expect(page.getByRole('menuitem', { name: 'Delivery information' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: 'Set up password' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(trigger).toBeFocused()
  await expect(page.getByRole('menu')).toHaveCount(0)
  await trigger.click()
  await page.getByRole('menuitem', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(await page.evaluate(() => localStorage.getItem('shopflow.cart.v1'))).toContain('quantity')
})

test('logout failure stays signed in and does not claim success', async ({ page }) => {
  await fixture(page, true)
  await page.goto('/customer')
  await page.getByRole('button', { name: 'Google Buyer', exact: true }).click()
  await page.getByRole('menuitem', { name: 'Sign out' }).click()
  await expect(page.getByRole('alert')).toHaveText(
    'Sign-out could not be confirmed. Please try again.',
  )
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: 'Google Buyer', exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/customer$/)
})
