<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

interface CandidateDto {
  id: number
  name: string
  affiliation: string
  officeId: number
  officeName: string
}

interface ElectionDto {
  id: number
  title: string
  date: string
  zipcode: string
  status: string
  closeDate: string | null
  candidates: CandidateDto[]
}

interface MyCandidateResponseDto {
  candidateId: number
  response: boolean
  comment: string | null
  dateSubmitted: string
  lastModified: string | null
}

interface MyElectionResponsesDto {
  electionId: number
  hasResponses: boolean
  firstSubmittedAt: string | null
  responses: MyCandidateResponseDto[]
}

const props = defineProps<{ id: number }>()
const router = useRouter()

const election = ref<ElectionDto | null>(null)
const mine = ref<MyElectionResponsesDto | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref<string | null>(null)
const closedReason = ref<string | null>(null)
const message = ref<string | null>(null)

// Per-candidate model: response is null until user picks; comment optional
const answers = reactive<Record<number, { response: boolean | null; comment: string }>>({})

type Mode = 'fresh' | 'editing' | 'readonly' | 'choosing'
const mode = ref<Mode>('fresh')

const isClosed = computed(() => {
  const e = election.value
  if (!e) return false
  if (e.status !== 'PUBLISHED') return true
  if (!e.closeDate) return false
  return new Date(e.closeDate).getTime() <= Date.now()
})

async function load() {
  loading.value = true
  error.value = null
  closedReason.value = null
  try {
    const res = await axios.get<ElectionDto>(`/api/polls/elections/${props.id}`)
    election.value = res.data
    if (isClosed.value) {
      closedReason.value = 'This poll is no longer available for responses.'
      return
    }
    const mineRes = await axios.get<MyElectionResponsesDto>(
      `/api/polls/elections/${props.id}/responses/me`
    )
    mine.value = mineRes.data
    seedAnswers()
    mode.value = mine.value.hasResponses ? 'choosing' : 'fresh'
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load election'
  } finally {
    loading.value = false
  }
}

function seedAnswers() {
  if (!election.value) return
  const prev = new Map((mine.value?.responses ?? []).map(r => [r.candidateId, r]))
  for (const c of election.value.candidates) {
    const p = prev.get(c.id)
    answers[c.id] = {
      response: p ? p.response : null,
      comment: p?.comment ?? ''
    }
  }
}

function chooseEdit() { mode.value = 'editing' }
function chooseReadonly() { mode.value = 'readonly' }

async function submit() {
  if (!election.value) return
  for (const c of election.value.candidates) {
    if (answers[c.id]?.response == null) {
      error.value = `Please answer Yes or No for: ${c.name}`
      return
    }
  }
  submitting.value = true
  error.value = null
  try {
    await axios.post(`/api/polls/elections/${props.id}/responses`, {
      answers: election.value.candidates.map(c => ({
        candidateId: c.id,
        response: answers[c.id].response,
        comment: answers[c.id].comment.trim() || null
      }))
    })
    message.value = 'Responses submitted successfully!'
    setTimeout(() => router.push(`/polls/election/${props.id}/results`), 600)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Submission failed'
  } finally {
    submitting.value = false
  }
}

// Group candidates by office for nicer display
const groupedByOffice = computed(() => {
  const map = new Map<string, CandidateDto[]>()
  for (const c of election.value?.candidates ?? []) {
    const list = map.get(c.officeName) ?? []
    list.push(c)
    map.set(c.officeName, list)
  }
  return Array.from(map.entries())
})

onMounted(load)
</script>

<template>
  <div v-if="loading">Loading…</div>
  <div v-else-if="error" class="error">{{ error }}</div>
  <div v-else-if="closedReason" class="notice">
    <p>{{ closedReason }}</p>
    <router-link :to="`/polls/election/${props.id}/results`">View results</router-link>
  </div>

  <div v-else-if="election" class="form">
    <header>
      <h2>{{ election.title }}</h2>
      <p class="hint">
        Election date: {{ new Date(election.date).toLocaleDateString() }}
        · Zipcode {{ election.zipcode }}
        <template v-if="election.closeDate">
          · Closes {{ new Date(election.closeDate).toLocaleString() }}
        </template>
      </p>
    </header>

    <div v-if="mode === 'choosing' && mine" class="prompt">
      <p>
        You voted on
        <strong>{{ new Date(mine.firstSubmittedAt!).toLocaleDateString() }}</strong>.
        Would you like to change your responses?
      </p>
      <div class="row">
        <button @click="chooseEdit" class="primary">Yes, edit</button>
        <button @click="chooseReadonly">No, just review</button>
      </div>
    </div>

    <div v-if="mode === 'readonly'" class="readonly-banner">
      Your previous votes are shown below. They cannot be modified here.
    </div>

    <fieldset
      v-for="[officeName, group] in groupedByOffice"
      :key="officeName"
      :disabled="mode === 'readonly'"
    >
      <legend>{{ officeName }}</legend>
      <div v-for="c in group" :key="c.id" class="candidate">
        <div class="candidate-info">
          <strong>{{ c.name }}</strong>
          <span class="affiliation">{{ c.affiliation }}</span>
        </div>
        <div class="vote">
          <label class="vote-option">
            <input type="radio" :name="`c-${c.id}`" :value="true" v-model="answers[c.id].response" />
            Yes
          </label>
          <label class="vote-option">
            <input type="radio" :name="`c-${c.id}`" :value="false" v-model="answers[c.id].response" />
            No
          </label>
        </div>
        <input
          v-model="answers[c.id].comment"
          type="text"
          placeholder="Comment (optional)"
          class="comment"
        />
      </div>
    </fieldset>

    <p v-if="message" class="success">{{ message }}</p>

    <div v-if="mode !== 'readonly' && mode !== 'choosing'" class="row">
      <button @click="submit" :disabled="submitting" class="primary">
        {{ submitting ? 'Submitting…' : (mine?.hasResponses ? 'Update votes' : 'Submit votes') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.form { display: flex; flex-direction: column; gap: 1rem; }
header { margin-bottom: 0.5rem; }
h2 { color: #1a365d; margin: 0 0 0.5rem; }
.hint { color: #718096; font-size: 0.85rem; margin: 0; }
.prompt {
  background: #ebf8ff;
  border: 1px solid #4299e1;
  padding: 1rem;
  border-radius: 6px;
}
.readonly-banner {
  background: #f7fafc;
  border: 1px solid #cbd5e0;
  padding: 0.75rem;
  border-radius: 6px;
  color: #4a5568;
}
.notice {
  background: #fffaf0;
  border: 1px solid #ed8936;
  padding: 1rem;
  border-radius: 6px;
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
.candidate {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 0.5rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid #edf2f7;
}
.candidate:last-child { border-bottom: none; }
.candidate-info {
  display: flex;
  flex-direction: column;
}
.affiliation { color: #4a5568; font-size: 0.85rem; }
.vote { display: flex; gap: 0.75rem; }
.vote-option {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-weight: 400;
  font-size: 0.9rem;
}
.comment {
  grid-column: 1 / -1;
  padding: 0.4rem;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font: inherit;
  font-size: 0.85rem;
}
.row { display: flex; gap: 0.5rem; }
button {
  padding: 0.5rem 1rem;
  border: 1px solid #cbd5e0;
  background: white;
  border-radius: 4px;
  cursor: pointer;
}
button.primary {
  background: #1a365d;
  color: white;
  border-color: #1a365d;
}
button:disabled { opacity: 0.6; cursor: not-allowed; }
.error { color: #c53030; }
.success { color: #2f855a; }
</style>
