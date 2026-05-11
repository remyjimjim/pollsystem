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
    // Land authenticated users wherever they were headed, or the home page.
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
  <div class="view">
    <div v-if="status === 'pending'" class="pending">
      <p>Signing you in…</p>
    </div>
    <div v-else class="error-box">
      <h1>Sign-in failed</h1>
      <p>{{ error }}</p>
      <p>
        <router-link to="/login">Request a new sign-in link</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.view {
  padding: 2rem 0;
  max-width: 480px;
  margin: 0 auto;
}
.pending {
  text-align: center;
  color: #4a5568;
  font-size: 1rem;
}
.error-box {
  padding: 1rem;
  border: 1px solid #feb2b2;
  background: #fff5f5;
  border-radius: 4px;
  color: #742a2a;
}
.error-box h1 {
  font-size: 1.25rem;
  margin: 0 0 0.5rem;
}
.error-box p {
  margin: 0 0 0.5rem;
}
</style>
