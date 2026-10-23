<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PublicCommentToggle from './PublicCommentToggle.vue'
import ReplaceWordDialog from './ReplaceWordDialog.vue'
import { useCommentModeration } from '../composables/useCommentModeration'

const { t } = useI18n()
const moderation = useCommentModeration()

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
  candidatesWidget: string | null
  candidatesGroupBy: string | null
}

interface MyCandidateResponseDto {
  candidateId: number
  response: boolean
  comment: string | null
  commentPublic?: boolean
  dateSubmitted: string
  lastModified: string | null
}

/** A published, still-open ballot measure on this election's ballot. */
interface BallotMeasureDto {
  id: number
  title: string
  summary: string
  effectiveDate: string
  closeDate: string | null
}

interface MyBallotResponseDto {
  measureId: number
  hasResponse: boolean
  response: boolean | null
  comment: string | null
  commentPublic?: boolean
  dateSubmitted: string | null
}

interface MyElectionResponsesDto {
  electionId: number
  hasResponses: boolean
  firstSubmittedAt: string | null
  responses: MyCandidateResponseDto[]
}

// focusMeasure: arriving from a ballot measure's Vote link, scroll to it.
const props = defineProps<{ id: number; focusMeasure?: number | null }>()
const router = useRouter()

const election = ref<ElectionDto | null>(null)
const mine = ref<MyElectionResponsesDto | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref<string | null>(null)
// Problems with a submission. Shown by the Submit button, with the form still
// on screen: the office (or candidate) still to choose links to its section.
const missing = ref<{ kind: 'office' | 'candidate'; office: string; label: string } | null>(null)
const submitError = ref<string | null>(null)
const closedReason = ref<string | null>(null)
const message = ref<string | null>(null)

// Per-candidate state used by the legacy Yes/No widget.
const answers = reactive<Record<number, { response: boolean | null; comment: string }>>({})

// Per-office selection state used by the new widget modes. The Set holds the
// candidate IDs selected within a given office. Size 0–1 for selectOne*,
// size 0–N for selectManyCheckbox.
const selectionByOffice = reactive<Record<string, Set<number>>>({})

// Per-candidate comment used by all widget modes.
const comments = reactive<Record<number, string>>({})
// Per-candidate "make comment public" box, all widget modes.
const commentPublic = reactive<Record<number, boolean>>({})

// The election's ballot measures, at the bottom of the form. Optional, like a
// real ballot: a measure left blank isn't submitted (decided 2026-10-15).
const measures = ref<BallotMeasureDto[]>([])
const measureAnswers = reactive<Record<number, { response: boolean | null; comment: string; commentPublic: boolean }>>({})
const votedOnMeasure = ref(false)

type Mode = 'fresh' | 'editing' | 'readonly' | 'choosing'
const mode = ref<Mode>('fresh')

// Resolves the widget hint into one of the supported canonical names, or
// "legacy" for the Yes/No-per-candidate fallback when the template has none.
type Widget =
  | 'selectOneRadio'
  | 'selectOneList'
  | 'selectOneCheckbox'
  | 'selectManyCheckbox'
  | 'legacy'

const widget = computed<Widget>(() => {
  switch (election.value?.candidatesWidget) {
    case 'selectOne':
    case 'selectOneRadio':    return 'selectOneRadio'
    case 'selectOneList':     return 'selectOneList'
    case 'selectOneCheckbox': return 'selectOneCheckbox'
    case 'selectManyCheckbox': return 'selectManyCheckbox'
    default: return 'legacy'
  }
})

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
      closedReason.value = t('election.closedNote')
      return
    }
    const mineRes = await axios.get<MyElectionResponsesDto>(
      `/api/polls/elections/${props.id}/responses/me`
    )
    mine.value = mineRes.data
    seedAnswers()
    await loadMeasures()
    mode.value = mine.value.hasResponses || votedOnMeasure.value ? 'choosing' : 'fresh'
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('election.loadFailed')
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
    comments[c.id] = p?.comment ?? ''
    commentPublic[c.id] = p?.commentPublic ?? false
  }

  // Rebuild per-office selection sets from the prior responses so the new
  // widgets show the user's existing vote when they re-open the form.
  for (const [office, group] of groupedByOffice.value) {
    const picked = group
      .filter(c => prev.get(c.id)?.response === true)
      .map(c => c.id)
    selectionByOffice[office] = new Set(picked)
  }
}

