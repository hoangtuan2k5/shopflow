import { h, render } from 'vue'
import ChartTooltipContent from './ChartTooltipContent.vue'
import type { ChartConfig } from '.'

export function componentToString(
  config: ChartConfig,
  title: (data: Record<string, unknown>) => string,
  formatValue: (value: unknown) => string,
) {
  return (data: Record<string, unknown>) => {
    const div = document.createElement('div')
    render(h(ChartTooltipContent, { config, title: title(data), payload: data, formatValue }), div)
    const html = div.innerHTML
    render(null, div)
    return html
  }
}
