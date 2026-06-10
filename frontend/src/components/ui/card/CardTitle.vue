<script setup lang="ts">
import { type HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <h3
    data-slot="card-title"
    :class="cn('text-lg font-semibold leading-none tracking-normal', $props.class)"
  >
    <slot />
  </h3>
</template>
