<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PurviewSetter from '@/components/PurviewSetter.vue'
import { ScopeLevel, type Purview } from '@/types'

const { t } = useI18n()

// Elections are purviewed at County / State / Nationwide (no single zip).
const ELECTION_LEVELS = [ScopeLevel.COUNTY, ScopeLevel.STATE, ScopeLevel.NATIONAL]

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
  scopeLevel: ScopeLevel
  regionIds: number[]
  regionStateIds: number[]
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
  closeDate: toLocalInput(props.initial?.closeDate ?? null),
  candidates:
    props.initial && props.initial.candidates.length > 0
      ? props.initial.candidates.map(c => ({ ...c }))
      : ([{ name: '', affiliation: '', officeName: '' }] as CandidateInputModel[])
})

// Purview (County/State/Nationwide). Seeded from the draft on edit.
const initialPurview = props.initial
  ? {
      scopeLevel: props.initial.scopeLevel,
      regionIds: props.initial.regionIds,
      zipcodes: [] as string[],
      regionStateIds: props.initial.regionStateIds,
    }
  : null
const purview = ref<Purview>(
  props.initial
    ? { scopeLevel: props.initial.scopeLevel, regionIds: props.initial.regionIds, zipcodes: [] }
    : { scopeLevel: ScopeLevel.STATE, regionIds: [], zipcodes: [] },
)

const draftId = ref<number | null>(props.initial?.id ?? null)

// ---------- its ballot measures (decided 2026-10-15) ----------
// Listed at the bottom once the election is saved. Draft ones go live with
// the election when it's published; Edit leaves this form, so it offers to
// save unsaved changes first.
interface MeasureRow { id: number; title: string; status: string }
const measures = ref<MeasureRow[]>([])
const draftMeasures = computed(() => measures.value.filter(m => m.status === 'DRAFT'))
async function loadMeasures() {
  if (draftId.value == null) return
  try {
    measures.value = (await axios.get<MeasureRow[]>('/api/polls/ballot-measures', { params: { electionId: draftId.value } })).data ?? []
  } catch {
    measures.value = []
  }
}
onMounted(loadMeasures)

// Unsaved changes = the form differs from what was last saved (or loaded).
const snapshot = () => { const p = payload(); return JSON.stringify({ ...p, regionIds: [...p.regionIds].sort((x, y) => x - y) }) }
const savedState = ref('')
onMounted(() => { savedState.value = snapshot() })
const dirty = computed(() => snapshot() !== savedState.value)

const leaveFor = ref<MeasureRow | null>(null)
function editMeasure(m: MeasureRow) {
  if (!dirty.value) { router.push(`/creator/polls/ballot-measure/${m.id}/edit`); return }
  leaveFor.value = m
}
async function leave(save: boolean) {
  const m = leaveFor.value
  if (!m) return
  if (save) {
    await saveDraft()
    if (error.value) { leaveFor.value = null; return }
  }
  leaveFor.value = null
  router.push(`/creator/polls/ballot-measure/${m.id}/edit`)
}

