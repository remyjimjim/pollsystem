<script setup lang="ts">
import { reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

interface CandidateInputModel {
  name: string
  affiliation: string
  officeName: string
}

interface ElectionInitial {
  id: number
  pollTypeId: number
  title: string
  date: string  // YYYY-MM-DD
  zipcode: string
  closeDate: string | null
  candidates: CandidateInputModel[]
}

const props = defineProps<{
  pollTypeId: number
  initial?: ElectionInitial
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
  title: props.initial?.title ?? '',
  date: props.initial?.date ?? '',
  zipcode: props.initial?.zipcode ?? '',
  closeDate: toLocalInput(props.initial?.closeDate ?? null),
  candidates:
    props.initial && props.initial.candidates.length > 0
      ? props.initial.candidates.map(c => ({ ...c }))
      : ([{ name: '', affiliation: '', officeName: '' }] as CandidateInputModel[])
})

const draftId = ref<number | null>(props.initial?.id ?? null)
const submitting = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const closeWarning = ref<string | null>(null)

function addCandidate() {
  form.candidates.push({ name: '', affiliation: '', officeName: '' })
}
function removeCandidate(idx: number) {
  if (form.candidates.length > 1) form.candidates.splice(idx, 1)
}

function payload() {
  return {
    pollTypeId: props.pollTypeId,
    title: form.title.trim(),
    date: form.date,
    zipcode: form.zipcode.trim(),
    closeDate: form.closeDate ? new Date(form.closeDate).toISOString() : null,
    candidates: form.candidates
      .map(c => ({
        name: c.name.trim(),
        affiliation: c.affiliation.trim(),
        officeName: c.officeName.trim()
      }))
      .filter(c => c.name && c.affiliation && c.officeName)
  }
}

function validate(): string | null {
  if (!form.title.trim()) return 'Title is required'
  if (!form.date) return 'Election date is required'
  if (!/^\d{5}$/.test(form.zipcode.trim())) return 'Zipcode must be 5 digits'
  const completeCandidates = form.candidates.filter(
    c => c.name.trim() && c.affiliation.trim() && c.officeName.trim()
  )
  if (completeCandidates.length === 0) {
    return 'Add at least one complete candidate (name, affiliation, office)'
  }
  return null
}

async function saveDraft() {
  const v = validate()
  if (v) { error.value = v; return }
  error.value = null; message.value = null; closeWarning.value = null
  submitting.value = true
  try {
    if (draftId.value == null) {
      const res = await axios.post('/api/polls/elections', payload())
      draftId.value = res.data.id
    } else {
      await axios.put(`/api/polls/elections/${draftId.value}`, payload())
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
      `/api/polls/elections/${draftId.value}/publish`,
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
</script>

<template>
  <div class="form">
    <label>
      Title
      <input v-model="form.title" maxlength="500" required />
    </label>

    <div class="row-fields">
      <label>
        Election date
        <input v-model="form.date" type="date" required />
      </label>
      <label>
        Zipcode
        <input
          v-model="form.zipcode"
          type="text"
          maxlength="5"
          pattern="[0-9]{5}"
          inputmode="numeric"
          required
        />
      </label>
    </div>

    <label>
      Close date (optional)
      <input v-model="form.closeDate" type="datetime-local" />
    </label>

    <fieldset>
      <legend>Candidates</legend>
      <p class="hint">Voters will mark Yes / No on each candidate.</p>
      <div v-for="(c, i) in form.candidates" :key="i" class="candidate">
        <input v-model="c.name" placeholder="Name" maxlength="255" />
        <input v-model="c.affiliation" placeholder="Affiliation" maxlength="255" />
        <input v-model="c.officeName" placeholder="Office (e.g. Mayor)" maxlength="255" />
        <button
          type="button"
          @click="removeCandidate(i)"
          :disabled="form.candidates.length === 1"
          class="remove"
          aria-label="Remove candidate"
        >×</button>
      </div>
      <button type="button" @click="addCandidate" class="add">+ Add candidate</button>
    </fieldset>

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
input {
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
  grid-template-columns: 1fr 1fr 1fr 2rem;
  gap: 0.4rem;
  margin-bottom: 0.4rem;
}
.remove {
  background: white;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  cursor: pointer;
}
.remove:disabled { opacity: 0.4; cursor: not-allowed; }
.add {
  background: white;
  border: 1px dashed #cbd5e0;
  padding: 0.4rem 0.8rem;
  border-radius: 4px;
  cursor: pointer;
  align-self: flex-start;
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
.hint { color: #718096; font-size: 0.85rem; margin: 0 0 0.5rem; }
.error { color: #c53030; margin: 0; }
.success { color: #2f855a; margin: 0; }
.warning {
  background: #fffaf0;
  border: 1px solid #ed8936;
  border-radius: 6px;
  padding: 1rem;
}
</style>
