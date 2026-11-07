<script setup lang="ts">
import { ref, computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { IconUserCircle, IconChevronDown, IconLogout } from '@tabler/icons-vue'
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
} from 'reka-ui'
import { Button } from '@/components/ui/button'
import { useSessionStore } from '@/stores/session'
import { getAccountProfile } from '@/api/account'
const props = defineProps<{ class?: string }>()
const session = useSessionStore()
const { t } = useI18n()
const router = useRouter()
const open = ref(false)
const busy = ref(false)
const error = ref(false)
const account = useQuery({
  queryKey: computed(() => ['account', session.user?.userId]),
  queryFn: getAccountProfile,
  enabled: computed(() => open.value && session.isAuthenticated),
  retry: false,
})
const itemClass =
  'flex cursor-pointer items-center gap-2 rounded-md px-3 py-2.5 text-sm outline-none data-[highlighted]:bg-muted data-[disabled]:pointer-events-none data-[disabled]:opacity-50'
async function signOut() {
  busy.value = true
  error.value = false
  try {
    await session.signOut()
    open.value = false
    await router.replace('/login')
  } catch {
    error.value = true
    open.value = true
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <DropdownMenuRoot v-if="session.user" v-model:open="open">
    <DropdownMenuTrigger as-child>
      <Button
        variant="outline"
        :class="['h-11 gap-2 px-3 lg:h-12', props.class]"
        :title="session.user.displayName"
        :aria-label="session.user.displayName"
        :disabled="busy"
      >
        <IconUserCircle :size="20" aria-hidden="true" />
        <span class="max-w-[3rem] truncate min-[375px]:max-w-[4.5rem] sm:max-w-[12rem]">{{
          session.accountLabel
        }}</span>
        <IconChevronDown :size="14" class="hidden sm:block" aria-hidden="true" />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent
        align="end"
        :side-offset="8"
        :collision-padding="12"
        class="z-50 w-72 max-w-[calc(100vw-24px)] rounded-xl border border-border bg-card p-2 text-foreground shadow-xl"
      >
        <DropdownMenuLabel class="px-3 py-2">
          <p class="break-words font-semibold">{{ session.user.displayName }}</p>
          <p
            v-if="account.data.value?.email"
            class="break-all text-xs font-normal text-muted-foreground"
          >
            {{ account.data.value.email }}
          </p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator class="my-1 h-px bg-border" />
        <p v-if="account.isPending.value" role="status" class="px-3 py-2 text-sm">
          {{ t('account.loading') }}
        </p>
        <div v-else-if="account.isError.value" class="px-3 py-2 text-sm">
          <p role="alert">{{ t('account.loadError') }}</p>
          <button type="button" class="mt-1 underline" @click="account.refetch()">
            {{ t('account.retry') }}
          </button>
        </div>
        <template v-else-if="account.data.value">
          <DropdownMenuItem
            :class="itemClass"
            :disabled="!account.data.value.capabilities.manageDelivery"
            @select="router.push('/account/delivery')"
            >{{ t('account.delivery') }}</DropdownMenuItem
          >
          <DropdownMenuItem
            :class="itemClass"
            :disabled="
              !account.data.value.capabilities.changePassword &&
              !account.data.value.capabilities.setPassword
            "
            @select="router.push('/account/password')"
            >{{
              t(
                account.data.value.capabilities.setPassword
                  ? 'account.setupPassword'
                  : 'account.changePassword',
              )
            }}</DropdownMenuItem
          >
          <p
            v-if="!account.data.value.capabilities.manageDelivery"
            class="px-3 py-2 text-xs text-muted-foreground"
          >
            {{ t('account.demoHint') }}
          </p>
        </template>
        <DropdownMenuItem
          v-if="session.roleKey !== 'customer'"
          :class="itemClass"
          @select="router.push(session.homePath)"
          >{{ t('auth.workspace') }}</DropdownMenuItem
        >
        <DropdownMenuSeparator class="my-1 h-px bg-border" />
        <p v-if="error" role="alert" class="px-3 py-2 text-sm text-destructive">
          {{ t('account.logoutError') }}
        </p>
        <DropdownMenuItem :class="itemClass" :disabled="busy" @select.prevent="signOut"
          ><IconLogout :size="18" aria-hidden="true" />{{
            t(busy ? 'account.loading' : 'auth.signOut')
          }}</DropdownMenuItem
        >
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>
