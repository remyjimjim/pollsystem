<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({ email: '', passcode: '' })
const error = ref<string | null>(null)
const submitting = ref(false)

async function onSubmit() {
  error.value = null
  submitting.value = true
  try {
    await auth.login({ email: form.email, passcode: form.passcode })
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Invalid email or password'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="view">
    <h1>Login</h1>
    <form @submit.prevent="onSubmit" class="form">
      <label>
        Email
        <input v-model="form.email" type="email" required autocomplete="email" />
      </label>
      <label>
        Password
        <input v-model="form.passcode" type="password" required autocomplete="current-password" />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="submitting">
        {{ submitting ? 'Signing in…' : 'Sign in' }}
      </button>
      <p class="alt">No account? <router-link to="/register">Register</router-link></p>
    </form>
  </div>
</template>

<style scoped>
.view {
  padding: 2rem 0;
  max-width: 360px;
  margin: 0 auto;
}
h1 {
  margin-bottom: 1rem;
  color: #1a365d;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
label {
  display: flex;
  flex-direction: column;
  font-size: 0.9rem;
  gap: 0.25rem;
}
input {
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font-size: 1rem;
}
button {
  padding: 0.6rem;
  background: #1a365d;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.error {
  color: #c53030;
  font-size: 0.9rem;
  margin: 0;
}
.alt {
  font-size: 0.9rem;
  text-align: center;
  margin: 0;
}
</style>
