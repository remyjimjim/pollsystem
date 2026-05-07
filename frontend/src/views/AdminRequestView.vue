<script setup lang="ts">
import { ref } from 'vue'
import axios from 'axios'
import ZipSetter from '@/components/ZipSetter.vue'

const zipcodes = ref<string[]>([])
const reason = ref('')

const submitting = ref(false)
const submitted = ref(false)
const error = ref<string | null>(null)

async function onSubmit() {
  error.value = null
  if (zipcodes.value.length === 0) {
    error.value = 'Select at least one zipcode you want to administer'
    return
  }
  if (reason.value.trim().length === 0) {
    error.value = 'Reason is required'
    return
  }
  submitting.value = true
  try {
    await axios.post('/api/admin-requests', {
      zipcodes: zipcodes.value,
      reason: reason.value.trim()
    })
    submitted.value = true
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Submission failed'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="view">
    <h1>Request Admin Access</h1>

    <div v-if="submitted" class="success">
      <p>Your request has been submitted. A Super will review it and you'll receive an email once it's processed.</p>
    </div>

    <form v-else @submit.prevent="onSubmit" class="form">
      <p class="lead">
        Pick the zipcodes you want to administer Creator Requests for.
        A Super will review your request.
      </p>

      <fieldset>
        <legend>Zipcodes you want to administer</legend>
        <ZipSetter v-model="zipcodes" />
        <p v-if="zipcodes.length > 0" class="hint">
          Selected: {{ zipcodes.join(', ') }}
        </p>
      </fieldset>

      <fieldset>
        <legend>Reason</legend>
        <textarea
          v-model="reason"
          rows="5"
          maxlength="2000"
          placeholder="Why would you like to be an admin in this area?"
        />
      </fieldset>

      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="submitting">
        {{ submitting ? 'Submitting…' : 'Submit Request' }}
      </button>
    </form>
  </div>
</template>

<style scoped>
.view {
  padding: 2rem 0;
  max-width: 720px;
  margin: 0 auto;
}
h1 { margin-bottom: 1rem; color: #1a365d; }
.lead { color: #4a5568; margin-bottom: 1rem; }
.form { display: flex; flex-direction: column; gap: 1.25rem; }
fieldset {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1rem;
}
legend {
  padding: 0 0.5rem;
  font-weight: 600;
  color: #2d3748;
}
textarea {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font: inherit;
  resize: vertical;
}
.hint { margin: 0.5rem 0 0; font-size: 0.85rem; color: #4a5568; }
button {
  align-self: flex-start;
  padding: 0.6rem 1.25rem;
  background: #1a365d;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error { color: #c53030; margin: 0; }
.success {
  background: #f0fff4;
  border: 1px solid #9ae6b4;
  color: #22543d;
  padding: 1rem;
  border-radius: 6px;
}
</style>
