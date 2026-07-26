import { createI18n } from 'vue-i18n'
import { en } from './en'
import { vi } from './vi'

export type AppLocale = 'en' | 'vi'

const STORAGE_KEY = 'shopflow.locale'

function initialLocale(): AppLocale {
  const stored = typeof localStorage === 'undefined' ? null : localStorage.getItem(STORAGE_KEY)
  if (stored === 'en' || stored === 'vi') return stored
  const browserLanguage = typeof navigator === 'undefined' ? '' : (navigator.language ?? '')
  return browserLanguage.toLowerCase().startsWith('vi') ? 'vi' : 'en'
}

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: 'en',
  messages: { en, vi },
  missingWarn: false,
  fallbackWarn: false,
})

export function setLocale(locale: AppLocale) {
  i18n.global.locale.value = locale
  if (typeof localStorage !== 'undefined') localStorage.setItem(STORAGE_KEY, locale)
  if (typeof document !== 'undefined') document.documentElement.lang = locale
}

if (typeof document !== 'undefined') {
  document.documentElement.lang = i18n.global.locale.value
}
