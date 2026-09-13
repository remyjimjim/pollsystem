<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()

const form = reactive({
  email: '',
  phone: '',
  zipcode: ''
})
const error = ref<string | null>(null)
const emailTaken = ref(false)
const submitting = ref(false)

async function onSubmit() {
  error.value = null
  emailTaken.value = false
  submitting.value = true
  try {
    // Pay-first: validate + start Stripe Checkout, then hand off to Stripe.
    // The account is created by the checkout webhook once payment succeeds,
    // which emails a one-time sign-in link. The page navigates away here.
    const url = await auth.registerCheckout({
      email: form.email,
      phone: form.phone,
      zipcode: form.zipcode
    })
    window.location.href = url
  } catch (e: any) {
    const status = e?.response?.status
    const msg: string | undefined = e?.response?.data?.message
    if (status === 409 && typeof msg === 'string' && msg.toLowerCase().includes('email')) {
      emailTaken.value = true
      error.value = t('register.errorEmailTaken')
    } else if (status === 409) {
      error.value = t('register.errorPhoneTaken')
    } else if (status === 400) {
      error.value = msg ?? t('register.errorInvalid')
    } else {
      error.value = t('register.errorGeneric')
    }
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-sm py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('register.heading') }}</h1>

    <form @submit.prevent="onSubmit" class="flex flex-col gap-3">
      <p class="mb-1 text-sm text-slate-600">
        {{ $t('register.intro') }}
      </p>
      <label class="flex flex-col gap-1 text-sm text-slate-700">
        {{ $t('register.emailLabel') }}
        <input
          v-model="form.email"
          type="email"
          required
          autocomplete="email"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-sm text-slate-700">
        {{ $t('register.phoneLabel') }}
        <input
          v-model="form.phone"
          type="tel"
          required
          autocomplete="tel"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-sm text-slate-700">
        {{ $t('register.zipcodeLabel') }}
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
      <p v-if="error" class="text-sm text-red-700">
        {{ error }}
        <router-link v-if="emailTaken" to="/login" class="ml-1 text-slate-800 underline">
          {{ $t('register.signInLink') }}
        </router-link>
      </p>
      <button
        type="submit"
        :disabled="submitting"
        class="rounded bg-emerald-600 px-4 py-2 text-base font-semibold text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? $t('register.submittingButton') : $t('register.submitButton') }}
      </button>
      <p class="text-center text-sm text-slate-600">
        {{ $t('register.haveAccount') }}
        <router-link to="/login" class="text-slate-800 underline">{{ $t('register.signInLink') }}</router-link>
      </p>
    </form>
  </div>
</template>
