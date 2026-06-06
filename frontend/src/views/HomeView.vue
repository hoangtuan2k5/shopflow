<script setup lang="ts">
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
</script>

<template>
  <main class="min-h-screen bg-background px-6 py-10 text-foreground">
    <section class="mx-auto grid w-full max-w-3xl gap-6">
      <div class="space-y-2">
        <p class="text-sm font-medium text-primary">Shopflow Frontend</p>
        <h1 class="text-3xl font-semibold tracking-normal">Vue UI foundation</h1>
        <p class="max-w-2xl text-muted-foreground">
          Tailwind CSS and shadcn-vue components are wired for the frontend workspace.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Component smoke test</CardTitle>
          <CardDescription>
            Button, Input, Card and Dialog render with the shared theme tokens.
          </CardDescription>
        </CardHeader>
        <CardContent class="grid gap-4 sm:grid-cols-[1fr_auto]">
          <Input
            aria-label="Search orders, products, or customers"
            placeholder="Search orders, products, or customers"
          />
          <Button>Search</Button>
        </CardContent>
        <CardFooter>
          <Dialog>
            <DialogTrigger as-child>
              <Button variant="outline">Open dialog</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>shadcn-vue is ready</DialogTitle>
                <DialogDescription>
                  This dialog uses reka-ui primitives and Tailwind theme classes.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <DialogClose as-child>
                  <Button>Close</Button>
                </DialogClose>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </CardFooter>
      </Card>
    </section>
  </main>
</template>
