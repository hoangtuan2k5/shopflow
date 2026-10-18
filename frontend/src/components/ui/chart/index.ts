export type ChartConfig = Record<string, { label: string; color: string }>
export { default as ChartContainer } from './ChartContainer.vue'
export { default as ChartTooltipContent } from './ChartTooltipContent.vue'
export { componentToString } from './utils'
export { VisCrosshair as ChartCrosshair, VisTooltip as ChartTooltip } from '@unovis/vue'
