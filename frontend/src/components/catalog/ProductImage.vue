<script setup lang="ts">
import { ref, watch } from 'vue'
import { IconPackage } from '@tabler/icons-vue'

const props = defineProps<{ src: string | null; name: string; eager?: boolean }>()
const failed = ref(false)
watch(
  () => props.src,
  () => {
    failed.value = false
  },
)
</script>

<template>
  <img
    v-if="src && !failed"
    :src="src"
    :alt="name"
    :loading="eager ? 'eager' : 'lazy'"
    decoding="async"
    class="absolute inset-0 h-full w-full object-contain"
    @error="failed = true"
  />
  <slot v-else><IconPackage :size="32" :stroke-width="1.5" aria-hidden="true" /></slot>
</template>
