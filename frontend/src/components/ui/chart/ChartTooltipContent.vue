<script setup lang="ts">
import type { ChartConfig } from '.'

defineProps<{
  title: string
  payload: Record<string, unknown>
  config: ChartConfig
  formatValue: (value: unknown) => string
}>()
</script>

<template>
  <div class="grid min-w-40 gap-2 rounded-lg border bg-card px-3 py-2 text-xs shadow-lg">
    <p class="font-medium">{{ title }}</p>
    <div v-for="(item, key) in config" :key="key" class="flex items-center justify-between gap-4">
      <span class="flex items-center gap-2 text-muted-foreground">
        <span class="size-2 rounded-sm" :style="{ backgroundColor: item.color }" />{{ item.label }}
      </span>
      <span class="font-semibold tabular-nums">{{ formatValue(payload[key]) }}</span>
    </div>
  </div>
</template>
