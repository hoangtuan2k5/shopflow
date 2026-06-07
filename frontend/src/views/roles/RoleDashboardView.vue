<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute } from 'vue-router'
import { apiBaseUrl, getOpenApiDocument } from '@/api'
import RoleValidationForm from '@/components/forms/RoleValidationForm.vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useAppShellStore } from '@/stores/appShell'

const route = useRoute()
const appShell = useAppShellStore()

const pageTitle = computed(() => String(route.meta.title ?? appShell.activeRole?.label))
const pageDescription = computed(() =>
  String(route.meta.description ?? 'Role workflow placeholder.'),
)
const openApiQuery = useQuery({
  queryKey: ['openapi-document'],
  queryFn: getOpenApiDocument,
  retry: 1,
})

const apiClientStatus = computed(() => {
  if (openApiQuery.isLoading.value) {
    return 'Checking backend'
  }

  if (openApiQuery.isError.value) {
    return 'Backend unavailable'
  }

  return openApiQuery.data.value?.info.title ?? 'Connected'
})

const apiSpecVersion = computed(() => openApiQuery.data.value?.info.version ?? 'Not loaded')
</script>

<template>
  <section class="grid gap-6">
    <div class="space-y-2">
      <p class="text-sm font-medium text-primary">Sprint 3 placeholder</p>
      <h2 class="text-3xl font-semibold tracking-normal">{{ pageTitle }}</h2>
      <p class="max-w-2xl text-muted-foreground">{{ pageDescription }}</p>
    </div>

    <div class="grid gap-4 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>Layout skeleton</CardTitle>
          <CardDescription>
            Header, sidebar and content shell are ready for role-based pages.
          </CardDescription>
        </CardHeader>
        <CardContent class="grid gap-3 text-sm">
          <div class="flex items-center justify-between gap-4 rounded-md bg-muted px-3 py-2">
            <span class="text-muted-foreground">Active role</span>
            <span class="font-medium">{{ appShell.activeRole?.label }}</span>
          </div>
          <div class="flex items-center justify-between gap-4 rounded-md bg-muted px-3 py-2">
            <span class="text-muted-foreground">API base URL</span>
            <span class="font-medium">{{ apiBaseUrl }}</span>
          </div>
          <div class="flex items-center justify-between gap-4 rounded-md bg-muted px-3 py-2">
            <span class="text-muted-foreground">API client</span>
            <span class="font-medium">{{ apiClientStatus }}</span>
          </div>
          <div class="flex items-center justify-between gap-4 rounded-md bg-muted px-3 py-2">
            <span class="text-muted-foreground">OpenAPI version</span>
            <span class="font-medium">{{ apiSpecVersion }}</span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Form validation</CardTitle>
          <CardDescription>VeeValidate and Zod are wired for basic form rules.</CardDescription>
        </CardHeader>
        <CardContent>
          <RoleValidationForm />
        </CardContent>
      </Card>
    </div>
  </section>
</template>
