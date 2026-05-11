<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const form = reactive({ email: '' })
const error = ref<string | null>(null)
const submitting = ref(false)
const sentTo = ref<string | null>(null)

async function onSubmit() {
  error.value = null
  submitting.value = true
  try {
    await auth.requestMagicLink({ email: form.email })
    sentTo.value = form.email
  } catch (e: any) {
    const status = e?.response?.status
    if (status === 400) {
      error.value = "We don't have an account for that email. Please register first."
    } else {
      error.value = e?.response?.data?.message ?? 'Could not send the sign-in link'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-sm py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">Sign in</h1>

    <div
      v-if="sentTo"
      class="rounded border border-green-200 bg-green-50 p-4 text-sm text-green-900"
    >
      <p class="mb-2"><strong class="font-semibold">Check your email.</strong></p>
      <p class="mb-2">
        We've sent a one-time sign-in link to
        <strong class="font-semibold">{{ sentTo }}</strong>.
        The link expires in 15 minutes and can only be used once.
      </p>
      <p class="mt-3 text-center">
        Wrong address?
        <a href="#" @click.prevent="sentTo = null" class="text-slate-700 underline">Try again</a>
      </p>
    </div>

    <form v-else @submit.prevent="onSubmit" class="flex flex-col gap-3">
      <p class="mb-1 text-sm text-slate-600">
        Enter the email on your account and we'll send you a one-time sign-in link.
      </p>
      <label class="flex flex-col gap-1 text-sm text-slate-700">
        Email
        <input
          v-model="form.email"
          type="email"
          required
          autocomplete="email"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
      </label>
      <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
      <button
        type="submit"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-base text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? 'Sending link…' : 'Email me a sign-in link' }}
      </button>
      <p class="text-center text-sm text-slate-600">
        No account?
        <router-link to="/register" class="text-slate-800 underline">Register</router-link>
      </p>
    </form>
  </div>
</template>
