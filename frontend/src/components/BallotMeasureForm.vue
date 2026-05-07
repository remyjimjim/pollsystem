<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

interface BallotMeasureInitial {
  id: number
  pollTypeId: number
  electionId: number
  title: string
  summary: string
  effectiveDate: string  // YYYY-MM-DD
  closeDate: string | null
}

interface CreatorPollSummary {
  id: number
  type: 'Questionnaire' | 'Election' | 'BallotMeasure'
  title: string
  status: string
  closeDate: string | null
  createdAt: string
}

const props = defineProps<{
  pollTypeId: number
  initial?: BallotMeasureInitial
}>()

const router = useRouter()

function toLocalInput(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}`
  )
}

const form = reactive({
  electionId: props.initial?.electionId ?? null as number | null,
  title: props.initial?.title ?? '',
  summary: props.initial?.summary ?? '',
  effectiveDate: props.initial?.effectiveDate ?? '',
  closeDate: toLocalInput(props.initial?.closeDate ?? null)
})

const elections = ref<CreatorPollSummary[]>([])
const loadingElections = ref(false)

const draftId = ref<number | null>(props.initial?.id ?? null)
const submitting = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const closeWarning = ref<string | null>(null)

async function loadElections() {
  loadingElections.value = true
  try {
    const res = await axios.get<CreatorPollSummary[]>('/api/creator/polls')
    elections.value = res.data.filter(p => p.type === 'Election')
  } catch {
    error.value = 'Failed to load your elections'
  } finally {
    loadingElections.value = false
  }
}

function payload() {
  return {
    pollTypeId: props.pollTypeId,
    electionId: form.electionId,
    title: form.title.trim(),
    summary: form.summary.trim(),
    effectiveDate: form.effectiveDate,
    closeDate: form.closeDate ? new Date(form.closeDate).toISOString() : null
  }
}

function validate(): string | null {
  if (form.electionId == null) return 'Pick a parent Election'
  if (!form.title.trim()) return 'Title is required'
  if (!form.summary.trim()) return 'Summary is required'
  if (!form.effectiveDate) return 'Effective date is required'
  return null
}

async function saveDraft() {
  const v = validate()
  if (v) { error.value = v; return }
  error.value = null; message.value = null; closeWarning.value = null
  submitting.value = true
  try {
    if (draftId.value == null) {
      const res = await axios.post('/api/polls/ballot-measures', payload())
      draftId.value = res.data.id
    } else {
      await axios.put(`/api/polls/ballot-measures/${draftId.value}`, payload())
    }
    message.value = 'Draft saved'
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Save failed'
  } finally {
    submitting.value = false
  }
}

async function publish(confirmed = false) {
  if (draftId.value == null) {
    await saveDraft()
    if (draftId.value == null) return
  }
  error.value = null; message.value = null
  if (!confirmed) closeWarning.value = null
  submitting.value = true
  try {
    await axios.post(
      `/api/polls/ballot-measures/${draftId.value}/publish`,
      null,
      { params: { confirmed } }
    )
    message.value = 'Published!'
    setTimeout(() => router.push('/creator/dashboard'), 600)
  } catch (e: any) {
    const msg: string = e?.response?.data?.message ?? ''
    if (msg.startsWith('close_date_short:')) {
      const iso = msg.substring('close_date_short:'.length)
      closeWarning.value = `This poll will close on ${new Date(iso).toLocaleString()}. Continue?`
    } else {
      error.value = msg || 'Publish failed'
    }
  } finally {
    submitting.value = false
  }
}

onMounted(loadElections)
</script>

<template>
  <div class="form">
    <label>
      Parent Election
      <select v-model="form.electionId" :disabled="loadingElections" required>
        <option :value="null" disabled>
          {{ loadingElections ? 'Loading…' : 'Select an Election' }}
        </option>
        <option v-for="e in elections" :key="e.id" :value="e.id">
          {{ e.title }} ({{ e.status }})
        </option>
      </select>
    </label>
    <p v-if="!loadingElections && elections.length === 0" class="hint">
      You haven't created any Elections yet. Create one first, then attach a measure to it.
    </p>

    <label>
      Title
      <input v-model="form.title" maxlength="500" required />
    </label>

    <label>
      Summary
      <textarea v-model="form.summary" rows="4" required />
    </label>

    <div class="row-fields">
      <label>
        Effective date
        <input v-model="form.effectiveDate" type="date" required />
      </label>
      <label>
        Close date (optional)
        <input v-model="form.closeDate" type="datetime-local" />
      </label>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="success">{{ message }}</p>

    <div v-if="closeWarning" class="warning">
      <p>{{ closeWarning }}</p>
      <div class="row">
        <button type="button" class="primary" @click="publish(true)" :disabled="submitting">
          Confirm and publish
        </button>
        <button type="button" @click="closeWarning = null" :disabled="submitting">
          Cancel
        </button>
      </div>
    </div>

    <div v-else class="row">
      <button type="button" @click="saveDraft" :disabled="submitting">
        {{ submitting ? 'Saving…' : (draftId ? 'Save changes' : 'Save draft') }}
      </button>
      <button type="button" class="primary" @click="publish(false)" :disabled="submitting">
        Publish
      </button>
    </div>
  </div>
</template>

<style scoped>
.form { display: flex; flex-direction: column; gap: 1rem; }
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: #2d3748;
}
input,
textarea,
select {
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font: inherit;
  font-weight: 400;
}
.row-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
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
.hint { color: #718096; font-size: 0.85rem; margin: 0; }
.error { color: #c53030; margin: 0; }
.success { color: #2f855a; margin: 0; }
.warning {
  background: #fffaf0;
  border: 1px solid #ed8936;
  border-radius: 6px;
  padding: 1rem;
}
</style>
