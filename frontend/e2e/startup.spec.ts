import { expect, test } from '@playwright/test'
import { mockSession } from './session'

test('Storefront starts while external font services are unresponsive', async ({ page }) => {
  await mockSession(page, null)
  await page.addInitScript(() => localStorage.setItem('shopflow.locale', 'en'))
  await page.route(/\/api\/products(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { items: [], nextCursor: null } }),
  )
  let release!: () => void
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route(/^https:\/\/fonts\.(googleapis|gstatic)\.com\//, async (route) => {
    await pending
    await route.fulfill({ contentType: 'text/css', body: '' })
  })
  try {
    await page.goto('/customer', { waitUntil: 'commit' })
    await expect(page.getByTestId('chat-open')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Pick fast. Order simply.' })).toBeVisible()
  } finally {
    release()
    await page.unrouteAll({ behavior: 'wait' })
  }
})

test('Vietnamese storefront font weights load from the application origin', async ({ page }) => {
  await mockSession(page, null)
  await page.route(/\/api\/products(?:\?.*)?$/, (route) =>
    route.fulfill({ json: { items: [], nextCursor: null } }),
  )
  const fontUrls: string[] = []
  page.on('request', (request) => {
    if (request.resourceType() === 'font') fontUrls.push(request.url())
  })
  await page.goto('/customer')
  const loaded = await page.evaluate(async () =>
    Promise.all(
      [400, 500, 600, 700, 900].map(async (weight) => {
        const faces = await document.fonts.load(
          `${weight} 16px "Be Vietnam Pro"`,
          'Màng lọc không khí',
        )
        return faces.length > 0 && faces.every((face) => face.status === 'loaded')
      }),
    ),
  )
  expect(loaded).toEqual([true, true, true, true, true])
  expect(fontUrls.length).toBeGreaterThan(0)
  expect(fontUrls.every((url) => new URL(url).origin === new URL(page.url()).origin)).toBe(true)
})
