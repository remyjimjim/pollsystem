<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import type { PollType } from '@/types'
import QuestionnaireForm from '@/components/QuestionnaireForm.vue'
import ElectionForm from '@/components/ElectionForm.vue'
import BallotMeasureForm from '@/components/BallotMeasureForm.vue'

const pollTypes = ref<PollType[]>([])
const selected = ref<PollType | null>(null)
const template = ref<unknown>(null)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    const res = await axios.get<PollType[]>('/api/poll-types')
    pollTypes.value = res.data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load poll types'
  }
})

async function pickType(pt: PollType) {
  selected.value = pt
  template.value = null
  try {
    const res = await axios.get(`/api/poll-types/${pt.id}/template`)
    template.value = res.data
  } catch {
    // template optional in UI for now
  }
}

function reset() {
  selected.value = null
  template.value = null
}
</script>

<template>
  <div class="mx-auto max-w-3xl py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">Create New Poll</h1>

    <p v-if="error" class="text-sm text-red-700">{{ error }}</p>

    <section v-if="!selected">
      <h2 class="mb-3 text-lg font-semibold text-slate-700">Choose a poll type</h2>
      <div class="grid gap-3 sm:grid-cols-[repeat(auto-fit,minmax(200px,1fr))]">
        <button
          v-for="pt in pollTypes"
          :key="pt.id"
          @click="pickType(pt)"
          class="rounded-lg border-2 border-slate-200 bg-white px-4 py-6 transition-colors hover:border-slate-800"
        >
          <strong class="font-semibold text-slate-800">{{ pt.name }}</strong>
        </button>
      </div>
    </section>

    <section v-else>
      <div class="mb-4 flex items-center justify-between">
        <h2 class="m-0 text-lg font-semibold text-slate-700">{{ selected.name }}</h2>
        <button
          @click="reset"
          class="text-sm text-slate-800 underline"
        >Change type</button>
      </div>

      <QuestionnaireForm
        v-if="selected.name === 'Questionnaire'"
        :poll-type-id="selected.id"
      />

      <ElectionForm
        v-else-if="selected.name === 'Election'"
        :poll-type-id="selected.id"
      />

      <BallotMeasureForm
        v-else-if="selected.name === 'Referendum/Ballot Measure'"
        :poll-type-id="selected.id"
      />

      <div v-else class="rounded-md border border-orange-400 bg-orange-50 p-4">
        <p class="m-0 text-sm text-slate-700">
          The <strong class="font-semibold">{{ selected.name }}</strong> form is not implemented yet.
        </p>
      </div>
    </section>
  </div>
</template>