async function loadMeasures() {
  measures.value = (await axios.get<BallotMeasureDto[]>('/api/polls/ballot-measures', {
    params: { electionId: props.id }
  })).data ?? []
  const mineList = await Promise.all(measures.value.map(m =>
    axios.get<MyBallotResponseDto>(`/api/polls/ballot-measures/${m.id}/responses/me`).then(r => r.data)
  ))
  votedOnMeasure.value = mineList.some(r => r?.hasResponse)
  measures.value.forEach((m, i) => {
    const r = mineList[i]
    measureAnswers[m.id] = {
      response: r?.response ?? null,
      comment: r?.comment ?? '',
      commentPublic: r?.commentPublic ?? false
    }
  })
  if (props.focusMeasure && measures.value.some(m => m.id === props.focusMeasure)) {
    // After the loading state clears and the measures render.
    setTimeout(() => document.getElementById(`measure-${props.focusMeasure}`)?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }), 0)
    await nextTick()
  }
}

/** The section for an office, so a "please choose" message can link to it. */
function officeAnchor(office: string): string {
  return `office-${groupedByOffice.value.findIndex(([name]) => name === office)}`
}

/** Scroll to an office's section and put the cursor on its first choice. */
function goToOffice(office: string) {
  const el = document.getElementById(officeAnchor(office))
  el?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  el?.querySelector<HTMLElement>('input:not([type=hidden]), select')?.focus({ preventScroll: true })
}

// Once the office has a choice, the message (and its red outline) goes away.
watch([selectionByOffice, answers], () => {
  const m = missing.value
  if (!m) return
  const done = m.kind === 'office'
    ? (selectionByOffice[m.office]?.size ?? 0) > 0
    : (election.value?.candidates ?? []).filter(c => c.officeName === m.office).every(c => answers[c.id]?.response != null)
  if (done) missing.value = null
}, { deep: true })

function chooseEdit() { mode.value = 'editing' }
function chooseReadonly() { mode.value = 'readonly' }

const groupedByOffice = computed<[string, CandidateDto[]][]>(() => {
  const map = new Map<string, CandidateDto[]>()
  for (const c of election.value?.candidates ?? []) {
    const key = c.officeName
    const list = map.get(key) ?? []
    list.push(c)
    map.set(key, list)
  }
  return Array.from(map.entries())
})

function isSelected(office: string, candidateId: number): boolean {
  return selectionByOffice[office]?.has(candidateId) ?? false
}

function selectOnly(office: string, candidateId: number) {
  selectionByOffice[office] = new Set([candidateId])
}

function toggleSelection(office: string, candidateId: number) {
  const set = selectionByOffice[office] ?? new Set<number>()
  if (set.has(candidateId)) set.delete(candidateId)
  else set.add(candidateId)
  selectionByOffice[office] = new Set(set)
}

function onListChange(office: string, e: Event) {
  const raw = (e.target as HTMLSelectElement).value
  if (!raw) selectionByOffice[office] = new Set()
  else selectionByOffice[office] = new Set([Number(raw)])
}

