<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import type { PollType } from '@/types'
import ZipSetter from '@/components/ZipSetter.vue'

const pollTypes = ref<PollType[]>([])
const selectedPollTypeIds = ref<number[]>([])
const zipcodes = ref<string[]>([])
const reason = ref('')

const submitting = ref(false)
const submitted = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    const res = await axios.get<PollType[]>('/api/poll-types')
    pollTypes.value = res.data
  } catch {
    // Endpoint not implemented yet — fall back to known names from V1 seed
    pollTypes.value = [
      { id: 1, pollType: 1, name: 'Election' },
      { id: 2, pollType: 2, name: 'Questionnaire' },
      { id: 3, pollType: 3, name: 'Referendum/Ballot Measure' }
    ]
  }
})

async function onSubmit() {
  error.value = null
  if (selectedPollTypeIds.value.length === 0) {
    error.value = 'Select at least one poll type'
    return
  }
  if (zipcodes.value.length === 0) {
    error.value = 'Select at least one zipcode'
    return
  }
  if (reason.value.trim().length === 0) {
    error.value = 'Reason is required'
    return
  }
  submitting.value = true
  try {
    await axios.post('/api/creator-requests', {
      pollTypeIds: selectedPollTypeIds.value,
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
    <h1>Creator Request</h1>

    <div v-if="submitted" class="success">
      <p>Your request has been submitted. You'll receive an email once it has been reviewed.</p>
    </div>

    <form v-else @submit.prevent="onSubmit" class="form">
      <p class="lead">
        Tell us where and what kind of polls you'd like to create. An admin will review your request.
      </p>

      <fieldset>
        <legend>Poll Types</legend>
        <div class="checkbox-grid">
          <label v-for="pt in pollTypes" :key="pt.id" class="check">
            <input type="checkbox" :value="pt.id" v-model="selectedPollTypeIds" />
            {{ pt.name }}
          </label>
        </div>
      </fieldset>

      <fieldset>
        <legend>Geographic Scope</legend>
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
          placeholder="Why would you like to create polls in this area?"
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
h1 {
  margin-bottom: 1rem;
  color: #1a365d;
}
.lead {
  color: #4a5568;
  margin-bottom: 1rem;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
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
.checkbox-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 0.4rem;
}
.check {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
textarea {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font-size: 1rem;
  font-family: inherit;
  resize: vertical;
}
.hint {
  margin: 0.5rem 0 0;
  font-size: 0.85rem;
  color: #4a5568;
}
button {
  align-self: flex-start;
  padding: 0.6rem 1.25rem;
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
  margin: 0;
}
.success {
  background: #f0fff4;
  border: 1px solid #9ae6b4;
  color: #22543d;
  padding: 1rem;
  border-radius: 6px;
}
</style>
