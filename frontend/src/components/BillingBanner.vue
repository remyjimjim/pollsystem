<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useBilling } from '@/composables/useBilling'

const auth = useAuthStore()
const route = useRoute()
const { busy, error, startCheckout, openPortal } = useBilling()

// A live subscription: paidUntil is set and still in the future.
const isPaid = computed(() => {
  const until = auth.user?.paidUntil
  return !!until && new Date(until).getTime() > Date.now()
})

// Stripe redirects back to "/" with ?checkout=success|cancel.
const checkoutStatus = computed(() => {
  const c = route.query.checkout
  return typeof c === 'string' ? c : undefined
})
</script>

<template>
  <div
    v-if="auth.isAuthenticated"
    class="mb-6 rounded-md border border-slate-200 bg-white p-5"
  >
    <p
      v-if="checkoutStatus === 'success'"
      class="mb-3 rounded bg-emerald-50 px-3 py-2 text-sm text-emerald-800"
    >
      {{ $t('billing.success') }}
    </p>
    <p
      v-else-if="checkoutStatus === 'cancel'"
      class="mb-3 rounded bg-amber-50 px-3 py-2 text-sm text-amber-800"
    >
      {{ $t('billing.canceled') }}
    </p>

    <template v-if="isPaid">
      <h2 class="text-lg font-semibold text-slate-800">{{ $t('billing.manageTitle') }}</h2>
      <p class="mt-1 mb-3 text-sm text-slate-600">{{ $t('billing.manageBlurb') }}</p>
      <button
        type="button"
        :disabled="busy"
        class="rounded bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-60"
        @click="openPortal"
      >
        {{ $t('billing.manageCta') }}
      </button>
    </template>
    <template v-else>
      <h2 class="text-lg font-semibold text-slate-800">{{ $t('billing.subscribeTitle') }}</h2>
      <p class="mt-1 mb-3 text-sm text-slate-600">{{ $t('billing.subscribeBlurb') }}</p>
      <button
        type="button"
        :disabled="busy"
        class="rounded bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-500 disabled:opacity-60"
        @click="startCheckout"
      >
        {{ $t('billing.subscribeCta') }}
      </button>
    </template>

    <p v-if="error" class="mt-2 text-sm text-red-700">{{ $t('billing.unavailable') }}</p>
  </div>
</template>
