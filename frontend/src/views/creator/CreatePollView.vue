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
  <div class="view">
    <h1>Create New Poll</h1>

    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="!selected">
      <h2>Choose a poll type</h2>
      <div class="cards">
        <button
          v-for="pt in pollTypes"
          :key="pt.id"
          class="card"
          @click="pickType(pt)"
        >
          <strong>{{ pt.name }}</strong>
        </button>
      </div>
    </section>

    <section v-else>
      <div class="header-row">
        <h2>{{ selected.name }}</h2>
        <button @click="reset" class="link">Change type</button>
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

      <div v-else class="todo">
        <p>The <strong>{{ selected.name }}</strong> form is not implemented yet.</p>
      </div>
    </section>
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
h2 {
  color: #2d3748;
  margin-bottom: 0.75rem;
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.75rem;
}
.card {
  padding: 1.5rem 1rem;
  background: white;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 120ms;
}
.card:hover {
  border-color: #1a365d;
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}
.link {
  background: none;
  border: none;
  color: #1a365d;
  cursor: pointer;
  text-decoration: underline;
  font-size: 0.9rem;
}
.todo {
  padding: 1rem;
  background: #fffaf0;
  border: 1px solid #ed8936;
  border-radius: 6px;
}
.todo code {
  background: white;
  padding: 0.1rem 0.3rem;
  border-radius: 3px;
  font-size: 0.85rem;
}
.error {
  color: #c53030;
}
</style>
