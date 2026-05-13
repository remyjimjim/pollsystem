<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const status = ref<'pending' | 'error'>('pending')
const error = ref<string | null>(null)

onMounted(async () => {
  const token = route.query.token
  if (typeof token !== 'string' || !token) {
    status.value = 'error'
    error.value = t('magicLink.errorMalformed')
    return
  }
  try {
    await auth.redeemMagicLink(token)
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch (e: any) {
    status.value = 'error'
    error.value =
      e?.response?.status === 401
        ? t('magicLink.errorExpired')
        : e?.response?.data?.message ?? t('magicLink.errorGeneric')
  }
})
</script>

<template>
  <div class="mx-auto max-w-md py-8">
    <p v-if="status === 'pending'" class="text-center text-base text-slate-600">
      {{ $t('magicLink.signingIn') }}
    </p>
    <div
      v-else
      class="rounded border border-red-200 bg-red-50 p-4 text-red-900"
    >
      <h1 class="mb-2 text-xl font-semibold">{{ $t('magicLink.failedHeading') }}</h1>
      <p class="mb-2">{{ error }}</p>
      <p>
        <router-link to="/login" class="font-semibold text-red-900 underline">
          {{ $t('magicLink.requestNew') }}
        </router-link>
      </p>
    </div>
  </div>
</template>