async function submit() {
  if (!election.value) return
  const candidatesList = election.value.candidates

  // Per-widget validation: every office needs at least one selection in the
  // new modes; legacy mode requires a Yes/No on every candidate.
  if (widget.value === 'legacy') {
    for (const c of candidatesList) {
      if (answers[c.id]?.response == null) {
        missing.value = { kind: 'candidate', office: c.officeName, label: c.name }
        return
      }
    }
  } else {
    for (const [office] of groupedByOffice.value) {
      if ((selectionByOffice[office]?.size ?? 0) === 0) {
        missing.value = { kind: 'office', office, label: office }
        return
      }
    }
  }

  missing.value = null
  submitError.value = null
  submitting.value = true
  try {
    // Public comments: offer to replace any listed word; a "No" keeps that comment private.
    const legacy = widget.value === 'legacy'
    await moderation.review(candidatesList.map(c => ({
      inputId: `comment-c-${c.id}`,
      get: () => (legacy ? answers[c.id].comment : comments[c.id]) ?? '',
      set: (text: string) => { if (legacy) answers[c.id].comment = text; else comments[c.id] = text },
      isPublic: () => commentPublic[c.id] ?? false,
      makePrivate: () => { commentPublic[c.id] = false }
    })).concat(measures.value.map(m => ({
      inputId: `comment-m-${m.id}`,
      get: () => measureAnswers[m.id].comment,
      set: (text: string) => { measureAnswers[m.id].comment = text },
      isPublic: () => measureAnswers[m.id].commentPublic,
      makePrivate: () => { measureAnswers[m.id].commentPublic = false }
    }))))
    const payload = candidatesList.map(c => {
      const response = widget.value === 'legacy'
        ? answers[c.id].response
        : selectionByOffice[c.officeName]?.has(c.id) ?? false
      const commentText = widget.value === 'legacy'
        ? answers[c.id].comment
        : (comments[c.id] ?? '')
      return {
        candidateId: c.id,
        response,
        comment: commentText.trim() || null,
        commentPublic: commentPublic[c.id] ?? false
      }
    })
    await axios.post(`/api/polls/elections/${props.id}/responses`, { answers: payload })
    // Then each ballot measure that was answered.
    for (const m of measures.value) {
      const a = measureAnswers[m.id]
      if (a.response === null) continue
      await axios.post(`/api/polls/ballot-measures/${m.id}/responses`, {
        response: a.response,
        comment: a.comment.trim() || null,
        commentPublic: a.commentPublic
      })
    }
    message.value = t('election.submittedOk')
    setTimeout(() => router.push(`/polls/election/${props.id}/results`), 600)
  } catch (e: any) {
    submitError.value = e?.response?.data?.message ?? t('election.submissionFailed')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-if="loading" class="text-sm text-slate-600">{{ $t('common.loading') }}</div>
  <div v-else-if="error" class="text-sm text-red-700">{{ error }}</div>
  <div v-else-if="closedReason" class="rounded-md border border-orange-400 bg-orange-50 p-4">
    <p class="mb-2 text-sm text-orange-900">{{ closedReason }}</p>
    <router-link
      :to="`/polls/election/${props.id}/results`"
      class="text-sm font-semibold text-slate-800 underline"
    >
      {{ $t('nav.viewResults') }}
    </router-link>
  </div>

  <div v-else-if="election" class="flex flex-col gap-4">
    <header class="mb-2">
      <h2 class="mb-1 text-xl font-semibold text-slate-800">{{ election.title }}</h2>
      <p class="m-0 text-sm text-slate-500">
        {{ $t('election.dateLabel') }} {{ new Date(election.date).toLocaleDateString() }}
        · {{ $t('election.zipcodeLabel') }} {{ election.zipcode }}
        <template v-if="election.closeDate">
          · {{ $t('election.closesLabel') }} {{ new Date(election.closeDate).toLocaleString() }}
        </template>
      </p>
    </header>

    <div v-if="mode === 'choosing' && mine" class="rounded-md border border-sky-300 bg-sky-50 p-4">
      <p class="mb-2 text-sm text-slate-700">
        {{ $t('election.votedOn') }}
        <strong class="font-semibold">{{ new Date(mine.firstSubmittedAt!).toLocaleDateString() }}</strong>.
        {{ $t('election.wouldYouChange') }}
      </p>
      <div class="flex gap-2">
        <button
          @click="chooseEdit"
          class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900"
        >{{ $t('election.yesEdit') }}</button>
        <button
          @click="chooseReadonly"
          class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50"
        >{{ $t('election.noReview') }}</button>
      </div>
    </div>

    <div
      v-if="mode === 'readonly'"
      class="rounded-md border border-slate-300 bg-slate-50 p-3 text-sm text-slate-600"
    >
      {{ $t('election.readonlyNote') }}
    </div>

    <fieldset
      v-for="[officeName, group] in groupedByOffice"
      :id="officeAnchor(officeName)"
      :key="officeName"
      :disabled="mode === 'readonly'"
      class="scroll-mt-4 rounded-md border p-4 disabled:opacity-90"
      :class="missing?.office === officeName ? 'border-red-400 ring-2 ring-red-200' : 'border-slate-200'"
      :data-missing="missing?.office === officeName ? 'true' : undefined"
    >
      <legend class="px-2 text-sm font-semibold text-slate-700">{{ officeName }}</legend>

      <!-- selectOneList: dropdown picker -->
      <div v-if="widget === 'selectOneList'" class="mt-1">
        <select
          :value="Array.from(selectionByOffice[officeName] ?? [])[0] ?? ''"
          @change="onListChange(officeName, $event)"
          class="w-full rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        >
          <option value="">{{ $t('common.select') }}</option>
          <option v-for="c in group" :key="c.id" :value="c.id">
            {{ c.name }} ({{ c.affiliation }})
          </option>
        </select>
      </div>

      <!-- Each choice input is labelled by the candidate's name + affiliation
           (<label for>), so screen readers announce it and clicking the name
           selects it; the comment box stays outside the label. -->
      <!-- selectOneRadio: radio group, one selected per office -->
      <div
        v-else-if="widget === 'selectOneRadio'"
        v-for="c in group"
        :key="c.id"
        class="grid grid-cols-[auto_1fr] gap-2 border-b border-slate-100 py-2 last:border-b-0"
      >
        <input
          :id="`candidate-${c.id}`"
          type="radio"
          :name="`office-${officeName}`"
          :checked="isSelected(officeName, c.id)"
          @change="selectOnly(officeName, c.id)"
          class="mt-1"
        />
        <div class="flex flex-col">
          <label :for="`candidate-${c.id}`" class="flex cursor-pointer flex-col">
            <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
            <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
          </label>
          <input
            :id="`comment-c-${c.id}`"
            v-model="comments[c.id]"
            type="text"
            :placeholder="$t('common.comment')"
            class="mt-1 rounded border border-slate-200 p-2 text-sm focus:border-slate-500 focus:outline-none"
          />
          <PublicCommentToggle :id="`comment-public-c-${c.id}`" v-model="commentPublic[c.id]" />
        </div>
      </div>

      <!-- selectOneCheckbox: checkbox group constrained to one selection -->
      <div
        v-else-if="widget === 'selectOneCheckbox'"
        v-for="c in group"
        :key="c.id"
        class="grid grid-cols-[auto_1fr] gap-2 border-b border-slate-100 py-2 last:border-b-0"
      >
        <input
          :id="`candidate-${c.id}`"
          type="checkbox"
          :checked="isSelected(officeName, c.id)"
          @change="selectOnly(officeName, c.id)"
          class="mt-1"
        />
        <div class="flex flex-col">
          <label :for="`candidate-${c.id}`" class="flex cursor-pointer flex-col">
            <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
            <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
          </label>
          <input
            :id="`comment-c-${c.id}`"
            v-model="comments[c.id]"
            type="text"
            :placeholder="$t('common.comment')"
            class="mt-1 rounded border border-slate-200 p-2 text-sm focus:border-slate-500 focus:outline-none"
          />
          <PublicCommentToggle :id="`comment-public-c-${c.id}`" v-model="commentPublic[c.id]" />
        </div>
      </div>

      <!-- selectManyCheckbox: checkbox group, any number selected -->
      <div
        v-else-if="widget === 'selectManyCheckbox'"
        v-for="c in group"
        :key="c.id"
        class="grid grid-cols-[auto_1fr] gap-2 border-b border-slate-100 py-2 last:border-b-0"
      >
        <input
          :id="`candidate-${c.id}`"
          type="checkbox"
          :checked="isSelected(officeName, c.id)"
          @change="toggleSelection(officeName, c.id)"
          class="mt-1"
        />
        <div class="flex flex-col">
          <label :for="`candidate-${c.id}`" class="flex cursor-pointer flex-col">
            <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
            <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
          </label>
          <input
            :id="`comment-c-${c.id}`"
            v-model="comments[c.id]"
            type="text"
            :placeholder="$t('common.comment')"
            class="mt-1 rounded border border-slate-200 p-2 text-sm focus:border-slate-500 focus:outline-none"
          />
          <PublicCommentToggle :id="`comment-public-c-${c.id}`" v-model="commentPublic[c.id]" />
        </div>
      </div>

      <!-- legacy: per-candidate Yes/No -->
      <div
        v-else
        v-for="c in group"
        :key="c.id"
        class="grid grid-cols-[2fr_1fr] gap-2 border-b border-slate-100 py-2 last:border-b-0"
      >
        <div class="flex flex-col">
          <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
          <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
        </div>
        <div class="flex gap-3">
          <label class="flex items-center gap-1 text-sm">
            <input type="radio" :name="`c-${c.id}`" :value="true" v-model="answers[c.id].response" />
            {{ $t('common.yes') }}
          </label>
          <label class="flex items-center gap-1 text-sm">
            <input type="radio" :name="`c-${c.id}`" :value="false" v-model="answers[c.id].response" />
            {{ $t('common.no') }}
          </label>
        </div>
        <div class="col-span-2 flex flex-col">
          <input
            :id="`comment-c-${c.id}`"
            v-model="answers[c.id].comment"
            type="text"
            :placeholder="$t('common.comment')"
            class="rounded border border-slate-200 p-2 text-sm focus:border-slate-500 focus:outline-none"
          />
          <PublicCommentToggle :id="`comment-public-c-${c.id}`" v-model="commentPublic[c.id]" />
        </div>
      </div>
    </fieldset>

    <!-- The election's ballot measures: optional, submitted with the candidates. -->
    <template v-if="measures.length">
      <h3 class="m-0 mt-2 text-base font-semibold text-slate-800">{{ $t('election.measuresHeading') }}</h3>
      <p class="m-0 text-xs text-slate-500">{{ $t('election.measuresOptional') }}</p>
      <fieldset
        v-for="m in measures"
        :id="`measure-${m.id}`"
        :key="m.id"
        :disabled="mode === 'readonly'"
        data-test="ballot-measure"
        class="scroll-mt-4 rounded-md border p-4 disabled:opacity-90"
        :class="m.id === props.focusMeasure ? 'border-sky-400 bg-sky-50/40' : 'border-slate-200'"
      >
        <legend class="px-2 text-sm font-semibold text-slate-700">{{ m.title }}</legend>
        <p class="m-0 mb-2 text-xs text-slate-500">
          {{ $t('ballotMeasure.effectiveLabel') }} {{ new Date(m.effectiveDate).toLocaleDateString() }}
          <template v-if="m.closeDate">· {{ $t('ballotMeasure.closesLabel') }} {{ new Date(m.closeDate).toLocaleString() }}</template>
        </p>
        <p class="m-0 mb-3 whitespace-pre-wrap rounded-r bg-slate-50 p-3 text-sm text-slate-700 [border-left:3px_solid_#4299e1]">{{ m.summary }}</p>
        <div class="mb-2 flex gap-4">
          <label class="flex items-center gap-2 text-base text-slate-700">
            <input :id="`measure-${m.id}-yes`" type="radio" :name="`measure-${m.id}`" :value="true" v-model="measureAnswers[m.id].response" />
            {{ $t('common.yes') }}
          </label>
          <label class="flex items-center gap-2 text-base text-slate-700">
            <input :id="`measure-${m.id}-no`" type="radio" :name="`measure-${m.id}`" :value="false" v-model="measureAnswers[m.id].response" />
            {{ $t('common.no') }}
          </label>
        </div>
        <input
          :id="`comment-m-${m.id}`"
          v-model="measureAnswers[m.id].comment"
          type="text"
          :placeholder="$t('common.comment')"
          :aria-label="`${$t('common.comment')}: ${m.title}`"
          class="w-full rounded border border-slate-200 p-2 text-sm focus:border-slate-500 focus:outline-none"
        />
        <PublicCommentToggle :id="`comment-public-m-${m.id}`" v-model="measureAnswers[m.id].commentPublic" />
      </fieldset>
    </template>

    <p v-if="message" class="m-0 text-sm text-green-700">{{ message }}</p>
    <p v-if="missing" class="m-0 text-sm text-red-700" role="alert" data-test="missing-choice">
      <i18n-t :keypath="missing.kind === 'office' ? 'election.validation.selectionFor' : 'election.validation.yesNoFor'" tag="span">
        <template #office><a :href="`#${officeAnchor(missing.office)}`" class="font-semibold underline" @click.prevent="goToOffice(missing.office)">{{ missing.label }}</a></template>
        <template #name><a :href="`#${officeAnchor(missing.office)}`" class="font-semibold underline" @click.prevent="goToOffice(missing.office)">{{ missing.label }}</a></template>
      </i18n-t>
    </p>
    <p v-if="submitError" class="m-0 text-sm text-red-700" role="alert" data-test="submit-error">{{ submitError }}</p>

    <div v-if="mode !== 'readonly' && mode !== 'choosing'" class="flex gap-2">
      <button
        @click="submit"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? $t('common.submitting') : (mine?.hasResponses ? $t('election.updateVotes') : $t('election.submitVotes')) }}
      </button>
    </div>
    <ReplaceWordDialog v-if="moderation.asking.value" :asking="moderation.asking.value" @answer="moderation.answer" />
  </div>
</template>
