<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useBilling } from '@/composables/useBilling'

const auth = useAuthStore()
const route = useRoute()
const { busy, error, startCheckout, openPortal } = useBilling()

// A live subscription (from the store): paidUntil set and still in the future.
const isPaid = computed(() => auth.isPaid)

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
      <!-- No free accounts: a logged-in unpaid user is a lapsed member, so this
           is a renewal prompt (not a "become a member" upsell). -->
      <h2 class="text-lg font-semibold text-slate-800">{{ $t('billing.renewTitle') }}</h2>
      <p class="mt-1 mb-3 text-sm text-slate-600">{{ $t('billing.renewBlurb') }}</p>
      <button
        type="button"
        :disabled="busy"
        class="rounded bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-500 disabled:opacity-60"
        @click="startCheckout"
      >
        {{ $t('billing.renewCta') }}
      </button>
    </template>

    <p v-if="error" class="mt-2 text-sm text-red-700">{{ $t('billing.unavailable') }}</p>
  </div>
</template>
