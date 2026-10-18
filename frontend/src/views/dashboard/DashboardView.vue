<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import { VisDonut, VisSingleContainer } from '@unovis/vue'
import { IconArrowRight, IconChartBar, IconRefresh } from '@tabler/icons-vue'
import { getDashboard, type DashboardFilter } from '@/api'
import { Button, buttonVariants } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ChartContainer } from '@/components/ui/chart'
import DashboardTrend from './DashboardTrend.vue'

const { t, locale } = useI18n()
function todayInVietnam() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}
function daysBefore(date: string, days: number) {
  const value = new Date(`${date}T00:00:00Z`)
  value.setUTCDate(value.getUTCDate() - days)
  return value.toISOString().slice(0, 10)
}
const to = ref(todayInVietnam())
const from = ref(daysBefore(to.value, 29))
const groupBy = ref<'day' | 'month'>('day')
const applied = ref<DashboardFilter>({ from: from.value, to: to.value, groupBy: groupBy.value })
const invalid = ref(false)
const dashboard = useQuery({
  queryKey: computed(() => ['dashboard', applied.value]),
  queryFn: ({ signal }) => getDashboard(applied.value, signal),
  retry: false,
})
const data = computed(() => dashboard.data.value)
function apply() {
  const start = Date.parse(`${from.value}T00:00:00Z`)
  const end = Date.parse(`${to.value}T00:00:00Z`)
  invalid.value =
    !Number.isFinite(start) || !Number.isFinite(end) || start > end || end - start > 365 * 86400000
  if (!invalid.value) applied.value = { from: from.value, to: to.value, groupBy: groupBy.value }
}
function preset(days: number) {
  to.value = todayInVietnam()
  from.value = daysBefore(to.value, days - 1)
  apply()
}
function money(value: string | number) {
  return new Intl.NumberFormat(locale.value, {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(BigInt(value))
}
function count(value: string | number) {
  return new Intl.NumberFormat(locale.value).format(Number(value))
}
function date(value: string) {
  return new Intl.DateTimeFormat(locale.value, {
    timeZone: 'UTC',
    day: '2-digit',
    month: 'short',
    ...(applied.value.groupBy === 'month' ? { year: 'numeric' as const } : {}),
  }).format(new Date(`${value}T00:00:00Z`))
}
const cards = computed(() =>
  data.value
    ? [
        {
          label: t('dashboard.paid'),
          value: money(data.value.paidAmount),
          scope: t('dashboard.inPeriod'),
        },
        {
          label: t('dashboard.orders'),
          value: count(data.value.orderCount),
          scope: t('dashboard.inPeriod'),
        },
        {
          label: t('dashboard.processing'),
          value: count(data.value.processingOrders),
          scope: t('dashboard.current'),
        },
        {
          label: t('dashboard.lowStock'),
          value: count(data.value.lowStockProducts),
          scope: t('dashboard.current'),
        },
      ]
    : [],
)
const colors = ['#64748b', '#007a5c', '#c73e3e', '#b7791f']
const statuses = computed(
  () =>
    data.value?.statuses.map((s, i) => ({
      ...s,
      label: t(`dashboard.status.${s.status}`),
      color: colors[i] ?? '#64748b',
    })) ?? [],
)
const statusConfig = computed(() =>
  Object.fromEntries(statuses.value.map((s) => [s.status, { label: s.label, color: s.color }])),
)
</script>

<template>
  <section class="grid min-w-0 gap-6" :aria-label="t('dashboard.title')">
    <header class="flex flex-wrap items-start justify-between gap-4 border-b pb-6">
      <div class="max-w-xl">
        <p
          class="mb-2 flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-success"
        >
          <IconChartBar :size="16" />ShopFlow / {{ t('dashboard.owner') }}
        </p>
        <h1 class="text-3xl font-semibold tracking-tight text-primary">
          {{ t('dashboard.title') }}
        </h1>
        <p class="mt-2 text-sm leading-6 text-muted-foreground">{{ t('dashboard.subtitle') }}</p>
      </div>
      <RouterLink to="/shop-owner" :class="buttonVariants({ variant: 'outline' })"
        >{{ t('dashboard.deliveryLink') }}<IconArrowRight :size="16"
      /></RouterLink>
    </header>

    <form class="grid gap-4 rounded-xl border bg-card p-4 sm:p-5" @submit.prevent="apply">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <p class="text-sm font-semibold text-primary">{{ t('dashboard.period') }}</p>
        <div class="flex gap-2">
          <Button
            v-for="days in [7, 30, 90]"
            :key="days"
            type="button"
            size="sm"
            variant="outline"
            @click="preset(days)"
            >{{ t('dashboard.lastDays', { days }) }}</Button
          >
        </div>
      </div>
      <div class="grid items-end gap-3 sm:grid-cols-2 xl:grid-cols-[1fr_1fr_1fr_auto]">
        <label class="grid min-w-0 gap-1.5 text-sm font-medium"
          >{{ t('dashboard.from')
          }}<Input
            v-model="from"
            type="date"
            required
            :aria-invalid="invalid"
            aria-describedby="dashboard-range-error"
        /></label>
        <label class="grid min-w-0 gap-1.5 text-sm font-medium"
          >{{ t('dashboard.to')
          }}<Input
            v-model="to"
            type="date"
            required
            :aria-invalid="invalid"
            aria-describedby="dashboard-range-error"
        /></label>
        <label class="grid gap-1.5 text-sm font-medium"
          >{{ t('dashboard.group')
          }}<select
            v-model="groupBy"
            :aria-label="t('dashboard.group')"
            class="h-10 rounded-md border bg-background px-3 focus-visible:outline-2"
          >
            <option value="day">{{ t('dashboard.day') }}</option>
            <option value="month">{{ t('dashboard.month') }}</option>
          </select></label
        >
        <Button type="submit">{{ t('dashboard.apply') }}</Button>
      </div>
      <p class="text-xs text-muted-foreground">{{ t('dashboard.zone') }}</p>
      <p v-if="invalid" id="dashboard-range-error" role="alert" class="text-sm text-destructive">
        {{ t('dashboard.invalid') }}
      </p>
    </form>

    <p
      v-if="dashboard.isPending.value"
      role="status"
      class="rounded-xl border bg-card p-10 text-center text-muted-foreground"
    >
      {{ t('dashboard.loading') }}
    </p>
    <div
      v-else-if="dashboard.isError.value"
      role="alert"
      class="grid justify-items-center gap-3 rounded-xl border border-destructive/30 bg-card p-8"
    >
      <p>{{ t('dashboard.error') }}</p>
      <Button variant="outline" @click="dashboard.refetch()"
        ><IconRefresh :size="16" />{{ t('dashboard.retry') }}</Button
      >
    </div>
    <template v-else-if="data">
      <p class="text-xs text-muted-foreground" data-testid="dashboard-applied-period">
        {{ data.from }} — {{ data.to }}
      </p>
      <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4" data-testid="dashboard-cards">
        <div
          v-for="(card, index) in cards"
          :key="card.label"
          :class="[
            'min-w-0 rounded-xl border p-5',
            index === 0 ? 'border-primary bg-primary text-white' : 'bg-card',
          ]"
        >
          <p
            :class="[
              'text-xs font-medium',
              index === 0 ? 'text-white/80' : 'text-muted-foreground',
            ]"
          >
            {{ card.label }}
          </p>
          <p class="my-3 break-words text-2xl font-semibold tabular-nums tracking-tight">
            {{ card.value }}
          </p>
          <p :class="['text-xs', index === 0 ? 'text-white/70' : 'text-muted-foreground']">
            {{ card.scope }}
          </p>
        </div>
      </div>
      <p
        v-if="data.orderCount === 0 && data.paidAmount === '0'"
        role="status"
        class="rounded-xl border border-dashed p-6 text-center text-muted-foreground"
      >
        {{ t('dashboard.empty') }}
      </p>
      <div class="grid min-w-0 gap-4 xl:grid-cols-2">
        <section
          v-for="metric in ['paidAmount', 'orderCount'] as const"
          :key="metric"
          class="min-w-0 rounded-xl border bg-card p-4 sm:p-5"
        >
          <div class="mb-5 flex items-start justify-between gap-4">
            <h2 class="font-semibold text-primary">
              {{ metric === 'paidAmount' ? t('dashboard.paidTrend') : t('dashboard.ordersTrend') }}
            </h2>
            <span class="shrink-0 text-xs text-muted-foreground">{{
              t(`dashboard.${applied.groupBy}`)
            }}</span>
          </div>
          <DashboardTrend
            :points="data.series"
            :locale="locale"
            :metric="metric"
            :label="metric === 'paidAmount' ? t('dashboard.paid') : t('dashboard.orders')"
            :format-date="date"
            :format-value="metric === 'paidAmount' ? money : count"
          />
        </section>
      </div>
      <p class="text-xs leading-5 text-muted-foreground">{{ t('dashboard.grossNote') }}</p>
      <div class="grid min-w-0 gap-4 xl:grid-cols-[1fr_1.5fr]">
        <section class="min-w-0 rounded-xl border bg-card p-5">
          <h2 class="font-semibold text-primary">{{ t('dashboard.distribution') }}</h2>
          <ChartContainer
            v-if="data.orderCount"
            :config="statusConfig"
            class="mx-auto h-48 max-w-56"
            aria-hidden="true"
          >
            <VisSingleContainer :data="statuses" :duration="0"
              ><VisDonut
                :value="(s: { count: number }) => s.count"
                :color="(s: { color: string }) => s.color"
                :arc-width="22"
            /></VisSingleContainer>
          </ChartContainer>
          <ul class="mt-4 grid gap-3" :aria-label="t('dashboard.distribution')">
            <li
              v-for="status in statuses"
              :key="status.status"
              class="flex justify-between gap-3 text-sm"
            >
              <span class="flex items-center gap-2"
                ><span class="size-2 rounded-full" :style="{ backgroundColor: status.color }" />{{
                  status.label
                }}</span
              ><strong class="tabular-nums">{{ count(status.count) }}</strong>
            </li>
          </ul>
        </section>
        <section class="min-w-0 rounded-xl border bg-card p-5">
          <h2 class="font-semibold text-primary">{{ t('dashboard.top') }}</h2>
          <p class="mt-1 text-xs text-muted-foreground">{{ t('dashboard.topHint') }}</p>
          <ol class="mt-5 divide-y">
            <li
              v-for="(product, index) in data.topProducts"
              :key="product.productId"
              class="flex min-w-0 items-start gap-3 py-4"
            >
              <span
                class="grid size-7 shrink-0 place-items-center rounded-md bg-accent text-xs font-bold text-success"
                >{{ index + 1 }}</span
              >
              <div class="min-w-0 flex-1">
                <p class="break-words text-sm font-medium leading-6">{{ product.productName }}</p>
                <p class="mt-1 text-xs text-muted-foreground">
                  {{ t('dashboard.units', { count: count(product.quantity) }) }}
                </p>
              </div>
              <strong class="shrink-0 text-sm tabular-nums">{{ money(product.paidAmount) }}</strong>
            </li>
          </ol>
          <p
            v-if="!data.topProducts.length"
            class="py-10 text-center text-sm text-muted-foreground"
          >
            {{ t('dashboard.noProducts') }}
          </p>
        </section>
      </div>
      <details class="min-w-0 rounded-xl border bg-card p-5">
        <summary class="cursor-pointer text-sm font-semibold text-primary">
          {{ t('dashboard.table') }}
        </summary>
        <div class="mt-4 max-h-96 overflow-auto">
          <table class="w-full text-left text-sm">
            <caption class="sr-only">
              {{
                t('dashboard.table')
              }}
            </caption>
            <thead>
              <tr class="border-b">
                <th scope="col" class="p-2">{{ t('dashboard.date') }}</th>
                <th scope="col" class="p-2 text-right">{{ t('dashboard.paid') }}</th>
                <th scope="col" class="p-2 text-right">{{ t('dashboard.orders') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="point in data.series" :key="point.date" class="border-b last:border-0">
                <th scope="row" class="p-2 font-normal">{{ date(point.date) }}</th>
                <td class="p-2 text-right tabular-nums">{{ money(point.paidAmount) }}</td>
                <td class="p-2 text-right tabular-nums">{{ count(point.orderCount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </template>
  </section>
</template>
