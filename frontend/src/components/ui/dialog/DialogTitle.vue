<script setup lang="ts">
import { DialogTitle, type DialogTitleProps } from 'reka-ui'
import { type HTMLAttributes, computed } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<
  DialogTitleProps & {
    class?: HTMLAttributes['class']
  }
>()

const delegatedProps = computed(() => {
  const delegated = { ...props }
  delete delegated.class
  return delegated
})
</script>

<template>
  <DialogTitle
    data-slot="dialog-title"
    v-bind="delegatedProps"
    :class="cn('text-lg font-semibold leading-none tracking-normal', props.class)"
  >
    <slot />
  </DialogTitle>
</template>
