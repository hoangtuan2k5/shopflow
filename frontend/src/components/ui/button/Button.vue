<script setup lang="ts">
import { Primitive, type PrimitiveProps } from 'reka-ui'
import { type HTMLAttributes, computed } from 'vue'
import { type ButtonVariants, buttonVariants } from '.'
import { cn } from '@/lib/utils'

interface Props extends PrimitiveProps {
  variant?: ButtonVariants['variant']
  size?: ButtonVariants['size']
  class?: HTMLAttributes['class']
}

const props = withDefaults(defineProps<Props>(), {
  as: 'button',
})

const delegatedProps = computed(() => {
  const delegated = { ...props }
  delete delegated.class
  delete delegated.variant
  delete delegated.size
  return delegated
})
</script>

<template>
  <Primitive
    data-slot="button"
    v-bind="delegatedProps"
    :class="cn(buttonVariants({ variant, size }), props.class)"
  >
    <slot />
  </Primitive>
</template>
