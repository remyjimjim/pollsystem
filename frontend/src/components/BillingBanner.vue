<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useBilling } from '@/composables/useBilling'
import { AccessLevel } from '@/types'

const auth = useAuthStore()
const route = useRoute()
const { busy, error, startCheckout, openPortal, cancelMembership, keepMembership } = useBilling()
const { locale } = useI18n()

// Cancelled: still a member until paidUntil, then lapsed (sign in, view, Renew).
const ending = computed(() => !!auth.user?.cancelAtPeriodEnd)
const endsOn = computed(() => {
  const until = auth.user?.paidUntil
  return until ? new Date(until).toLocaleDateString(locale.value, { dateStyle: 'long' }) : ''
})
const confirming = ref(false)
async function confirmCancel() {
  if (await cancelMembership()) confirming.value = false
}

// A live subscription (from the store): paidUntil set and still in the future.
const isPaid = computed(() => auth.isPaid)
// SUPER is exempt from the subscription (as in the backend's ParticipationGuard),
// so an unpaid super gets no renewal prompt.
const isExempt = computed(() => auth.hasAccess(AccessLevel.SUPER))

// Stripe redirects back to "/" with ?checkout=success|cancel.
const checkoutStatus = computed(() => {
  const c = route.query.checkout
  return typeof c === 'string' ? c : undefined
})
</script>

<template>
  <div
    v-if="auth.isAuthenticated && (isPaid || !isExempt || checkoutStatus)"
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

    <template v-if="isPaid && ending">
      <h2 class="text-lg font-semibold text-slate-800">{{ $t('billing.endingTitle') }}</h2>
      <p class="mt-1 mb-3 text-sm text-slate-600" data-test="billing-ending">{{ $t('billing.endingBlurb', { date: endsOn }) }}</p>
      <div class="flex flex-wrap gap-2">
        <button
          type="button"
          :disabled="busy"
          class="rounded bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-500 disabled:opacity-60"
          data-test="billing-keep"
          @click="keepMembership"
        >
          {{ $t('billing.keepCta') }}
        </button>
        <button
          type="button"
          :disabled="busy"
          class="rounded border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          @click="openPortal"
        >
          {{ $t('billing.manageCta') }}
        </button>
      </div>
    </template>
    <template v-else-if="isPaid">
      <h2 class="text-lg font-semibold text-slate-800">{{ $t('billing.manageTitle') }}</h2>
      <p class="mt-1 mb-3 text-sm text-slate-600">{{ $t('billing.manageBlurb') }}</p>
      <div v-if="!confirming" class="flex flex-wrap gap-2">
        <button
          type="button"
          :disabled="busy"
          class="rounded bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-60"
          @click="openPortal"
        >
          {{ $t('billing.manageCta') }}
        </button>
        <button
          type="button"
          :disabled="busy"
          class="rounded border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          data-test="billing-cancel"
          @click="confirming = true"
        >
          {{ $t('billing.cancelCta') }}
        </button>
      </div>
      <div v-else class="rounded border border-amber-300 bg-amber-50 p-3" data-test="billing-cancel-confirm">
        <p class="mb-2 text-sm text-amber-900">{{ $t('billing.cancelConfirm', { date: endsOn }) }}</p>
        <div class="flex flex-wrap gap-2">
          <button
            type="button"
            :disabled="busy"
            class="rounded bg-red-700 px-3 py-1.5 text-sm font-semibold text-white hover:bg-red-600 disabled:opacity-60"
            data-test="billing-cancel-yes"
            @click="confirmCancel"
          >
            {{ $t('billing.cancelYes') }}
          </button>
          <button
            type="button"
            :disabled="busy"
            class="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
            @click="confirming = false"
          >
            {{ $t('billing.cancelNo') }}
          </button>
        </div>
      </div>
    </template>
    <template v-else-if="!isExempt">
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
