import type { Page } from '@playwright/test'

export type MockRole = 'CUSTOMER' | 'WAREHOUSE' | 'SHOP_OWNER'

const displayNames: Record<MockRole, string> = {
  CUSTOMER: 'Khách hàng demo',
  WAREHOUSE: 'Nhân viên kho demo',
  SHOP_OWNER: 'Chủ shop demo',
}

/**
 * Mọi màn hình đều đọc phiên hiện tại khi khởi động, nên mọi spec phải trả lời lời gọi đó —
 * nếu không, request hỏng sẽ sinh console error và làm hỏng phần kiểm tra browserErrors.
 *
 * Playwright ưu tiên handler đăng ký sau, nên một test có thể gọi lại hàm này với role khác để
 * ghi đè mặc định của beforeEach.
 */
export async function mockSession(page: Page, role: MockRole | null) {
  await page.route('**/api/auth/session', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        role === null
          ? { authenticated: false, user: null }
          : {
              authenticated: true,
              user: {
                userId: 1,
                username: role.toLowerCase(),
                displayName: displayNames[role],
                role,
              },
            },
      ),
    }),
  )
}
