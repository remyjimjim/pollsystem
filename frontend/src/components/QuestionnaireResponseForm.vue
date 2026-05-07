<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

interface QuestionDto { id: number; text: string }
interface QuestionnaireDto {
  id: number
  title: string
  summary: string
  status: string
  closeDate: string | null
  questions: QuestionDto[]
}

interface MyResponseDto {
  questionId: number
  response: string
  comment: string | null
  dateSubmitted: string
  lastModified: string | null
}

interface MyResponsesDto {
  questionnaireId: number
  hasResponses: boolean
  firstSubmittedAt: string | null
  responses: MyResponseDto[]
}

const props = defineProps<{ id: number }>()
const router = useRouter()

const poll = ref<QuestionnaireDto | null>(null)
const mine = ref<MyResponsesDto | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref<string | null>(null)
const closedReason = ref<string | null>(null)
const message = ref<string | null>(null)

// answers keyed by questionId
const answers = reactive<Record<number, { response: string; comment: string }>>({})
type Mode = 'fresh' | 'editing' | 'readonly' | 'choosing'
const mode = ref<Mode>('fresh')

const isClosed = computed(() => {
  const p = poll.value
  if (!p) return false
  if (p.status !== 'PUBLISHED') return true
  if (!p.closeDate) return false
  return new Date(p.closeDate).getTime() <= Date.now()
})

async function load() {
  loading.value = true
  error.value = null
  closedReason.value = null
  try {
    const pollRes = await axios.get<QuestionnaireDto>(
      `/api/polls/questionnaires/${props.id}`
    )
    poll.value = pollRes.data
    if (isClosed.value) {
      closedReason.value = 'This poll is no longer available for responses.'
      return
    }
    const mineRes = await axios.get<MyResponsesDto>(
      `/api/polls/questionnaires/${props.id}/responses/me`
    )
    mine.value = mineRes.data
    seedAnswers()
    mode.value = mine.value.hasResponses ? 'choosing' : 'fresh'
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load poll'
  } finally {
    loading.value = false
  }
}

function seedAnswers() {
  if (!poll.value) return
  const prev = new Map((mine.value?.responses ?? []).map(r => [r.questionId, r]))
  for (const q of poll.value.questions) {
    const p = prev.get(q.id)
    answers[q.id] = {
      response: p?.response ?? '',
      comment: p?.comment ?? ''
    }
  }
}

function chooseEdit() { mode.value = 'editing' }
function chooseReadonly() { mode.value = 'readonly' }

async function submit() {
  if (!poll.value) return
  for (const q of poll.value.questions) {
    if (!answers[q.id]?.response.trim()) {
      error.value = `Please answer: "${q.text}"`
      return
    }
  }
  submitting.value = true
  error.value = null
  try {
    await axios.post(`/api/polls/questionnaires/${props.id}/responses`, {
      answers: poll.value.questions.map(q => ({
        questionId: q.id,
        response: answers[q.id].response.trim(),
        comment: answers[q.id].comment.trim() || null
      }))
    })
    message.value = 'Responses submitted successfully!'
    setTimeout(() => {
      router.push(`/polls/questionnaire/${props.id}/results`)
    }, 600)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Submission failed'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-if="loading">Loading…</div>
  <div v-else-if="error" class="error">{{ error }}</div>
  <div v-else-if="closedReason" class="notice">
    <p>{{ closedReason }}</p>
    <router-link :to="`/polls/questionnaire/${props.id}/results`">View results</router-link>
  </div>

  <div v-else-if="poll" class="form">
    <header>
      <h2>{{ poll.title }}</h2>
      <p class="summary">{{ poll.summary }}</p>
      <p v-if="poll.closeDate" class="hint">
        Closes {{ new Date(poll.closeDate).toLocaleString() }}
      </p>
    </header>

    <div v-if="mode === 'choosing' && mine" class="prompt">
      <p>
        You submitted answers on
        <strong>{{ new Date(mine.firstSubmittedAt!).toLocaleDateString() }}</strong>.
        Would you like to change your responses?
      </p>
      <div class="row">
        <button @click="chooseEdit" class="primary">Yes, edit</button>
        <button @click="chooseReadonly">No, just review</button>
      </div>
    </div>

    <div v-if="mode === 'readonly'" class="readonly-banner">
      Your previous responses are shown below. They cannot be modified here.
    </div>

    <fieldset
      v-for="q in poll.questions"
      :key="q.id"
      :disabled="mode === 'readonly'"
    >
      <legend>{{ q.text }}</legend>
      <label>
        Answer
        <textarea
          v-model="answers[q.id].response"
          rows="2"
          required
        />
      </label>
      <label>
        Comment (optional)
        <input v-model="answers[q.id].comment" type="text" />
      </label>
    </fieldset>

    <p v-if="message" class="success">{{ message }}</p>

    <div v-if="mode !== 'readonly' && mode !== 'choosing'" class="row">
      <button @click="submit" :disabled="submitting" class="primary">
        {{ submitting ? 'Submitting…' : (mine?.hasResponses ? 'Update responses' : 'Submit responses') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
header { margin-bottom: 0.5rem; }
h2 { color: #1a365d; margin: 0 0 0.5rem; }
.summary { margin: 0; color: #4a5568; white-space: pre-wrap; }
.hint { color: #718096; font-size: 0.85rem; margin-top: 0.5rem; }
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
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.5rem;
  font-size: 0.85rem;
  color: #4a5568;
}
input, textarea {
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font: inherit;
}
.row {
  display: flex;
  gap: 0.5rem;
}
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
