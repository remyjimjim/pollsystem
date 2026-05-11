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
  <div class="flex flex-col gap-4">
    <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
      Title
      <input
        v-model="form.title"
        maxlength="500"
        required
        class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
      />
    </label>

    <div class="grid grid-cols-2 gap-3">
      <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
        Election date
        <input
          v-model="form.date"
          type="date"
          required
          class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
        Zipcode
        <input
          v-model="form.zipcode"
          type="text"
          maxlength="5"
          pattern="[0-9]{5}"
          inputmode="numeric"
          required
          class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
        />
      </label>
    </div>

    <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
      Close date (optional)
      <input
        v-model="form.closeDate"
        type="datetime-local"
        class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
      />
    </label>

    <fieldset class="rounded-md border border-slate-200 p-4">
      <legend class="px-2 text-sm font-semibold text-slate-700">Candidates</legend>
      <p class="mb-2 text-sm text-slate-500">Voters will mark Yes / No on each candidate.</p>
      <div
        v-for="(c, i) in form.candidates"
        :key="i"
        class="mb-2 grid grid-cols-[1fr_1fr_1fr_2rem] gap-1"
      >
        <input
          v-model="c.name"
          placeholder="Name"
          maxlength="255"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
        <input
          v-model="c.affiliation"
          placeholder="Affiliation"
          maxlength="255"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
        <input
          v-model="c.officeName"
          placeholder="Office (e.g. Mayor)"
          maxlength="255"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
        <button
          type="button"
          @click="removeCandidate(i)"
          :disabled="form.candidates.length === 1"
          class="rounded border border-slate-300 bg-white hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
          aria-label="Remove candidate"
        >×</button>
      </div>
      <button
        type="button"
        @click="addCandidate"
        class="self-start rounded border border-dashed border-slate-300 bg-white px-3 py-2 text-sm hover:bg-slate-50"
      >+ Add candidate</button>
    </fieldset>

    <p v-if="error" class="m-0 text-sm text-red-700">{{ error }}</p>
    <p v-if="message" class="m-0 text-sm text-green-700">{{ message }}</p>

    <div v-if="closeWarning" class="rounded-md border border-orange-400 bg-orange-50 p-4">
      <p class="mb-2 text-sm text-orange-900">{{ closeWarning }}</p>
      <div class="flex gap-2">
        <button
          type="button"
          @click="publish(true)"
          :disabled="submitting"
          class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          Confirm and publish
        </button>
        <button
          type="button"
          @click="closeWarning = null"
          :disabled="submitting"
          class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
        >
          Cancel
        </button>
      </div>
    </div>

    <div v-else class="flex gap-2">
      <button
        type="button"
        @click="saveDraft"
        :disabled="submitting"
        class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? 'Saving…' : (draftId ? 'Save changes' : 'Save draft') }}
      </button>
      <button
        type="button"
        @click="publish(false)"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        Publish
      </button>
    </div>
  </div>
</template>
