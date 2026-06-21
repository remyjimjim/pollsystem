<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import axios from 'axios'

interface StateRow { id: number; name: string; initial: string }
interface CountyRow { id: number; stateId: number; name: string }
interface CountyZipRow { id: number; countyId: number; zipcode: string }

const { t } = useI18n()

interface QuestionResultDto {
  questionId: number
  text: string
  totalResponses: number
  byAnswer: Record<string, number>
}
interface QuestionnaireResultsDto {
  questionnaireId: number
  title: string
  totalRespondents: number
  perQuestion: QuestionResultDto[]
  filterApplied: Record<string, string> | null
  suppressed: boolean
  suppressionMessage: string | null
}

interface CandidateResultDto {
  candidateId: number
  name: string
  affiliation: string
  officeName: string
  yes: number
  no: number
  total: number
}
interface ElectionResultsDto {
  electionId: number
  title: string
  totalRespondents: number
  perCandidate: CandidateResultDto[]
  filterApplied: Record<string, string> | null
  suppressed: boolean
  suppressionMessage: string | null
}

interface BallotMeasureResultsDto {
  measureId: number
  title: string
  totalRespondents: number
  yes: number
  no: number
  filterApplied: Record<string, string> | null
  suppressed: boolean
  suppressionMessage: string | null
}

const route = useRoute()
const type = computed(() => String(route.params.type))
const id = computed(() => Number(route.params.id))

const qData = ref<QuestionnaireResultsDto | null>(null)
const eData = ref<ElectionResultsDto | null>(null)
const bData = ref<BallotMeasureResultsDto | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const onlyPurview = ref(false)

// ---------- geo pickers (same UX as /polls/search) ----------
const states = ref<StateRow[]>([])
const counties = ref<CountyRow[]>([])
const zipcodeOptions = ref<CountyZipRow[]>([])
const selectedStateIds = ref<number[]>([])
const lastClickedStateIndex = ref<number | null>(null)
const selectedCountyIds = ref<number[]>([])
const lastClickedCountyIndex = ref<number | null>(null)
const selectedZipcodes = ref<string[]>([])
const lastClickedZipIndex = ref<number | null>(null)
const statePickerOpen = ref(false)
const countyPickerOpen = ref(false)
const zipPickerOpen = ref(false)

async function loadStates() {
  try { states.value = (await axios.get<StateRow[]>('/api/states')).data } catch { /* non-fatal */ }
}
async function loadCounties(stateIds: number[]) {
  try {
    counties.value = (await axios.get<CountyRow[]>('/api/counties', {
      params: { state_id: stateIds.join(',') }
    })).data
  } catch { counties.value = [] }
}
async function loadZipcodesByCounty(countyIds: number[]) {
  try {
    zipcodeOptions.value = (await axios.post<CountyZipRow[]>('/api/zipcodes', { countyIds })).data
  } catch { zipcodeOptions.value = [] }
}
async function loadZipcodesByState(stateIds: number[]) {
  try {
    zipcodeOptions.value = (await axios.post<CountyZipRow[]>('/api/zipcodes', { stateIds })).data
  } catch { zipcodeOptions.value = [] }
}

async function onStateChange() {
  selectedCountyIds.value = []
  selectedZipcodes.value = []
  counties.value = []
  zipcodeOptions.value = []
  if (selectedStateIds.value.length > 0) {
    await loadCounties(selectedStateIds.value)
    await loadZipcodesByState(selectedStateIds.value)
  }
}
async function onCountyChange() {
  selectedZipcodes.value = []
  if (selectedCountyIds.value.length > 0) {
    await loadZipcodesByCounty(selectedCountyIds.value)
  } else if (selectedStateIds.value.length > 0) {
    await loadZipcodesByState(selectedStateIds.value)
  } else {
    zipcodeOptions.value = []
  }
}

