<script setup lang="ts">
import { reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import ZipSetter from '@/components/ZipSetter.vue'

interface QuestionnaireInitial {
  id: number
  pollTypeId: number
  title: string
  summary: string
  closeDate: string | null
  questions: { text: string }[]
  zipcodes: string[]
}

const props = defineProps<{
  pollTypeId: number
  initial?: QuestionnaireInitial
}>()

const router = useRouter()

function toLocalInput(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  // datetime-local expects YYYY-MM-DDTHH:mm in local time
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}`
  )
}

const form = reactive({
  title: props.initial?.title ?? '',
  summary: props.initial?.summary ?? '',
  closeDate: toLocalInput(props.initial?.closeDate ?? null),
  zipcodes: [...(props.initial?.zipcodes ?? [])] as string[],
  questions:
    props.initial && props.initial.questions.length > 0
      ? props.initial.questions.map(q => ({ text: q.text }))
      : ([{ text: '' }] as { text: string }[])
})

const draftId = ref<number | null>(props.initial?.id ?? null)
const submitting = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const closeWarning = ref<string | null>(null)

function addQuestion() {
  form.questions.push({ text: '' })
}
function removeQuestion(idx: number) {
  if (form.questions.length > 1) form.questions.splice(idx, 1)
}

function payload() {
  return {
    pollTypeId: props.pollTypeId,
    title: form.title.trim(),
    summary: form.summary.trim(),
    closeDate: form.closeDate ? new Date(form.closeDate).toISOString() : null,
    zipcodes: form.zipcodes,
    questions: form.questions
      .map(q => ({ text: q.text.trim() }))
      .filter(q => q.text.length > 0)
  }
}

function validate(): string | null {
  if (!form.title.trim()) return 'Title is required'
  if (!form.summary.trim()) return 'Summary is required'
  if (form.zipcodes.length === 0) return 'Select at least one zipcode'
  if (form.questions.every(q => !q.text.trim())) return 'Add at least one question'
  return null
}

async function saveDraft() {
  const v = validate()
  if (v) { error.value = v; return }
  error.value = null; message.value = null; closeWarning.value = null
  submitting.value = true
  try {
    if (draftId.value == null) {
      const res = await axios.post('/api/polls/questionnaires', payload())
      draftId.value = res.data.id
    } else {
      await axios.put(`/api/polls/questionnaires/${draftId.value}`, payload())
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
      `/api/polls/questionnaires/${draftId.value}/publish`,
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

    <label>
      Summary
      <textarea v-model="form.summary" rows="3" required />
    </label>

    <label>
      Close date (optional)
      <input v-model="form.closeDate" type="datetime-local" />
    </label>

    <fieldset>
      <legend>Geographic Scope</legend>
      <ZipSetter v-model="form.zipcodes" />
    </fieldset>

    <fieldset>
      <legend>Questions</legend>
      <div v-for="(q, i) in form.questions" :key="i" class="question-row">
        <input
          v-model="q.text"
          :placeholder="`Question ${i + 1}`"
          maxlength="1000"
        />
        <button
          type="button"
          @click="removeQuestion(i)"
          :disabled="form.questions.length === 1"
          class="remove"
          aria-label="Remove question"
        >×</button>
      </div>
      <button type="button" @click="addQuestion" class="add">+ Add question</button>
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
.form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.9rem;
  font-weight: 600;
  color: #2d3748;
}
input,
textarea {
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font: inherit;
  font-weight: 400;
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
.question-row {
  display: flex;
  gap: 0.4rem;
  margin-bottom: 0.4rem;
}
.question-row input {
  flex: 1;
}
.remove {
  width: 2rem;
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
.error { color: #c53030; margin: 0; }
.success { color: #2f855a; margin: 0; }
.warning {
  background: #fffaf0;
  border: 1px solid #ed8936;
  border-radius: 6px;
  padding: 1rem;
}
</style>
