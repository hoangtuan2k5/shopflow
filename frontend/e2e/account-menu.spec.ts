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

test('google-only password setup uses private endpoints and signs out after confirmation', async ({
  page,
}) => {
  await fixture(page)
  await page.route('**/api/auth/account/password/setup', (route) =>
    route.fulfill({
      status: 202,
      json: {
        challengeId: 'd3fdcaca-2441-4fe2-b8ac-1e52af999c10',
        message: 'Issued',
        expiresInSeconds: 600,
        resendAfterSeconds: 60,
      },
    }),
  )
  let requestBody: unknown
  await page.route('**/api/auth/account/password/setup/confirm', (route) => {
    requestBody = route.request().postDataJSON()
    return route.fulfill({ status: 204 })
  })
  await page.goto('/account/password')
  await page.getByRole('button', { name: 'Send verification code' }).click()
  await page.getByLabel('Verification code', { exact: true }).fill('123456')
  await page.getByLabel('New password', { exact: true }).fill('Newpass1!')
  await page.getByLabel('Confirm new password').fill('Newpass1!')
  await page.getByRole('button', { name: 'Save password and sign out' }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(requestBody).toEqual({
    challengeId: 'd3fdcaca-2441-4fe2-b8ac-1e52af999c10',
    otp: '123456',
    newPassword: 'Newpass1!',
  })
  const storage = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))
  expect(storage).not.toContain('Newpass1!')
  expect(storage).not.toContain('123456')
})

test('local password change checks confirmation and requires the current password', async ({
  page,
}) => {
  await fixture(page)
  await page.route('**/api/auth/account', (route) =>
    route.fulfill({
      json: {
        displayName: 'Google Buyer',
        email: 'buyer@example.com',
        capabilities: { manageDelivery: true, changePassword: true, setPassword: false },
      },
    }),
  )
  let calls = 0
  await page.route('**/api/auth/account/password/change', (route) => {
    calls++
    expect(route.request().postDataJSON()).toEqual({
      currentPassword: 'Oldpass1!',
      newPassword: 'Newpass1!',
    })
    return route.fulfill({ status: 204 })
  })
  await page.goto('/account/password')
  await page.getByLabel('Current password', { exact: true }).fill('Oldpass1!')
  await page.getByLabel('New password', { exact: true }).fill('Newpass1!')
  await page.getByLabel('Confirm new password').fill('Otherpass1!')
  await page.getByRole('button', { name: 'Save password and sign out' }).click()
  await expect(page.getByRole('alert')).toHaveText('Passwords do not match.')
  expect(calls).toBe(0)
  await page.getByLabel('Confirm new password').fill('Newpass1!')
  await page.getByRole('button', { name: 'Save password and sign out' }).click()
  await expect(page).toHaveURL(/\/login$/)
})

test('delivery settings save and delete one private address without browser persistence', async ({
  page,
}) => {
  await fixture(page)
  let delivery: unknown = null
  await page.route('**/api/auth/account/delivery', (route) => {
    const method = route.request().method()
    if (method === 'PUT') delivery = route.request().postDataJSON()
    if (method === 'DELETE') {
      delivery = null
      return route.fulfill({ status: 204 })
    }
    return route.fulfill({ json: { delivery } })
  })
  await page.goto('/account/delivery')
  await page.getByLabel('Receiver name').fill('Private Receiver')
  await page.getByLabel('Receiver phone').fill('0123456789')
  await page.getByLabel('Address', { exact: true }).fill('Private street 45')
  await page.getByLabel('Province/City').fill('Hanoi')
  await page.getByRole('button', { name: 'Save delivery information' }).click()
  await expect(page.getByRole('status')).toHaveText('Delivery information updated.')
  await page.reload()
  await expect(page.getByLabel('Receiver name')).toHaveValue('Private Receiver')
  expect(
    await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage })),
  ).not.toContain('Private')
  await page.getByRole('button', { name: 'Delete saved address' }).click()
  await expect(page.getByLabel('Receiver name')).toHaveValue('')
  expect(delivery).toBeNull()
})
