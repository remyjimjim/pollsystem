<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const status = ref<'pending' | 'error'>('pending')
const error = ref<string | null>(null)

onMounted(async () => {
  const token = route.query.token
  if (typeof token !== 'string' || !token) {
    status.value = 'error'
    error.value = 'This sign-in link is malformed.'
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
        ? 'This sign-in link has expired or has already been used. Request a new one.'
        : e?.response?.data?.message ?? 'Could not complete sign-in.'
  }
})
</script>

<template>
  <div class="mx-auto max-w-md py-8">
    <p v-if="status === 'pending'" class="text-center text-base text-slate-600">
      Signing you in…
    </p>
    <div
      v-else
      class="rounded border border-red-200 bg-red-50 p-4 text-red-900"
    >
      <h1 class="mb-2 text-xl font-semibold">Sign-in failed</h1>
      <p class="mb-2">{{ error }}</p>
      <p>
        <router-link to="/login" class="font-semibold text-red-900 underline">
          Request a new sign-in link
        </router-link>
      </p>
    </div>
  </div>
</template>
