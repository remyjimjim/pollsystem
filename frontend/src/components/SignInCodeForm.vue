<script setup lang="ts">
// Sign in with the 6-digit code from a sign-in email (decided 2026-10-27). On a
// phone the email app opens links in its own browser, which signs in there and
// not here; typing the code here signs in this browser. Shown on the sign-in
// page (the email is known) and after paying (asks for it).
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ email?: string | null }>()
const emit = defineEmits<{ (e: 'signed-in'): void }>()
const { t } = useI18n()
const auth = useAuthStore()

const typedEmail = ref('')
const code = ref('')
const error = ref<string | null>(null)
const submitting = ref(false)

async function onSubmit() {
  error.value = null
  submitting.value = true
  try {
    await auth.redeemCode((props.email ?? typedEmail.value).trim(), code.value)
    emit('signed-in')
  } catch (e: any) {
    error.value = e?.response?.status === 429 ? t('signInCode.tooMany') : t('signInCode.wrong')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-2" data-test="sign-in-code" @submit.prevent="onSubmit">
    <p class="m-0 text-sm font-semibold">{{ $t('signInCode.heading') }}</p>
    <label v-if="!props.email" class="flex flex-col gap-1 text-sm">
      {{ $t('login.emailLabel') }}
      <input
        v-model="typedEmail"
        type="email"
        required
        autocomplete="email"
        class="rounded border border-slate-300 bg-white p-2 text-base text-slate-900 focus:border-slate-500 focus:outline-none"
      />
    </label>
    <div class="flex gap-2">
      <input
        v-model="code"
        :aria-label="$t('signInCode.codeLabel')"
        inputmode="numeric"
        autocomplete="one-time-code"
        maxlength="7"
        required
        placeholder="123 456"
        data-test="sign-in-code-input"
        class="w-32 rounded border border-slate-300 bg-white p-2 text-center font-mono text-lg tracking-widest text-slate-900 focus:border-slate-500 focus:outline-none"
      />
      <button
        type="submit"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-base text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? $t('signInCode.submitting') : $t('signInCode.button') }}
      </button>
    </div>
    <p v-if="error" class="m-0 text-sm text-red-700" role="alert">{{ error }}</p>
  </form>
</template>
