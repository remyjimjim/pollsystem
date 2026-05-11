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
  <div class="mx-auto max-w-3xl py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">Creator Request</h1>

    <div
      v-if="submitted"
      class="rounded-md border border-green-200 bg-green-50 p-4 text-green-900"
    >
      <p class="m-0">
        Your request has been submitted. You'll receive an email once it has been reviewed.
      </p>
    </div>

    <form v-else @submit.prevent="onSubmit" class="flex flex-col gap-5">
      <p class="m-0 text-slate-600">
        Tell us where and what kind of polls you'd like to create. An admin will
        review your request.
      </p>

      <fieldset class="rounded-md border border-slate-200 p-4">
        <legend class="px-2 text-sm font-semibold text-slate-700">Poll Types</legend>
        <div class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(200px,1fr))]">
          <label
            v-for="pt in pollTypes"
            :key="pt.id"
            class="flex items-center gap-2 text-sm"
          >
            <input type="checkbox" :value="pt.id" v-model="selectedPollTypeIds" />
            {{ pt.name }}
          </label>
        </div>
      </fieldset>

      <fieldset class="rounded-md border border-slate-200 p-4">
        <legend class="px-2 text-sm font-semibold text-slate-700">Geographic Scope</legend>
        <ZipSetter v-model="zipcodes" />
        <p v-if="zipcodes.length > 0" class="mt-2 text-sm text-slate-600">
          Selected: {{ zipcodes.join(', ') }}
        </p>
      </fieldset>

      <fieldset class="rounded-md border border-slate-200 p-4">
        <legend class="px-2 text-sm font-semibold text-slate-700">Reason (optional)</legend>
        <textarea
          v-model="reason"
          rows="5"
          maxlength="2000"
          placeholder="Why would you like to create polls in this area?"
          class="w-full resize-y rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
      </fieldset>

      <p v-if="error" class="m-0 text-sm text-red-700">{{ error }}</p>
      <button
        type="submit"
        :disabled="submitting"
        class="self-start rounded bg-slate-800 px-5 py-2 text-base text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? 'Submitting…' : 'Submit Request' }}
      </button>
    </form>
  </div>
</template>
