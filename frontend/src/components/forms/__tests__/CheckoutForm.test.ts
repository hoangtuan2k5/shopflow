import assert from 'node:assert/strict'
import test from 'node:test'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createServer } from 'vite'

test('renders payment handoff and field-specific server errors', async (context) => {
  const vite = await createServer({
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })
  context.after(() => vite.close())
  const { default: CheckoutForm } = await vite.ssrLoadModule(
    '/src/components/forms/CheckoutForm.vue',
  )

  const paymentHtml = await renderToString(
    createSSRApp({
      render: () =>
        h(CheckoutForm, {
          lines: [],
          submitting: false,
          error: null,
          successOrder: {
            id: 501,
            status: 'PENDING_PAYMENT',
            deliveryStatus: 'NONE',
            paymentMethod: 'CARD',
            totalAmount: 46_970_000,
            customer: { fullName: 'Guest', email: null, phone: null },
            shippingAddress: {
              receiverName: 'Guest',
              phone: '0900000000',
              addressLine: '1 Main',
              district: null,
              city: 'Hanoi',
            },
            items: [],
            createdAt: '2026-07-20T08:00:00Z',
          },
        }),
    }),
  )

  assert.match(paymentHtml, /id="payment-simulation"/)
  assert.match(paymentHtml, /Order #501/)
  assert.match(paymentHtml, /PENDING_PAYMENT/)
  assert.match(paymentHtml, /46\.970\.000/)

  const errorHtml = await renderToString(
    createSSRApp({
      render: () =>
        h(CheckoutForm, {
          lines: [
            {
              product: { id: 1, name: 'Coffee', price: 100_000, stockStatus: 'IN_STOCK' },
              quantity: 1,
            },
          ],
          submitting: false,
          error: {
            message: 'Invalid order request',
            fieldErrors: { 'shippingAddress.phone': 'must not be blank' },
          },
          successOrder: null,
        }),
    }),
  )

  assert.match(errorHtml, /Coffee/)
  assert.match(errorHtml, /must not be blank/)
  assert.match(errorHtml, /aria-invalid="true"/)
})
