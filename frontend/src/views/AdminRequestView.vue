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
  <div class="mx-auto max-w-3xl py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">Request Admin Access</h1>

    <div
      v-if="submitted"
      class="rounded-md border border-green-200 bg-green-50 p-4 text-green-900"
    >
      <p class="m-0">
        Your request has been submitted. A Super will review it and you'll
        receive an email once it's processed.
      </p>
    </div>

    <form v-else @submit.prevent="onSubmit" class="flex flex-col gap-5">
      <p class="m-0 text-slate-600">
        Pick the zipcodes you want to administer Creator Requests for.
        A Super will review your request.
      </p>

      <fieldset class="rounded-md border border-slate-200 p-4">
        <legend class="px-2 text-sm font-semibold text-slate-700">
          Zipcodes you want to administer
        </legend>
        <ZipSetter v-model="zipcodes" />
        <p v-if="zipcodes.length > 0" class="mt-2 text-sm text-slate-600">
          Selected: {{ zipcodes.join(', ') }}
        </p>
      </fieldset>

      <fieldset class="rounded-md border border-slate-200 p-4">
        <legend class="px-2 text-sm font-semibold text-slate-700">Reason</legend>
        <textarea
          v-model="reason"
          rows="5"
          maxlength="2000"
          placeholder="Why would you like to be an admin in this area?"
          class="w-full resize-y rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
      </fieldset>

      <p v-if="error" class="m-0 text-sm text-red-700">{{ error }}</p>
      <button
        type="submit"
        :disabled="submitting"
        class="self-start rounded bg-slate-800 px-5 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? 'Submitting…' : 'Submit Request' }}
      </button>
    </form>
  </div>
</template>
