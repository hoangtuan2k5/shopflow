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

  const successOrder = {
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
  }

  const paymentHtml = await renderToString(
    createSSRApp({
      render: () =>
        h(CheckoutForm, {
          lines: [],
          submitting: false,
          error: null,
          successOrder,
          paymentSubmitting: false,
          paymentError: null,
          paymentResult: null,
        }),
    }),
  )

  assert.match(paymentHtml, /id="payment-simulation"/)
  assert.match(paymentHtml, /Mô phỏng thanh toán/)
  assert.match(paymentHtml, /Đơn hàng #501/)
  assert.match(paymentHtml, /PENDING_PAYMENT/)
  assert.match(paymentHtml, /46\.970\.000/)
  assert.match(paymentHtml, /Thanh toán thành công/)
  assert.match(paymentHtml, /Mô phỏng thất bại/)
  assert.match(paymentHtml, /Mô phỏng hết hạn/)

  const paymentResultHtml = await renderToString(
    createSSRApp({
      render: () =>
        h(CheckoutForm, {
          lines: [],
          submitting: false,
          error: null,
          successOrder,
          paymentSubmitting: false,
          paymentError: null,
          paymentResult: {
            id: 91,
            orderId: 501,
            method: 'CARD',
            status: 'SUCCESS',
            amount: 46_970_000,
            paidAt: '2026-07-21T09:00:00Z',
            failedReason: null,
            createdAt: '2026-07-21T09:00:00Z',
            orderStatus: 'PAID',
          },
        }),
    }),
  )

  assert.match(paymentResultHtml, /Payment SUCCESS · Order PAID/)
  assert.doesNotMatch(paymentResultHtml, /Mô phỏng thất bại/)

  for (const [status, title, reason] of [
    ['FAILED', 'Thanh toán thất bại', 'Declined by simulation'],
    ['EXPIRED', 'Thanh toán đã hết hạn', 'Expired by simulation'],
  ] as const) {
    const terminalHtml = await renderToString(
      createSSRApp({
        render: () =>
          h(CheckoutForm, {
            lines: [],
            submitting: false,
            error: null,
            successOrder,
            paymentSubmitting: false,
            paymentError: null,
            paymentResult: {
              id: 92,
              orderId: 501,
              method: 'CARD',
              status,
              amount: 46_970_000,
              paidAt: null,
              failedReason: reason,
              createdAt: '2026-07-21T09:00:00Z',
              orderStatus: 'PAYMENT_FAILED',
            },
          }),
      }),
    )

    assert.match(terminalHtml, new RegExp(title))
    assert.match(terminalHtml, new RegExp(`Payment ${status} · Order PAYMENT_FAILED`))
    assert.match(terminalHtml, new RegExp(reason))
  }

  const paymentLoadingHtml = await renderToString(
    createSSRApp({
      render: () =>
        h(CheckoutForm, {
          lines: [],
          submitting: false,
          error: null,
          successOrder,
          paymentSubmitting: true,
          paymentError: null,
          paymentResult: null,
        }),
    }),
  )

  assert.match(paymentLoadingHtml, /Đang xử lý kết quả thanh toán…/)
  assert.match(paymentLoadingHtml, /disabled/)

  const paymentErrorHtml = await renderToString(
    createSSRApp({
      render: () =>
        h(CheckoutForm, {
          lines: [],
          submitting: false,
          error: null,
          successOrder,
          paymentSubmitting: false,
          paymentError: { message: 'Order is not eligible for payment', status: 409 },
          paymentResult: null,
        }),
    }),
  )

  assert.match(paymentErrorHtml, /Order is not eligible for payment/)
  assert.match(paymentErrorHtml, /Đơn hàng #501/)
  assert.match(paymentErrorHtml, /Thanh toán thành công/)

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
          paymentSubmitting: false,
          paymentError: null,
          paymentResult: null,
        }),
    }),
  )

  assert.match(errorHtml, /Coffee/)
  assert.match(errorHtml, /must not be blank/)
  assert.match(errorHtml, /aria-invalid="true"/)
})
