<script setup lang="ts">
// Local development only: the route exists only in development builds
// (router/index.ts), and the backend endpoints only under the `local` profile.
// English only: it's a developer tool.
import { onMounted, ref } from 'vue'
import axios from 'axios'

interface Settings {
  resultsThreshold: number
  resultsThresholdDefault: number
  resultsThresholdOverridden: boolean
  pollsRebuilt?: number
}

const settings = ref<Settings | null>(null)
const value = ref<number | null>(null)
const saving = ref(false)
const message = ref<string | null>(null)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    settings.value = (await axios.get<Settings>('/api/dev/settings')).data
    value.value = settings.value.resultsThreshold
  } catch {
    error.value = 'Dev settings need the local backend (BuildAndDeploy.bash local or local-docker).'
  }
})

async function save(threshold: number | null) {
  saving.value = true
  message.value = null
  error.value = null
  try {
    const s = (await axios.put<Settings>('/api/dev/settings', { resultsThreshold: threshold })).data
    settings.value = s
    value.value = s.resultsThreshold
    message.value = `Saved: results now hidden until ${s.resultsThreshold} respondents, updating every ${s.resultsThreshold}. Rebuilt the results of ${s.pollsRebuilt ?? 0} polls.`
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? "Couldn't save"
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-2xl py-8">
    <h1 class="mb-1 text-2xl font-semibold text-slate-800">Dev settings</h1>
    <p class="mb-6 text-sm text-amber-800">Local development only. Staging and prod don't have this page, and ignore these settings.</p>

    <p v-if="error" class="text-sm text-red-700" data-test="dev-settings-error">{{ error }}</p>

    <section v-if="settings" class="rounded-md border border-slate-200 p-4" data-test="results-threshold">
      <h2 class="m-0 mb-1 text-base font-semibold text-slate-800">Results privacy threshold</h2>
      <p class="m-0 mb-3 text-sm text-slate-600">
        Results stay hidden until a poll has this many respondents, then update in steps of this many.
        Groups smaller than this are withheld (narrowed results, creator reports, inside/outside counts),
        and the creator's "results are now visible" email goes out at it.
        Prod uses {{ settings.resultsThresholdDefault }}.
      </p>
      <form class="flex flex-wrap items-center gap-2" @submit.prevent="save(value)">
        <label for="results-threshold" class="text-sm text-slate-700">Respondents</label>
        <input
          id="results-threshold"
          v-model.number="value"
          type="number"
          min="1"
          max="100"
          step="1"
          required
          class="w-24 rounded border border-slate-300 p-1.5 text-sm"
        />
        <button type="submit" :disabled="saving" class="rounded bg-slate-800 px-3 py-1.5 text-sm text-white hover:bg-slate-900 disabled:opacity-60">
          {{ saving ? 'Saving…' : 'Save' }}
        </button>
        <button
          v-if="settings.resultsThresholdOverridden"
          type="button"
          :disabled="saving"
          data-test="reset-threshold"
          class="rounded border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-60"
          @click="save(null)"
        >Back to {{ settings.resultsThresholdDefault }}</button>
      </form>
      <p class="m-0 mt-2 text-xs text-slate-500">
        Now: {{ settings.resultsThreshold }}{{ settings.resultsThresholdOverridden ? ' (changed here; kept across rebuilds)' : ' (the default)' }}.
        Saving rebuilds every poll's results for the new value. No emails are sent.
      </p>
      <p v-if="message" class="m-0 mt-2 text-sm text-green-700" data-test="dev-settings-saved">{{ message }}</p>
    </section>
  </div>
</template>
