import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'

import App from './App.vue'
import { i18n } from './i18n'
import { queryClient } from './plugins/query'
import router from './router'
import './assets/main.css'

const app = createApp(App)

app.use(createPinia())
app.use(VueQueryPlugin, { queryClient })
app.use(i18n)
app.use(router)

app.mount('#app')
