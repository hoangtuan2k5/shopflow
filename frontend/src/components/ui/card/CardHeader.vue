<script setup lang="ts">
import { type HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <div data-slot="card-header" :class="cn('grid gap-1.5 p-6', $props.class)">
    <slot />
  </div>
</template>
