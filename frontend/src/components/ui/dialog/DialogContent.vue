<script setup lang="ts">
import {
  DialogContent,
  DialogOverlay,
  DialogPortal,
  type DialogContentProps,
  useForwardPropsEmits,
} from 'reka-ui'
import { type HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<
  DialogContentProps & {
    class?: HTMLAttributes['class']
  }
>()

const emits = defineEmits<{
  closeAutoFocus: [event: Event]
  escapeKeyDown: [event: KeyboardEvent]
  pointerDownOutside: [event: CustomEvent]
  interactOutside: [event: CustomEvent]
  openAutoFocus: [event: Event]
}>()

const forwarded = useForwardPropsEmits(props, emits)
</script>

<template>
  <DialogPortal>
    <DialogOverlay
      data-slot="dialog-overlay"
      class="fixed inset-0 z-50 bg-foreground/45 backdrop-blur-sm"
    />
    <DialogContent
      data-slot="dialog-content"
      v-bind="forwarded"
      :class="
        cn(
          'fixed left-1/2 top-1/2 z-50 grid w-[min(calc(100%-2rem),28rem)] -translate-x-1/2 -translate-y-1/2 gap-4 rounded-lg border border-border bg-popover p-6 text-popover-foreground shadow-lg outline-none',
          props.class,
        )
      "
    >
      <slot />
    </DialogContent>
  </DialogPortal>
</template>
