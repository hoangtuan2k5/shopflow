<script setup lang="ts">
import { computed, ref, reactive, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useSessionStore } from '@/stores/session'
import {
  getDeliveryProfile,
  saveDeliveryProfile,
  deleteDeliveryProfile,
  type DeliveryDetails,
} from '@/api/account'
const { t } = useI18n()
const session = useSessionStore()
const cache = useQueryClient()
const key = computed(() => ['account-delivery', session.user?.userId])
const profile = useQuery({ queryKey: key, queryFn: getDeliveryProfile, retry: false })
const empty = (): Omit<DeliveryDetails, 'district'> & { district: string } => ({
  receiverName: '',
  phone: '',
  addressLine: '',
  district: '',
  city: '',
})
const form = reactive(empty())
const dirty = ref(false)
const busy = ref(false)
const error = ref(false)
const saved = ref(false)
const fields = [
  {
    name: 'receiverName',
    label: 'checkout.fields.receiverName',
    max: 255,
    autocomplete: 'shipping name',
  },
  { name: 'phone', label: 'checkout.fields.receiverPhone', max: 20, autocomplete: 'shipping tel' },
  {
    name: 'addressLine',
    label: 'checkout.fields.address',
    max: 500,
    autocomplete: 'shipping street-address',
  },
  {
    name: 'district',
    label: 'checkout.fields.district',
    max: 100,
    autocomplete: 'shipping address-level2',
  },
  {
    name: 'city',
    label: 'checkout.fields.city',
    max: 100,
    autocomplete: 'shipping address-level1',
  },
] as const
watch(
  profile.data,
  (value) => {
    if (value && !dirty.value)
      Object.assign(form, value.delivery ?? empty(), { district: value.delivery?.district ?? '' })
  },
  { immediate: true },
)
function markEdited() {
  dirty.value = true
  saved.value = false
}
async function save(remove = false) {
  const userId = session.user?.userId
  busy.value = true
  error.value = false
  saved.value = false
  try {
    const result = remove
      ? (await deleteDeliveryProfile(), { delivery: null })
      : await saveDeliveryProfile({ ...form })
    if (session.user?.userId !== userId) return
    cache.setQueryData(key.value, result)
    Object.assign(form, result.delivery ?? empty(), { district: result.delivery?.district ?? '' })
    dirty.value = false
    saved.value = true
  } catch {
    error.value = true
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="mt-6 border-t pt-6">
    <h2 class="text-xl font-semibold">{{ t('account.delivery') }}</h2>
    <p v-if="profile.isPending.value" role="status" class="mt-4">{{ t('account.loading') }}</p>
    <div v-else-if="profile.isError.value" class="mt-4">
      <p role="alert">{{ t('account.loadError') }}</p>
      <Button variant="outline" class="mt-2" @click="profile.refetch()">{{
        t('account.retry')
      }}</Button>
    </div>
    <form v-else class="mt-5 space-y-4" @submit.prevent="save()" @input="markEdited">
      <div v-for="field in fields" :key="field.name">
        <label :for="'delivery-' + field.name" class="text-sm font-medium">{{
          t(field.label)
        }}</label>
        <Input
          :id="'delivery-' + field.name"
          v-model="form[field.name]"
          class="mt-1"
          :maxlength="field.max"
          :required="field.name !== 'district'"
          :autocomplete="field.autocomplete"
          :type="field.name === 'phone' ? 'tel' : 'text'"
        />
      </div>
      <p v-if="error" role="alert" class="text-sm text-destructive">
        {{ t('account.deliveryError') }}
      </p>
      <p v-if="saved" role="status" class="text-sm text-success">
        {{ t('account.deliverySaved') }}
      </p>
      <div class="flex flex-wrap gap-3">
        <Button type="submit" :disabled="busy">{{
          t(busy ? 'account.loading' : 'account.saveDelivery')
        }}</Button>
        <Button
          v-if="profile.data.value?.delivery"
          type="button"
          variant="outline"
          :disabled="busy"
          @click="save(true)"
          >{{ t('account.deleteDelivery') }}</Button
        >
      </div>
    </form>
  </section>
</template>
