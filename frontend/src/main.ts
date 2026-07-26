import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'

import App from './App.vue'
import { onUnauthorized } from './api'
import { i18n } from './i18n'
import { useSessionStore } from './stores/session'
import { queryClient } from './plugins/query'
import router from './router'
import './assets/main.css'

const app = createApp(App)

app.use(createPinia())
app.use(VueQueryPlugin, { queryClient })
app.use(i18n)
app.use(router)

// Phiên hết hạn giữa chừng chỉ lộ ra qua một 401 bất kỳ; không bắt ở đây thì người dùng mắc kẹt
// trên màn hình lỗi mà không biết phải đăng nhập lại.
onUnauthorized(() => {
  const session = useSessionStore()
  if (!session.isAuthenticated) return
  session.forget()
  queryClient.clear()
  void router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
})

app.mount('#app')
