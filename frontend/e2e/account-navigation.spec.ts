import { expect, test } from '@playwright/test'

const fullName = 'Nguyễn Hoàng Tuấn 👨‍👩‍👧‍👦 và tên hiển thị rất dài'
for (const width of [1280, 1024, 768, 390, 320]) {
  test(`signed-in account name is bounded and visible at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await page.route('**/api/auth/session', (route) =>
      route.fulfill({
        json: {
          authenticated: true,
          user: { userId: 27, username: 'customer', displayName: fullName, role: 'CUSTOMER' },
        },
      }),
    )
    await page.route('**/api/products*', (route) =>
      route.fulfill({ json: { items: [], nextCursor: null } }),
    )
    await page.goto('/customer')
    const account = page.getByRole('button', { name: fullName, exact: true })
    await expect(account).toHaveAttribute('title', fullName)
    await expect(account).toHaveAttribute('aria-haspopup', 'menu')
    const label = account.locator('span')
    await expect(label).toBeVisible()
    const value = await label.innerText()
    expect([
      ...new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(value),
    ]).toHaveLength(24)
    expect(value.endsWith('…')).toBe(true)
    await expect(page.getByText('Workspace', { exact: true })).toHaveCount(0)
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
    await page.screenshot({ path: `/tmp/shopflow-account-${width}.png` })
  })
}
