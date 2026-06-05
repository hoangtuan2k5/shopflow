<script setup lang="ts">
import { DialogDescription, type DialogDescriptionProps } from 'reka-ui'
import { type HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<
  DialogDescriptionProps & {
    class?: HTMLAttributes['class']
  }
>()
</script>

<template>
  <DialogDescription
    data-slot="dialog-description"
    v-bind="props"
    :class="cn('text-sm text-muted-foreground', props.class)"
  >
    <slot />
  </DialogDescription>
</template>
