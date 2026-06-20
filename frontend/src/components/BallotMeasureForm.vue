<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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
    error.value = t('form.ballotMeasure.electionsLoadFailed')
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
  if (form.electionId == null) return t('form.validation.pickElection')
  if (!form.title.trim()) return t('form.validation.titleRequired')
  if (!form.summary.trim()) return t('form.validation.summaryRequired')
  if (!form.effectiveDate) return t('form.validation.effectiveRequired')
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
    message.value = t('form.draftSaved')
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('form.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function publish(confirmed = false) {
  // Always save first so in-memory edits (e.g. closeDate) are persisted
  // before the publish endpoint validates them.
  await saveDraft()
  if (draftId.value == null || error.value) return
  message.value = null
  if (!confirmed) closeWarning.value = null
  submitting.value = true
  try {
    await axios.post(
      `/api/polls/ballot-measures/${draftId.value}/publish`,
      null,
      { params: { confirmed } }
    )
    message.value = t('form.published')
    setTimeout(() => router.push('/creator/dashboard'), 600)
  } catch (e: any) {
    const msg: string = e?.response?.data?.message ?? ''
    if (msg.startsWith('close_date_short:')) {
      const iso = msg.substring('close_date_short:'.length)
      closeWarning.value = t('form.closeDateShort', { date: new Date(iso).toLocaleString() })
    } else {
      error.value = msg || t('form.publishFailed')
    }
  } finally {
    submitting.value = false
  }
}

onMounted(loadElections)
</script>

<template>
  <div data-component="ballot-measure-form" class="flex flex-col gap-4">
    <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
      {{ $t('form.ballotMeasure.parentElection') }}
      <select
        v-model="form.electionId"
        :disabled="loadingElections"
        required
        class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none disabled:opacity-60"
      >
        <option :value="null" disabled>
          {{ loadingElections ? $t('common.loading') : $t('form.ballotMeasure.selectElection') }}
        </option>
        <option v-for="e in elections" :key="e.id" :value="e.id">
          {{ e.title }} ({{ e.status }})
        </option>
      </select>
    </label>
    <p v-if="!loadingElections && elections.length === 0" class="m-0 text-sm text-slate-500">
      {{ $t('form.ballotMeasure.noElections') }}
    </p>

    <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
      {{ $t('form.title') }}
      <input
        v-model="form.title"
        maxlength="500"
        required
        class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
      />
    </label>

    <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
      {{ $t('form.summary') }}
      <textarea
        v-model="form.summary"
        rows="4"
        required
        class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
      />
    </label>

    <div class="grid grid-cols-2 gap-3">
      <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
        {{ $t('form.ballotMeasure.effectiveDate') }}
        <input
          v-model="form.effectiveDate"
          type="date"
          required
          class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
        {{ $t('form.closeDateOptional') }}
        <input
          v-model="form.closeDate"
          type="datetime-local"
          class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
        />
      </label>
    </div>

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
          {{ $t('form.confirmPublish') }}
        </button>
        <button
          type="button"
          @click="closeWarning = null"
          :disabled="submitting"
          class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {{ $t('form.cancel') }}
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
        {{ submitting ? $t('form.saving') : (draftId ? $t('form.saveChanges') : $t('form.saveDraft')) }}
      </button>
      <button
        type="button"
        @click="router.push('/creator/dashboard')"
        :disabled="submitting"
        class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ $t('form.cancel') }}
      </button>
      <button
        type="button"
        @click="publish(false)"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ $t('form.publish') }}
      </button>
    </div>
  </div>
</template>
