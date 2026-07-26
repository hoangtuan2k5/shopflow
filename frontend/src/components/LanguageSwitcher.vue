<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { setLocale, type AppLocale } from '@/i18n'

const { t, locale } = useI18n()

function onChange(event: Event) {
  setLocale((event.target as HTMLSelectElement).value as AppLocale)
}
</script>

<template>
  <select
    class="h-9 shrink-0 rounded-md border border-input bg-card px-2 text-sm outline-none transition-colors focus-visible:ring-2 focus-visible:ring-ring"
    :value="locale"
    :aria-label="t('shell.language.label')"
    @change="onChange"
  >
    <option value="en">English</option>
    <option value="vi">Tiếng Việt</option>
  </select>
</template>
