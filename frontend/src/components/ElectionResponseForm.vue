<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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

// Per-candidate state used by the legacy Yes/No widget.
const answers = reactive<Record<number, { response: boolean | null; comment: string }>>({})

// Per-office selection state used by the new widget modes. The Set holds the
// candidate IDs selected within a given office. Size 0–1 for selectOne*,
// size 0–N for selectManyCheckbox.
const selectionByOffice = reactive<Record<string, Set<number>>>({})

// Per-candidate comment used by all widget modes.
const comments = reactive<Record<number, string>>({})

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
    mode.value = mine.value.hasResponses ? 'choosing' : 'fresh'
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
        error.value = t('election.validation.yesNoFor', { name: c.name })
        return
      }
    }
  } else {
    for (const [office] of groupedByOffice.value) {
      if ((selectionByOffice[office]?.size ?? 0) === 0) {
        error.value = t('election.validation.selectionFor', { office })
        return
      }
    }
  }

  submitting.value = true
  error.value = null
  try {
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
        comment: commentText.trim() || null
      }
    })
    await axios.post(`/api/polls/elections/${props.id}/responses`, { answers: payload })
    message.value = t('election.submittedOk')
    setTimeout(() => router.push(`/polls/election/${props.id}/results`), 600)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('election.submissionFailed')
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
      :key="officeName"
      :disabled="mode === 'readonly'"
      class="rounded-md border border-slate-200 p-4 disabled:opacity-90"
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

      <!-- selectOneRadio: radio group, one selected per office -->
      <div
        v-else-if="widget === 'selectOneRadio'"
        v-for="c in group"
        :key="c.id"
        class="grid grid-cols-[auto_1fr] gap-2 border-b border-slate-100 py-2 last:border-b-0"
      >
        <input
          type="radio"
          :name="`office-${officeName}`"
          :checked="isSelected(officeName, c.id)"
          @change="selectOnly(officeName, c.id)"
          class="mt-1"
        />
        <div class="flex flex-col">
          <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
          <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
          <input
            v-model="comments[c.id]"
            type="text"
            :placeholder="$t('common.comment')"
            class="mt-1 rounded border border-slate-200 p-2 text-sm focus:border-slate-500 focus:outline-none"
          />
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
          type="checkbox"
          :checked="isSelected(officeName, c.id)"
          @change="selectOnly(officeName, c.id)"
          class="mt-1"
        />
        <div class="flex flex-col">
          <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
          <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
          <input
            v-model="comments[c.id]"
            type="text"
            :placeholder="$t('common.comment')"
            class="mt-1 rounded border border-slate-200 p-2 text-sm focus:border-slate-500 focus:outline-none"
          />
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
          type="checkbox"
          :checked="isSelected(officeName, c.id)"
          @change="toggleSelection(officeName, c.id)"
          class="mt-1"
        />
        <div class="flex flex-col">
          <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
          <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
          <input
            v-model="comments[c.id]"
            type="text"
            :placeholder="$t('common.comment')"
            class="mt-1 rounded border border-slate-200 p-2 text-sm focus:border-slate-500 focus:outline-none"
          />
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
        <input
          v-model="answers[c.id].comment"
          type="text"
          :placeholder="$t('common.comment')"
          class="col-span-2 rounded border border-slate-200 p-2 text-sm focus:border-slate-500 focus:outline-none"
        />
      </div>
    </fieldset>

    <p v-if="message" class="m-0 text-sm text-green-700">{{ message }}</p>

    <div v-if="mode !== 'readonly' && mode !== 'choosing'" class="flex gap-2">
      <button
        @click="submit"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? $t('common.submitting') : (mine?.hasResponses ? $t('election.updateVotes') : $t('election.submitVotes')) }}
      </button>
    </div>
  </div>
</template>
