<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

// Cross-tab sign-in: when the user clicks the magic link (which opens in a new
// tab and redeems there), that tab writes the JWT to localStorage, firing a
// `storage` event here. We adopt it and continue on THIS tab, so the user ends
// up signed in on the tab they started from rather than the pop-up tab.
async function onTokenFromOtherTab(e: StorageEvent) {
  if (e.key !== 'token' || !e.newValue || auth.isAuthenticated) return
  await auth.adoptToken(e.newValue)
  const redirect = (route.query.redirect as string) || '/'
  if (auth.user && !auth.user.profileComplete) {
    router.replace({ name: 'CompleteProfile', query: { redirect } })
  } else {
    router.replace(redirect)
  }
}
onMounted(() => window.addEventListener('storage', onTokenFromOtherTab))
onUnmounted(() => window.removeEventListener('storage', onTokenFromOtherTab))

const form = reactive({ email: '' })
const error = ref<string | null>(null)
const submitting = ref(false)
const sentTo = ref<string | null>(null)
// True when the account exists but its membership has lapsed — we still email a
// sign-in link, and note that they can renew once signed in.
const lapsed = ref(false)

async function onSubmit() {
  error.value = null
  lapsed.value = false
  submitting.value = true
  try {
    // Route by account status first: unknown emails have no account (pay-first
    // model), so send them to register rather than emailing a dead-end link.
    const status = await auth.accountStatus(form.email)
    if (status === 'UNKNOWN') {
      error.value = t('login.errorNoAccount')
      return
    }
    lapsed.value = status === 'LAPSED'
    await auth.requestMagicLink({ email: form.email })
    sentTo.value = form.email
  } catch (e: any) {
    const status = e?.response?.status
    if (status === 400 || status === 404) {
      error.value = t('login.errorNoAccount')
    } else {
      error.value = e?.response?.data?.message ?? t('login.errorGeneric')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-sm py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('login.heading') }}</h1>

    <div
      v-if="sentTo"
      class="rounded border border-green-200 bg-green-50 p-4 text-sm text-green-900"
    >
      <p class="mb-2"><strong class="font-semibold">{{ $t('login.checkEmailHeading') }}</strong></p>
      <i18n-t keypath="login.checkEmailBody" tag="p" class="mb-2">
        <template #email>
          <strong class="font-semibold">{{ sentTo }}</strong>
        </template>
      </i18n-t>
      <p v-if="lapsed" class="mb-2 rounded bg-amber-50 px-3 py-2 text-amber-800">
        {{ $t('login.lapsedNote') }}
      </p>
      <p class="mb-2 text-xs text-slate-500">{{ $t('login.autoContinue') }}</p>
      <p class="mt-3 text-center">
        {{ $t('login.wrongAddress') }}
        <a href="#" @click.prevent="sentTo = null" class="text-slate-700 underline">{{ $t('common.tryAgain') }}</a>
      </p>
    </div>

    <form v-else @submit.prevent="onSubmit" class="flex flex-col gap-3">
      <p class="mb-1 text-sm text-slate-600">
        {{ $t('login.intro') }}
      </p>
      <label class="flex flex-col gap-1 text-sm text-slate-700">
        {{ $t('login.emailLabel') }}
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
        {{ submitting ? $t('login.submittingButton') : $t('login.submitButton') }}
      </button>
      <p class="text-center text-sm text-slate-600">
        {{ $t('login.noAccount') }}
        <router-link to="/register" class="text-slate-800 underline">{{ $t('login.registerLink') }}</router-link>
      </p>
    </form>
  </div>
</template>