function onStateClick(e: MouseEvent, idx: number, id: number) {
  const target = e.target as HTMLInputElement
  const willCheck = target.checked
  if (e.shiftKey && lastClickedStateIndex.value !== null) {
    const a = Math.min(lastClickedStateIndex.value, idx)
    const b = Math.max(lastClickedStateIndex.value, idx)
    const ids = states.value.slice(a, b + 1).map(s => s.id)
    selectedStateIds.value = willCheck
      ? Array.from(new Set([...selectedStateIds.value, ...ids]))
      : selectedStateIds.value.filter(x => !ids.includes(x))
  } else {
    selectedStateIds.value = willCheck
      ? Array.from(new Set([...selectedStateIds.value, id]))
      : selectedStateIds.value.filter(x => x !== id)
  }
  lastClickedStateIndex.value = idx
  onStateChange()
}
function onCountyClick(e: MouseEvent, idx: number, id: number) {
  const target = e.target as HTMLInputElement
  const willCheck = target.checked
  if (e.shiftKey && lastClickedCountyIndex.value !== null) {
    const a = Math.min(lastClickedCountyIndex.value, idx)
    const b = Math.max(lastClickedCountyIndex.value, idx)
    const ids = counties.value.slice(a, b + 1).map(c => c.id)
    selectedCountyIds.value = willCheck
      ? Array.from(new Set([...selectedCountyIds.value, ...ids]))
      : selectedCountyIds.value.filter(x => !ids.includes(x))
  } else {
    selectedCountyIds.value = willCheck
      ? Array.from(new Set([...selectedCountyIds.value, id]))
      : selectedCountyIds.value.filter(x => x !== id)
  }
  lastClickedCountyIndex.value = idx
  onCountyChange()
}
function onZipClick(e: MouseEvent, idx: number, code: string) {
  const target = e.target as HTMLInputElement
  const willCheck = target.checked
  if (e.shiftKey && lastClickedZipIndex.value !== null) {
    const a = Math.min(lastClickedZipIndex.value, idx)
    const b = Math.max(lastClickedZipIndex.value, idx)
    const codes = zipcodeOptions.value.slice(a, b + 1).map(z => z.zipcode)
    selectedZipcodes.value = willCheck
      ? Array.from(new Set([...selectedZipcodes.value, ...codes]))
      : selectedZipcodes.value.filter(z => !codes.includes(z))
  } else {
    selectedZipcodes.value = willCheck
      ? Array.from(new Set([...selectedZipcodes.value, code]))
      : selectedZipcodes.value.filter(z => z !== code)
  }
  lastClickedZipIndex.value = idx
}

const statePickerSummary = computed<string>(() => {
  if (selectedStateIds.value.length === 0) return t('results.stateAny')
  if (selectedStateIds.value.length === 1) {
    return states.value.find(s => s.id === selectedStateIds.value[0])?.name ?? t('results.stateAny')
  }
  return t('results.stateNSelected', { n: selectedStateIds.value.length })
})
const countyPickerSummary = computed<string>(() => {
  if (selectedCountyIds.value.length === 0) return t('results.countyAny')
  if (selectedCountyIds.value.length === 1) {
    return counties.value.find(c => c.id === selectedCountyIds.value[0])?.name ?? t('results.countyAny')
  }
  return t('results.countyNSelected', { n: selectedCountyIds.value.length })
})
const zipPickerSummary = computed<string>(() => {
  if (selectedZipcodes.value.length === 1) return selectedZipcodes.value[0]
  if (selectedZipcodes.value.length > 1) return t('results.zipcodeNSelected', { n: selectedZipcodes.value.length })
  return zipcodeOptions.value[0]?.zipcode ?? t('results.zipcodeAny')
})

function onDocClick(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  if (!target?.closest('[data-state-picker]')) statePickerOpen.value = false
  if (!target?.closest('[data-county-picker]')) countyPickerOpen.value = false
  if (!target?.closest('[data-zip-picker]')) zipPickerOpen.value = false
}
function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    statePickerOpen.value = false
    countyPickerOpen.value = false
    zipPickerOpen.value = false
  }
}

const isSupported = computed(
  () => type.value === 'questionnaire' || type.value === 'election' || type.value === 'ballot-measure'
)

const data = computed<{
  title: string
  totalRespondents: number
  filterApplied: Record<string, string> | null
  suppressed: boolean
  suppressionMessage: string | null
} | null>(() => {
  if (type.value === 'questionnaire') return qData.value
  if (type.value === 'election') return eData.value
  if (type.value === 'ballot-measure') return bData.value
  return null
})

