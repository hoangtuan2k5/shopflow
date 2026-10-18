<script setup lang="ts">
import { computed } from 'vue'
import { VisAxis, VisGroupedBar, VisLine, VisXYContainer } from '@unovis/vue'
import {
  ChartContainer,
  ChartCrosshair,
  ChartTooltip,
  componentToString,
} from '@/components/ui/chart'
import type { DashboardPoint } from '@/api'

const props = defineProps<{
  points: DashboardPoint[]
  metric: 'paidAmount' | 'orderCount'
  label: string
  locale: string
  formatDate: (value: string) => string
  formatValue: (value: string | number) => string
}>()
const config = computed(() => ({
  [props.metric]: {
    label: props.label,
    color: props.metric === 'paidAmount' ? '#007a5c' : '#1e2a5a',
  },
}))
const data = computed(() => props.points.map((point, index) => ({ ...point, index })))
type Point = DashboardPoint & { index: number }
const x = (point: Point) => point.index
const y = (point: Point) => Number(point[props.metric])
const tooltip = computed(() =>
  componentToString(
    config.value,
    (point) => props.formatDate(String(point.date)),
    (value) => props.formatValue(String(value)),
  ),
)
const ticks = computed(() =>
  data.value
    .filter((_, i) => i % Math.max(1, Math.ceil(data.value.length / 4)) === 0)
    .map((p) => p.index),
)
function tickLabel(index: number) {
  const point = data.value[Math.round(index)]
  return point ? props.formatDate(point.date) : ''
}
</script>

<template>
  <ChartContainer :config="config" class="h-64" aria-hidden="true">
    <VisXYContainer
      :data="data"
      :y-domain="[0, undefined]"
      :duration="0"
      :margin="{ top: 12, right: 12, bottom: 0, left: 0 }"
    >
      <VisLine
        v-if="metric === 'paidAmount'"
        :x="x"
        :y="y"
        :color="config[metric]?.color"
        :line-width="3"
      />
      <VisGroupedBar v-else :x="x" :y="y" :color="config[metric]?.color" :rounded-corners="3" />
      <VisAxis type="x" :tick-values="ticks" :tick-format="tickLabel" :grid-line="false" />
      <VisAxis
        type="y"
        :num-ticks="4"
        :tick-format="
          (value: number) => new Intl.NumberFormat(locale, { notation: 'compact' }).format(value)
        "
      />
      <ChartTooltip />
      <ChartCrosshair :x="x" :y="y" :template="tooltip" :color="config[metric]?.color" />
    </VisXYContainer>
  </ChartContainer>
</template>
