<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

const formSchema = toTypedSchema(
  z.object({
    referenceCode: z.string().min(3, 'Use at least 3 characters.'),
  }),
)

const { defineField, errors, handleSubmit, resetForm } = useForm({
  validationSchema: formSchema,
  initialValues: {
    referenceCode: '',
  },
})

const [referenceCode, referenceCodeAttrs] = defineField('referenceCode')

const onSubmit = handleSubmit(() => {
  resetForm()
})
</script>

<template>
  <form class="grid gap-3 sm:grid-cols-[1fr_auto]" @submit="onSubmit">
    <div class="grid gap-1">
      <Input
        v-model="referenceCode"
        v-bind="referenceCodeAttrs"
        aria-label="Workflow reference code"
        placeholder="Workflow reference code"
      />
      <p v-if="errors.referenceCode" class="text-sm text-destructive">
        {{ errors.referenceCode }}
      </p>
    </div>
    <Button type="submit">Validate</Button>
  </form>
</template>
