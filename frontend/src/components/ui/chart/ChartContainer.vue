<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { computed } from 'vue'
import { useId } from 'reka-ui'
import { cn } from '@/lib/utils'
import { type ChartConfig } from '.'

// Adapted from shadcn-vue's MIT-licensed chart registry.
const props = defineProps<{ config: ChartConfig; class?: HTMLAttributes['class'] }>()
const id = `chart-${useId().replace(/:/g, '')}`
const colors = computed(() =>
  Object.fromEntries(
    Object.entries(props.config).map(([key, value]) => [`--color-${key}`, value.color]),
  ),
)
</script>

<template>
  <div
    data-slot="chart"
    :data-chart="id"
    :class="
      cn(
        'min-w-0 w-full text-xs [&_.tick_text]:!fill-muted-foreground [&_.tick_line]:!stroke-border/50 [&_[data-vis-xy-container]]:h-full [&_[data-vis-xy-container]]:w-full [&_[data-vis-single-container]]:h-full [&_[data-vis-single-container]]:w-full',
        props.class,
      )
    "
    :style="{
      ...colors,
      '--vis-font-family': 'var(--font-sans)',
      '--vis-tooltip-padding': '0px',
      '--vis-tooltip-background-color': 'transparent',
      '--vis-tooltip-border-color': 'transparent',
      '--vis-tooltip-shadow-color': 'transparent',
    }"
  >
    <slot />
  </div>
</template>
