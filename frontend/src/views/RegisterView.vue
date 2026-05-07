<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const form = reactive({
  email: '',
  phone: '',
  zipcode: '',
  passcode: '',
  confirm: ''
})
const error = ref<string | null>(null)
const submitting = ref(false)

async function onSubmit() {
  error.value = null
  if (form.passcode !== form.confirm) {
    error.value = 'Passwords do not match'
    return
  }
  submitting.value = true
  try {
    await auth.register({
      email: form.email,
      phone: form.phone,
      zipcode: form.zipcode,
      passcode: form.passcode
    })
    router.push('/')
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Registration failed'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="view">
    <h1>Register</h1>
    <form @submit.prevent="onSubmit" class="form">
      <label>
        Email
        <input v-model="form.email" type="email" required autocomplete="email" />
      </label>
      <label>
        Phone
        <input v-model="form.phone" type="tel" required autocomplete="tel" />
      </label>
      <label>
        Zipcode
        <input
          v-model="form.zipcode"
          type="text"
          required
          pattern="[0-9]{5}"
          maxlength="5"
          inputmode="numeric"
          autocomplete="postal-code"
        />
      </label>
      <label>
        Password
        <input
          v-model="form.passcode"
          type="password"
          required
          minlength="8"
          autocomplete="new-password"
        />
      </label>
      <label>
        Confirm password
        <input
          v-model="form.confirm"
          type="password"
          required
          minlength="8"
          autocomplete="new-password"
        />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="submitting">
        {{ submitting ? 'Creating account…' : 'Create account' }}
      </button>
      <p class="alt">Already have an account? <router-link to="/login">Sign in</router-link></p>
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
