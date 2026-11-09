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
  const { i18n } = await vite.ssrLoadModule('/src/i18n/index.ts')
  i18n.global.locale.value = 'vi'

  function renderForm(props: Record<string, unknown>) {
    const app = createSSRApp({ render: () => h(CheckoutForm, props) })
    app.use(i18n)
    return renderToString(app)
  }

  const successOrder = {
    id: 501,
    orderRef: '3f2a1c9e-7b44-4d1a-9f0e-5c8d2a6b1e73',
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

  const paymentHtml = await renderForm({
    lines: [],
    submitting: false,
    error: null,
    successOrder,
    paymentSubmitting: false,
    paymentError: null,
    paymentResult: null,
  })

  assert.match(paymentHtml, /id="payment-simulation"/)
  assert.doesNotMatch(paymentHtml, /Mô phỏng|demo|backend/)
  assert.match(paymentHtml, /#501/)
  assert.match(paymentHtml, /Chờ thanh toán/)
  assert.doesNotMatch(paymentHtml, /PENDING_PAYMENT/)
  assert.match(paymentHtml, /46\.970\.000/)
  assert.match(paymentHtml, />Thanh toán</)
  assert.doesNotMatch(paymentHtml, /Mô phỏng thất bại/)
  assert.doesNotMatch(paymentHtml, /Mô phỏng hết hạn/)

  const paymentResultHtml = await renderForm({
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
  })

  assert.match(paymentResultHtml, /Đã thanh toán/)
  assert.doesNotMatch(paymentResultHtml, /PENDING_PAYMENT|Payment SUCCESS|Order PAID/)
  assert.doesNotMatch(paymentResultHtml, /Mô phỏng thất bại/)

  for (const [status, title, reason] of [
    ['FAILED', 'Thanh toán thất bại', 'Declined by simulation'],
    ['EXPIRED', 'Thanh toán đã hết hạn', 'Expired by simulation'],
  ] as const) {
    const terminalHtml = await renderForm({
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
    })

    assert.match(terminalHtml, new RegExp(title))
    assert.match(terminalHtml, /Thanh toán lỗi/)
    assert.doesNotMatch(terminalHtml, /PENDING_PAYMENT|PAYMENT_FAILED|Payment /)
    assert.doesNotMatch(terminalHtml, new RegExp(reason))
  }

  const paymentLoadingHtml = await renderForm({
    lines: [],
    submitting: false,
    error: null,
    successOrder,
    paymentSubmitting: true,
    paymentError: null,
    paymentResult: null,
  })

  assert.match(paymentLoadingHtml, /Đang thanh toán…/)
  assert.match(paymentLoadingHtml, /disabled/)

  const paymentErrorHtml = await renderForm({
    lines: [],
    submitting: false,
    error: null,
    successOrder,
    paymentSubmitting: false,
    paymentError: { message: 'Order is not eligible for payment', status: 409 },
    paymentResult: null,
  })

  assert.match(paymentErrorHtml, /Chưa thể hoàn tất thanh toán/)
  assert.match(paymentErrorHtml, /Chờ thanh toán/)
  assert.doesNotMatch(paymentErrorHtml, /Order is not eligible for payment/)
  assert.match(paymentErrorHtml, /#501/)
  assert.match(paymentErrorHtml, />Thanh toán</)

  for (const [locale, pending, paid, failed] of [
    ['vi', 'Chờ thanh toán', 'Đã thanh toán', 'Thanh toán lỗi'],
    ['en', 'Pending payment', 'Paid', 'Payment failed'],
  ]) {
    i18n.global.locale.value = locale
    for (const [status, orderStatus, orderId, expected] of [
      ['SUCCESS', 'PAID', 501, paid],
      ['FAILED', 'PAYMENT_FAILED', 501, failed],
      ['EXPIRED', 'PAYMENT_FAILED', 501, failed],
      ['SUCCESS', 'PAID', 999, pending],
    ]) {
      const html = await renderForm({
        lines: [],
        submitting: false,
        error: null,
        successOrder,
        paymentSubmitting: false,
        paymentError: null,
        paymentResult: {
          id: 91,
          orderId,
          method: 'CARD',
          status,
          amount: 46970000,
          paidAt: null,
          failedReason: null,
          createdAt: '2026-07-21T09:00:00Z',
          orderStatus,
        },
      })
      const summary = html.match(/<dl[\s\S]*?<\/dl>/)?.[0] ?? ''
      assert.ok(summary.includes(expected))
      assert.doesNotMatch(html, /PENDING_PAYMENT|PAYMENT_FAILED|Payment SUCCESS|Order PAID/)
      if (orderId === 999) assert.match(html, /type="button"[^>]*>[\s\S]*?(?:Thanh toán|Pay)/)
    }
  }
  i18n.global.locale.value = 'vi'

  const errorHtml = await renderForm({
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
  })

  assert.match(errorHtml, /Coffee/)
  assert.match(errorHtml, /must not be blank/)
  assert.match(errorHtml, /aria-invalid="true"/)
})
