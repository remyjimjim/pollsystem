<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

// Prefill from anything already on the user (usually empty for a payment-first
// account; non-empty if they land here again after a partial edit).
const form = reactive({
  phone: auth.user?.phone ?? '',
  zipcode: auth.user?.zipcode ?? ''
})
const error = ref<string | null>(null)
const submitting = ref(false)

async function onSubmit() {
  error.value = null
  submitting.value = true
  try {
    await auth.completeProfile({ phone: form.phone, zipcode: form.zipcode })
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('completeProfile.errorGeneric')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-sm py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('completeProfile.heading') }}</h1>

    <form @submit.prevent="onSubmit" class="flex flex-col gap-3">
      <p class="mb-1 text-sm text-slate-600">
        {{ $t('completeProfile.intro') }}
      </p>
      <label class="flex flex-col gap-1 text-sm text-slate-700">
        {{ $t('completeProfile.phoneLabel') }}
        <input
          v-model="form.phone"
          type="tel"
          required
          autocomplete="tel"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-sm text-slate-700">
        {{ $t('completeProfile.zipcodeLabel') }}
        <input
          v-model="form.zipcode"
          type="text"
          required
          pattern="[0-9]{5}"
          maxlength="5"
          inputmode="numeric"
          autocomplete="postal-code"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
      </label>
      <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
      <button
        type="submit"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-base text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? $t('completeProfile.submittingButton') : $t('completeProfile.submitButton') }}
      </button>
    </form>
  </div>
</template>