async function load() {
  if (!isSupported.value) return
  loading.value = true
  error.value = null
  try {
    const params: Record<string, string> = {}
    // Geo filter cascade matches /polls/search: explicit zipcodes win,
    // otherwise counties expand to their zips, otherwise states.
    if (selectedZipcodes.value.length > 0) params.zipcode = selectedZipcodes.value.join(',')
    else if (selectedCountyIds.value.length > 0) params.countyId = selectedCountyIds.value.join(',')
    else if (selectedStateIds.value.length > 0) params.stateId = selectedStateIds.value.join(',')
    if (onlyPurview.value) params.onlyPurview = 'true'
    if (type.value === 'questionnaire') {
      const res = await axios.get<QuestionnaireResultsDto>(
        `/api/polls/questionnaires/${id.value}/results`,
        { params }
      )
      qData.value = res.data
      eData.value = null
      bData.value = null
    } else if (type.value === 'election') {
      const res = await axios.get<ElectionResultsDto>(
        `/api/polls/elections/${id.value}/results`,
        { params }
      )
      eData.value = res.data
      qData.value = null
      bData.value = null
    } else {
      const res = await axios.get<BallotMeasureResultsDto>(
        `/api/polls/ballot-measures/${id.value}/results`,
        { params }
      )
      bData.value = res.data
      qData.value = null
      eData.value = null
    }
  } catch (e: any) {
    error.value = e?.response?.status === 404
      ? t('results.poll404')
      : (e?.response?.data?.message ?? t('results.loadFailed'))
  } finally {
    loading.value = false
  }
}

function clearFilter() {
  selectedStateIds.value = []
  selectedCountyIds.value = []
  selectedZipcodes.value = []
  counties.value = []
  zipcodeOptions.value = []
  load()
}
const hasGeoFilter = computed(() =>
  selectedStateIds.value.length > 0 ||
  selectedCountyIds.value.length > 0 ||
  selectedZipcodes.value.length > 0
)

function pct(count: number, total: number): string {
  if (total === 0) return '0%'
  return `${Math.round((count / total) * 100)}%`
}

const groupedCandidates = computed(() => {
  if (!eData.value) return [] as Array<[string, CandidateResultDto[]]>
  const map = new Map<string, CandidateResultDto[]>()
  for (const c of eData.value.perCandidate) {
    const list = map.get(c.officeName) ?? []
    list.push(c)
    map.set(c.officeName, list)
  }
  return Array.from(map.entries())
})

watch([id, type], load, { immediate: false })
onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onEsc)
  loadStates()
  load()
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onEsc)
})
</script>

