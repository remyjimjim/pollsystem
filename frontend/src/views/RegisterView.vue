<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const form = reactive({
  email: '',
  phone: '',
  zipcode: ''
})
const error = ref<string | null>(null)
const submitting = ref(false)
const sentTo = ref<string | null>(null)

async function onSubmit() {
  error.value = null
  submitting.value = true
  try {
    await auth.requestMagicLink({
      email: form.email,
      phone: form.phone,
      zipcode: form.zipcode
    })
    sentTo.value = form.email
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Could not send the sign-in link'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="view">
    <h1>Create an account</h1>

    <div v-if="sentTo" class="confirm">
      <p><strong>Check your email.</strong></p>
      <p>
        We've sent a one-time sign-in link to <strong>{{ sentTo }}</strong>.
        Click the link to finish creating your account. The link expires in 15 minutes.
      </p>
      <p class="alt">
        Wrong address? <a href="#" @click.prevent="sentTo = null">Try again</a>
      </p>
    </div>

    <form v-else @submit.prevent="onSubmit" class="form">
      <p class="hint">
        We use one-time email links instead of passwords. Enter your details
        and we'll send a sign-in link to your inbox.
      </p>
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
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="submitting">
        {{ submitting ? 'Sending link…' : 'Email me a sign-in link' }}
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
.hint {
  font-size: 0.9rem;
  color: #4a5568;
  margin: 0 0 0.5rem;
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
.confirm {
  padding: 1rem;
  border: 1px solid #c6f6d5;
  background: #f0fff4;
  border-radius: 4px;
  font-size: 0.95rem;
  color: #22543d;
}
.confirm p {
  margin: 0 0 0.5rem;
}
.confirm p:last-child {
  margin: 0.75rem 0 0;
}
</style>
