<script setup lang="ts">
import { type HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const modelValue = defineModel<string | number>()

defineOptions({
  inheritAttrs: false,
})

defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <input
    v-model="modelValue"
    data-slot="input"
    v-bind="$attrs"
    :class="
      cn(
        'flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
        $props.class,
      )
    "
  />
</template>