// Publishing with draft measures: confirm, listing what goes live with it.
const publishAsk = ref(false)
function askPublish() {
  if (draftMeasures.value.length) publishAsk.value = true
  else publish(false)
}
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
    scopeLevel: purview.value.scopeLevel,
    regionIds: purview.value.regionIds,
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
  if (!form.title.trim()) return t('form.validation.titleRequired')
  if (!form.date) return t('form.validation.dateRequired')
  if (purview.value.scopeLevel !== ScopeLevel.NATIONAL && purview.value.regionIds.length === 0) {
    return t('form.validation.purviewRequired')
  }
  const completeCandidates = form.candidates.filter(
    c => c.name.trim() && c.affiliation.trim() && c.officeName.trim()
  )
  if (completeCandidates.length === 0) {
    return t('form.validation.completeCandidates')
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
    message.value = t('form.draftSaved')
    savedState.value = snapshot()
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
      `/api/polls/elections/${draftId.value}/publish`,
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
</script>

<template>
  <div data-component="election-form" class="flex flex-col gap-4">
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
      {{ $t('form.election.electionDate') }}
      <input
        v-model="form.date"
        type="date"
        required
        class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
      />
    </label>

    <fieldset class="flex flex-col gap-2">
      <legend class="text-sm font-semibold text-slate-700">{{ $t('form.election.purview') }}</legend>
      <p class="m-0 text-xs text-slate-500">{{ $t('form.election.purviewHint') }}</p>
      <PurviewSetter :allowed-levels="ELECTION_LEVELS" :initial="initialPurview" v-model="purview" />
    </fieldset>

    <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
      {{ $t('form.closeDateOptional') }}
      <input
        v-model="form.closeDate"
        type="datetime-local"
        class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
      />
    </label>

    <fieldset class="rounded-md border border-slate-200 p-4">
      <legend class="px-2 text-sm font-semibold text-slate-700">{{ $t('form.election.candidates') }}</legend>
      <p class="mb-2 text-sm text-slate-500">{{ $t('form.election.candidatesHelp') }}</p>
      <div
        v-for="(c, i) in form.candidates"
        :key="i"
        class="mb-2 grid grid-cols-[1fr_1fr_1fr_2rem] gap-1"
      >
        <input
          v-model="c.name"
          :placeholder="$t('form.election.candidateName')"
          maxlength="255"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
        <input
          v-model="c.affiliation"
          :placeholder="$t('form.election.candidateAffiliation')"
          maxlength="255"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
        <input
          v-model="c.officeName"
          :placeholder="$t('form.election.candidateOffice')"
          maxlength="255"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
        <button
          type="button"
          @click="removeCandidate(i)"
          :disabled="form.candidates.length === 1"
          class="rounded border border-slate-300 bg-white hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
          :aria-label="$t('form.election.removeCandidate')"
        >×</button>
      </div>
      <button
        type="button"
        @click="addCandidate"
        class="self-start rounded border border-dashed border-slate-300 bg-white px-3 py-2 text-sm hover:bg-slate-50"
      >{{ $t('form.election.addCandidate') }}</button>
    </fieldset>

    <section v-if="draftId != null" class="rounded-md border border-slate-200 p-4" data-test="election-measures">
      <h3 class="m-0 mb-1 text-sm font-semibold text-slate-700">{{ $t('form.election.measuresHeading') }}</h3>
      <p class="m-0 mb-2 text-xs text-slate-500">{{ $t('form.election.measuresHelp') }}</p>
      <p v-if="measures.length === 0" class="m-0 text-sm text-slate-500">{{ $t('form.election.noMeasures') }}</p>
      <ul v-else class="m-0 list-none p-0">
        <li v-for="m in measures" :key="m.id" class="flex items-center gap-2 border-b border-slate-100 py-1.5 last:border-b-0" data-test="election-measure">
          <span class="text-sm text-slate-800">{{ m.title }}</span>
          <span class="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">{{ $t(`form.election.measureStatus.${m.status}`) }}</span>
          <button
            v-if="m.status === 'DRAFT'"
            type="button"
            data-test="edit-measure"
            class="ml-auto rounded border border-slate-300 bg-white px-2 py-0.5 text-xs hover:bg-slate-50"
            @click="editMeasure(m)"
          >{{ $t('form.election.editMeasure') }}</button>
          <router-link
            v-else
            :to="`/polls/ballot-measure/${m.id}/results`"
            class="ml-auto text-xs text-slate-800 underline"
          >{{ $t('form.election.viewMeasure') }}</router-link>
        </li>
      </ul>
    </section>

    <!-- Edit a measure with unsaved election changes: save first? -->
    <div v-if="leaveFor" role="dialog" aria-modal="true" data-test="save-before-leaving" class="fixed inset-0 z-50 flex items-start justify-center bg-black/40 p-4" @click.self="leaveFor = null">
      <div class="mt-24 w-full max-w-md rounded bg-white p-5 shadow-xl">
        <h2 class="mb-1 text-base font-semibold text-slate-800">{{ $t('form.election.saveFirstTitle') }}</h2>
        <p class="mb-3 text-sm text-slate-600">{{ $t('form.election.saveFirstBody', { title: leaveFor.title }) }}</p>
        <div class="flex flex-wrap justify-end gap-2">
          <button type="button" class="rounded border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50" @click="leaveFor = null">{{ $t('form.cancel') }}</button>
          <button type="button" data-test="leave-without-saving" class="rounded border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50" @click="leave(false)">{{ $t('form.election.goWithoutSaving') }}</button>
          <button type="button" data-test="save-and-go" class="rounded bg-slate-800 px-3 py-1 text-sm text-white hover:bg-slate-900" @click="leave(true)">{{ $t('form.election.saveAndGo') }}</button>
        </div>
      </div>
    </div>

    <!-- Publish with draft measures: they go live with the election. -->
    <div v-if="publishAsk" class="rounded-md border border-sky-300 bg-sky-50 p-4" data-test="publish-with-measures">
      <p class="m-0 mb-1 text-sm text-slate-800">{{ $t('form.election.publishWithMeasures', { n: draftMeasures.length }, draftMeasures.length) }}</p>
      <ul class="m-0 mb-3 pl-5 text-sm text-slate-700">
        <li v-for="m in draftMeasures" :key="m.id">{{ m.title }}</li>
      </ul>
      <div class="flex gap-2">
        <button type="button" data-test="publish-all" :disabled="submitting" class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:opacity-60" @click="publishAsk = false; publish(false)">{{ $t('form.election.publishAll') }}</button>
        <button type="button" :disabled="submitting" class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50" @click="publishAsk = false">{{ $t('form.cancel') }}</button>
      </div>
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

    <div v-else-if="!publishAsk" class="flex gap-2">
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
        @click="askPublish"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ $t('form.publish') }}
      </button>
    </div>
  </div>
</template>
