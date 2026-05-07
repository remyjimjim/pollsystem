<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

interface BallotMeasureDto {
  id: number
  title: string
  summary: string
  effectiveDate: string
  zipcode: string
  status: string
  closeDate: string | null
  electionTitle: string
}

interface MyBallotResponseDto {
  measureId: number
  hasResponse: boolean
  response: boolean | null
  comment: string | null
  dateSubmitted: string | null
  lastModified: string | null
}

const props = defineProps<{ id: number }>()
const router = useRouter()

const measure = ref<BallotMeasureDto | null>(null)
const mine = ref<MyBallotResponseDto | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref<string | null>(null)
const closedReason = ref<string | null>(null)
const message = ref<string | null>(null)

const answer = ref<boolean | null>(null)
const comment = ref('')

type Mode = 'fresh' | 'editing' | 'readonly' | 'choosing'
const mode = ref<Mode>('fresh')

const isClosed = computed(() => {
  const m = measure.value
  if (!m) return false
  if (m.status !== 'PUBLISHED') return true
  if (!m.closeDate) return false
  return new Date(m.closeDate).getTime() <= Date.now()
})

async function load() {
  loading.value = true
  error.value = null
  closedReason.value = null
  try {
    const res = await axios.get<BallotMeasureDto>(`/api/polls/ballot-measures/${props.id}`)
    measure.value = res.data
    if (isClosed.value) {
      closedReason.value = 'This poll is no longer available for responses.'
      return
    }
    const mineRes = await axios.get<MyBallotResponseDto>(
      `/api/polls/ballot-measures/${props.id}/responses/me`
    )
    mine.value = mineRes.data
    if (mine.value.hasResponse) {
      answer.value = mine.value.response
      comment.value = mine.value.comment ?? ''
      mode.value = 'choosing'
    } else {
      mode.value = 'fresh'
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load ballot measure'
  } finally {
    loading.value = false
  }
}

function chooseEdit() { mode.value = 'editing' }
function chooseReadonly() { mode.value = 'readonly' }

async function submit() {
  if (answer.value == null) {
    error.value = 'Please answer Yes or No'
    return
  }
  submitting.value = true
  error.value = null
  try {
    await axios.post(`/api/polls/ballot-measures/${props.id}/responses`, {
      response: answer.value,
      comment: comment.value.trim() || null
    })
    message.value = 'Response submitted successfully!'
    setTimeout(() => router.push(`/polls/ballot-measure/${props.id}/results`), 600)
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
    <router-link :to="`/polls/ballot-measure/${props.id}/results`">View results</router-link>
  </div>

  <div v-else-if="measure" class="form">
    <header>
      <h2>{{ measure.title }}</h2>
      <p class="hint">
        Part of: {{ measure.electionTitle }}
        · Zipcode {{ measure.zipcode }}
        · Effective {{ new Date(measure.effectiveDate).toLocaleDateString() }}
        <template v-if="measure.closeDate">
          · Closes {{ new Date(measure.closeDate).toLocaleString() }}
        </template>
      </p>
      <p class="summary">{{ measure.summary }}</p>
    </header>

    <div v-if="mode === 'choosing' && mine" class="prompt">
      <p>
        You voted on
        <strong>{{ new Date(mine.dateSubmitted!).toLocaleDateString() }}</strong>:
        <strong>{{ mine.response ? 'Yes' : 'No' }}</strong>.
        Would you like to change your response?
      </p>
      <div class="row">
        <button @click="chooseEdit" class="primary">Yes, edit</button>
        <button @click="chooseReadonly">No, just review</button>
      </div>
    </div>

    <div v-if="mode === 'readonly'" class="readonly-banner">
      Your previous response is shown below. It cannot be modified here.
    </div>

    <fieldset :disabled="mode === 'readonly'">
      <legend>Your vote</legend>
      <div class="vote">
        <label class="vote-option">
          <input type="radio" :value="true" v-model="answer" name="vote" />
          Yes
        </label>
        <label class="vote-option">
          <input type="radio" :value="false" v-model="answer" name="vote" />
          No
        </label>
      </div>
      <label>
        Comment (optional)
        <textarea v-model="comment" rows="2" />
      </label>
    </fieldset>

    <p v-if="message" class="success">{{ message }}</p>

    <div v-if="mode !== 'readonly' && mode !== 'choosing'" class="row">
      <button @click="submit" :disabled="submitting" class="primary">
        {{ submitting ? 'Submitting…' : (mine?.hasResponse ? 'Update vote' : 'Submit vote') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.form { display: flex; flex-direction: column; gap: 1rem; }
header { margin-bottom: 0.5rem; }
h2 { color: #1a365d; margin: 0 0 0.5rem; }
.hint { color: #718096; font-size: 0.85rem; margin: 0 0 0.5rem; }
.summary {
  color: #2d3748;
  margin: 0;
  padding: 0.75rem;
  background: #f7fafc;
  border-left: 3px solid #4299e1;
  border-radius: 0 4px 4px 0;
  white-space: pre-wrap;
}
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
.vote {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 0.75rem;
}
.vote-option {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-weight: 400;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: #4a5568;
}
textarea {
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font: inherit;
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