<template>
  <div class="mx-auto max-w-3xl py-8">
    <div v-if="!isSupported" class="rounded-md border border-orange-400 bg-orange-50 p-4">
      <h1 class="mb-2 text-xl font-semibold text-slate-800">{{ $t('results.heading') }}</h1>
      <p class="text-sm text-slate-700">
        {{ $t('results.unsupportedPre') }} <strong class="font-semibold">{{ type }}</strong> {{ $t('results.unsupportedPost') }}
      </p>
    </div>

    <template v-else>
      <h1 v-if="data" class="mb-4 text-2xl font-semibold text-slate-800">
        {{ $t('results.headingFor', { title: data.title }) }}
      </h1>
      <h1 v-else class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('results.heading') }}</h1>

      <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
      <p v-if="loading" class="text-sm text-slate-600">{{ $t('common.loading') }}</p>

      <!-- Purview note + checkbox. Default is "all respondents"; checking
           the box re-fetches with onlyPurview=true so the aggregate is
           narrowed to submitters whose user.zipcode is in the poll's set. -->
      <div v-if="data" class="mb-3 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
        <p class="m-0">{{ $t('results.purviewNote') }}</p>
        <p class="m-0 mt-1 text-xs text-slate-500">{{ $t('results.purviewUsOnly') }}</p>
        <label class="mt-2 flex items-center gap-2 font-semibold text-slate-700">
          <input v-model="onlyPurview" type="checkbox" @change="load" class="h-4 w-4" />
          {{ $t('results.onlyPurview') }}
        </label>
      </div>

      <form
        v-if="data"
        @submit.prevent="load"
        class="mb-4 grid grid-cols-1 items-end gap-3 rounded-md bg-slate-50 p-3 sm:grid-cols-[repeat(auto-fit,minmax(180px,1fr))]"
      >
        <!-- State picker -->
        <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
          {{ $t('results.state') }}
          <div data-state-picker class="relative">
            <button type="button" @click.stop="statePickerOpen = !statePickerOpen"
              class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50">
              <span>{{ statePickerSummary }}</span>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="ml-2 h-4 w-4 text-slate-500 transition-transform" :class="{ 'rotate-180': statePickerOpen }" aria-hidden="true"><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
            </button>
            <div v-if="statePickerOpen" class="absolute left-0 right-0 z-20 mt-1 max-h-48 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg">
              <label v-for="(s, idx) in states" :key="s.id" class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50">
                <input type="checkbox" :checked="selectedStateIds.includes(s.id)" @click="onStateClick($event, idx, s.id)" class="h-3.5 w-3.5" />
                <span>{{ s.name }}</span>
              </label>
            </div>
          </div>
        </label>

        <!-- County picker -->
        <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
          {{ $t('results.county') }}
          <div data-county-picker class="relative">
            <button type="button" @click.stop="countyPickerOpen = !countyPickerOpen"
              :disabled="counties.length === 0"
              class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60">
              <span>{{ countyPickerSummary }}</span>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="ml-2 h-4 w-4 text-slate-500 transition-transform" :class="{ 'rotate-180': countyPickerOpen }" aria-hidden="true"><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
            </button>
            <div v-if="countyPickerOpen && counties.length > 0" class="absolute left-0 right-0 z-20 mt-1 max-h-48 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg">
              <label v-for="(c, idx) in counties" :key="c.id" class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50">
                <input type="checkbox" :checked="selectedCountyIds.includes(c.id)" @click="onCountyClick($event, idx, c.id)" class="h-3.5 w-3.5" />
                <span>{{ c.name }}</span>
              </label>
            </div>
          </div>
        </label>

        <!-- Zipcode picker -->
        <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
          {{ $t('results.zipcode') }}
          <div data-zip-picker class="relative">
            <button type="button" @click.stop="zipPickerOpen = !zipPickerOpen"
              :disabled="zipcodeOptions.length === 0"
              class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60">
              <span class="font-mono">{{ zipPickerSummary }}</span>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="ml-2 h-4 w-4 text-slate-500 transition-transform" :class="{ 'rotate-180': zipPickerOpen }" aria-hidden="true"><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
            </button>
            <div v-if="zipPickerOpen && zipcodeOptions.length > 0" class="absolute left-0 right-0 z-20 mt-1 max-h-32 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg">
              <label v-for="(z, idx) in zipcodeOptions" :key="z.id" class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50">
                <input type="checkbox" :checked="selectedZipcodes.includes(z.zipcode)" @click="onZipClick($event, idx, z.zipcode)" class="h-3.5 w-3.5" />
                <span class="font-mono">{{ z.zipcode }}</span>
              </label>
            </div>
          </div>
        </label>

        <div class="flex items-end gap-2">
          <button
            type="submit"
            :disabled="loading"
            class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {{ $t('common.apply') }}
          </button>
          <button
            v-if="hasGeoFilter"
            type="button"
            @click="clearFilter"
            class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50"
          >
            {{ $t('common.clear') }}
          </button>
        </div>
      </form>

      <div
        v-if="data?.suppressed"
        class="rounded-md border border-orange-400 bg-orange-50 p-4"
      >
        <p class="m-0 text-sm text-orange-900">{{ data.suppressionMessage }}</p>
      </div>

      <template v-else-if="data">
        <p class="m-0 text-sm text-slate-500">
          {{ $t('results.respondents', { n: data.totalRespondents }) }}
          <span v-if="data.filterApplied">
            · {{ $t('results.filterPrefix') }} {{ Object.entries(data.filterApplied).map(([k, v]) => `${k}=${v}`).join(', ') }}
          </span>
        </p>

        <!-- Questionnaire results -->
        <template v-if="qData">
          <section
            v-for="q in qData.perQuestion"
            :key="q.questionId"
            class="mt-4 rounded-md border border-slate-200 p-4"
          >
            <h3 class="mb-1 text-base font-semibold text-slate-700">{{ q.text }}</h3>
            <p class="m-0 text-sm text-slate-500">{{ $t('results.responseCount', { n: q.totalResponses }) }}</p>
            <table v-if="q.totalResponses > 0" class="mt-2 w-full border-collapse">
              <tbody>
                <tr v-for="[answer, count] in Object.entries(q.byAnswer)" :key="answer">
                  <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">
                    {{ answer }}
                  </td>
                  <td class="rounded bg-slate-100 py-1 align-middle">
                    <div
                      class="rounded bg-sky-500 px-2 py-0.5 text-xs text-white whitespace-nowrap"
                      style="min-width: 2rem"
                      :style="{ width: pct(count, q.totalResponses) }"
                    >
                      <span>{{ count }} ({{ pct(count, q.totalResponses) }})</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else class="m-0 mt-1 text-sm text-slate-500">{{ $t('results.noResponsesYet') }}</p>
          </section>
        </template>

        <!-- Ballot measure results -->
        <template v-if="bData">
          <section class="mt-4 rounded-md border border-slate-200 p-4">
            <table v-if="bData.totalRespondents > 0" class="w-full border-collapse">
              <tbody>
                <tr>
                  <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.yes') }}</td>
                  <td class="rounded bg-slate-100 py-1 align-middle">
                    <div
                      class="rounded bg-green-700 px-2 py-0.5 text-xs text-white whitespace-nowrap"
                      style="min-width: 2rem"
                      :style="{ width: pct(bData.yes, bData.totalRespondents) }"
                    >
                      <span>{{ bData.yes }} ({{ pct(bData.yes, bData.totalRespondents) }})</span>
                    </div>
                  </td>
                </tr>
                <tr>
                  <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.no') }}</td>
                  <td class="rounded bg-slate-100 py-1 align-middle">
                    <div
                      class="rounded bg-red-700 px-2 py-0.5 text-xs text-white whitespace-nowrap"
                      style="min-width: 2rem"
                      :style="{ width: pct(bData.no, bData.totalRespondents) }"
                    >
                      <span>{{ bData.no }} ({{ pct(bData.no, bData.totalRespondents) }})</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else class="m-0 text-sm text-slate-500">{{ $t('results.noVotesYet') }}</p>
          </section>
        </template>

        <!-- Election results -->
        <template v-if="eData">
          <section
            v-for="[officeName, group] in groupedCandidates"
            :key="officeName"
            class="mt-4 rounded-md border border-slate-200 p-4"
          >
            <h3 class="mb-1 text-base font-semibold text-slate-700">{{ officeName }}</h3>
            <div
              v-for="c in group"
              :key="c.candidateId"
              class="border-b border-slate-100 py-3 last:border-b-0"
            >
              <div class="mb-1 flex flex-col">
                <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
                <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
              </div>
              <table v-if="c.total > 0" class="mt-2 w-full border-collapse">
                <tbody>
                  <tr>
                    <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.yes') }}</td>
                    <td class="rounded bg-slate-100 py-1 align-middle">
                      <div
                        class="rounded bg-green-700 px-2 py-0.5 text-xs text-white whitespace-nowrap"
                        style="min-width: 2rem"
                        :style="{ width: pct(c.yes, c.total) }"
                      >
                        <span>{{ c.yes }} ({{ pct(c.yes, c.total) }})</span>
                      </div>
                    </td>
                  </tr>
                  <tr>
                    <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.no') }}</td>
                    <td class="rounded bg-slate-100 py-1 align-middle">
                      <div
                        class="rounded bg-red-700 px-2 py-0.5 text-xs text-white whitespace-nowrap"
                        style="min-width: 2rem"
                        :style="{ width: pct(c.no, c.total) }"
                      >
                        <span>{{ c.no }} ({{ pct(c.no, c.total) }})</span>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-else class="m-0 text-sm text-slate-500">{{ $t('results.noVotesYet') }}</p>
            </div>
          </section>
        </template>
      </template>
    </template>
  </div>
</template>
