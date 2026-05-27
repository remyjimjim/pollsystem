<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()

interface ZipState {
  code: string
  state: string
}
interface PollSearchResult {
  id: number
  type: 'Questionnaire' | 'Election' | 'BallotMeasure'
  title: string
  closeDate: string | null
  zipcodes: ZipState[]
}
interface SearchSuggestions {
  titles: string[]
  candidates: string[]
}
interface StateRow { id: number; name: string; initial: string }
interface CountyRow { id: number; stateId: number; name: string }
interface CountyZipRow { id: number; countyId: number; zipcode: string }

// Which row's "extra zipcodes" popover is open. null = closed.
const expandedKey = ref<string | null>(null)
function rowKey(r: PollSearchResult): string {
  return `${r.type}-${r.id}`
}
function toggleExpand(r: PollSearchResult, e: MouseEvent) {
  e.stopPropagation()
  expandedKey.value = expandedKey.value === rowKey(r) ? null : rowKey(r)
}
function closeExpanded() {
  expandedKey.value = null
}
function onDocClick(e: MouseEvent) {
  // Close if the click was outside any open popover or its trigger.
  const target = e.target as HTMLElement | null
  if (!target?.closest('[data-zip-popover]') && !target?.closest('[data-zip-trigger]')) {
    expandedKey.value = null
  }
  if (!target?.closest('[data-zip-picker]')) {
    zipPickerOpen.value = false
  }
  if (!target?.closest('[data-state-picker]')) {
    statePickerOpen.value = false
  }
  if (!target?.closest('[data-county-picker]')) {
    countyPickerOpen.value = false
  }
}
function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    expandedKey.value = null
    zipPickerOpen.value = false
    statePickerOpen.value = false
    countyPickerOpen.value = false
  }
}
// Distinct values drawn from active polls, shown as native <datalist> hints.
const suggestions = ref<SearchSuggestions>({ titles: [], candidates: [] })

async function loadSuggestions() {
  try {
    const res = await axios.get<SearchSuggestions>('/api/polls/search/suggestions')
    suggestions.value = res.data
  } catch {
    // Autocomplete is a convenience; a failure here shouldn't block searching.
  }
}

// Geography cascade. Selections in state + county narrow the zipcode
// dropdown; only the chosen zipcode is sent as a search param.
const states = ref<StateRow[]>([])
const counties = ref<CountyRow[]>([])
const zipcodeOptions = ref<CountyZipRow[]>([])
const selectedStateIds = ref<number[]>([])
const lastClickedStateIndex = ref<number | null>(null)
const selectedCountyId = ref<number | ''>('')

async function loadStates() {
  try {
    const res = await axios.get<StateRow[]>('/api/states')
    states.value = res.data
  } catch {
    // Cascade is geography UX scaffolding; failures shouldn't block search.
  }
}
async function loadCounties(stateIds: number[]) {
  try {
    const res = await axios.get<CountyRow[]>('/api/counties', {
      params: { state_id: stateIds.join(',') }
    })
    counties.value = res.data
  } catch {
    counties.value = []
  }
}
async function loadZipcodesByCounty(countyId: number) {
  try {
    const res = await axios.get<CountyZipRow[]>('/api/zipcodes', { params: { county_ids: countyId } })
    zipcodeOptions.value = res.data
  } catch {
    zipcodeOptions.value = []
  }
}
async function loadZipcodesByState(stateIds: number[]) {
  try {
    const res = await axios.get<CountyZipRow[]>('/api/zipcodes', {
      params: { state_id: stateIds.join(',') }
    })
    zipcodeOptions.value = res.data
  } catch {
    zipcodeOptions.value = []
  }
}
async function loadZipcodesByPrefix(prefix: string) {
  try {
    const res = await axios.get<CountyZipRow[]>('/api/zipcodes', { params: { prefix } })
    zipcodeOptions.value = res.data
  } catch {
    zipcodeOptions.value = []
  }
}

// User-typed zipcode-search input. Drives a debounced prefix lookup
// when no state is selected; when a state is selected, narrows the
// already-loaded state/county list locally.
const zipFilter = ref('')
let zipFilterTimer: ReturnType<typeof setTimeout> | null = null
watch(zipFilter, (newVal) => {
  if (selectedStateIds.value.length > 0) return // state context: local filter only
  if (zipFilterTimer) clearTimeout(zipFilterTimer)
  const trimmed = newVal.trim()
  if (trimmed === '') {
    zipcodeOptions.value = []
    return
  }
  zipFilterTimer = setTimeout(() => loadZipcodesByPrefix(trimmed), 200)
})

const displayedZipcodes = computed<CountyZipRow[]>(() => {
  const prefix = zipFilter.value.trim()
  if (prefix === '') return zipcodeOptions.value
  return zipcodeOptions.value.filter(z => z.zipcode.startsWith(prefix))
})

async function onStateChange() {
  selectedCountyId.value = ''
  selectedZipcodes.value = []
  lastClickedZipIndex.value = null
  // Drop any leftover typeahead text. Otherwise it would keep filtering
  // the state-set dropdown — e.g. typed "982" + picked Arizona = empty.
  zipFilter.value = ''
  counties.value = []
  zipcodeOptions.value = []
  if (selectedStateIds.value.length > 0) {
    await loadCounties(selectedStateIds.value)
    // Populate zips across all chosen states; user can narrow further
    // with a county or by ticking specific zips.
    await loadZipcodesByState(selectedStateIds.value)
  }
}
async function onCountyChange() {
  selectedZipcodes.value = []
  lastClickedZipIndex.value = null
  if (selectedCountyId.value !== '') {
    await loadZipcodesByCounty(selectedCountyId.value)
  } else if (selectedStateIds.value.length > 0) {
    // County reset back to "Any" — fall back to the state-set list.
    await loadZipcodesByState(selectedStateIds.value)
  } else {
    zipcodeOptions.value = []
  }
}

function onZipKeydown(e: KeyboardEvent) {
  // Shift+8 produces '*', Shift+0 produces ')'. Match by key character
  // and also fall back to code-based detection for non-US layouts.
  const isSelectAll = e.key === '*' || (e.shiftKey && e.code === 'Digit8')
  const isDeselectAll = e.key === ')' || (e.shiftKey && e.code === 'Digit0')
  if (isSelectAll) {
    e.preventDefault()
    // Operate on whatever's visible to the user (respects any active
    // typeahead filter).
    selectedZipcodes.value = displayedZipcodes.value.map(z => z.zipcode)
    lastClickedZipIndex.value = null
  } else if (isDeselectAll) {
    e.preventDefault()
    selectedZipcodes.value = []
    lastClickedZipIndex.value = null
  }
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onEsc)
  loadSuggestions()
  loadStates()
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onEsc)
})

const filters = reactive({
  title: '',
  candidateName: '',
  type: '',
  includeClosed: false
})

// Zipcode picker: user checks one or more zipcodes from the county's
// list. Shift+click toggles a range from the last single click.
const selectedZipcodes = ref<string[]>([])
const lastClickedZipIndex = ref<number | null>(null)
const zipPickerOpen = ref(false)

const zipPickerSummary = computed<string>(() => {
  if (selectedZipcodes.value.length === 1) return selectedZipcodes.value[0]
  if (selectedZipcodes.value.length > 1) {
    return t('search.filters.zipcodeNSelected', { n: selectedZipcodes.value.length })
  }
  return displayedZipcodes.value[0]?.zipcode ?? ''
})

// State + County picker open state (same dropdown-trigger UX as the
// zipcode picker; State is multi-select with shift-click + */Shift+0,
// County stays single-select).
const statePickerOpen = ref(false)
const countyPickerOpen = ref(false)
const statePickerSummary = computed<string>(() => {
  if (selectedStateIds.value.length === 0) return t('search.filters.stateAny')
  if (selectedStateIds.value.length === 1) {
    return states.value.find(s => s.id === selectedStateIds.value[0])?.name
      ?? t('search.filters.stateAny')
  }
  return t('search.filters.stateNSelected', { n: selectedStateIds.value.length })
})
const selectedCountyLabel = computed(() => {
  if (selectedCountyId.value === '') return t('search.filters.countyAny')
  return counties.value.find(c => c.id === selectedCountyId.value)?.name
    ?? t('search.filters.countyAny')
})

function onStateClick(e: MouseEvent, idx: number, id: number) {
  const target = e.target as HTMLInputElement
  const willBeChecked = target.checked
  if (e.shiftKey && lastClickedStateIndex.value !== null) {
    const a = lastClickedStateIndex.value
    const b = idx
    const [start, end] = a < b ? [a, b] : [b, a]
    const rangeIds = states.value.slice(start, end + 1).map(s => s.id)
    if (willBeChecked) {
      const merged = new Set([...selectedStateIds.value, ...rangeIds])
      selectedStateIds.value = Array.from(merged)
    } else {
      const remove = new Set(rangeIds)
      selectedStateIds.value = selectedStateIds.value.filter(x => !remove.has(x))
    }
  } else {
    if (willBeChecked) {
      if (!selectedStateIds.value.includes(id)) {
        selectedStateIds.value = [...selectedStateIds.value, id]
      }
    } else {
      selectedStateIds.value = selectedStateIds.value.filter(x => x !== id)
    }
  }
  lastClickedStateIndex.value = idx
  onStateChange()
}
function onStateKeydown(e: KeyboardEvent) {
  const isSelectAll = e.key === '*' || (e.shiftKey && e.code === 'Digit8')
  const isDeselectAll = e.key === ')' || (e.shiftKey && e.code === 'Digit0')
  if (isSelectAll) {
    e.preventDefault()
    selectedStateIds.value = states.value.map(s => s.id)
    lastClickedStateIndex.value = null
    onStateChange()
  } else if (isDeselectAll) {
    e.preventDefault()
    selectedStateIds.value = []
    lastClickedStateIndex.value = null
    onStateChange()
  }
}
function pickCounty(id: number | '') {
  selectedCountyId.value = id
  countyPickerOpen.value = false
  onCountyChange()
}

function onZipClick(e: MouseEvent, idx: number, code: string) {
  const target = e.target as HTMLInputElement
  const willBeChecked = target.checked
  if (e.shiftKey && lastClickedZipIndex.value !== null) {
    const a = lastClickedZipIndex.value
    const b = idx
    const [start, end] = a < b ? [a, b] : [b, a]
    const rangeCodes = displayedZipcodes.value.slice(start, end + 1).map(z => z.zipcode)
    if (willBeChecked) {
      const merged = new Set([...selectedZipcodes.value, ...rangeCodes])
      selectedZipcodes.value = Array.from(merged)
    } else {
      const remove = new Set(rangeCodes)
      selectedZipcodes.value = selectedZipcodes.value.filter(z => !remove.has(z))
    }
  } else {
    if (willBeChecked) {
      if (!selectedZipcodes.value.includes(code)) {
        selectedZipcodes.value = [...selectedZipcodes.value, code]
      }
    } else {
      selectedZipcodes.value = selectedZipcodes.value.filter(z => z !== code)
    }
  }
  lastClickedZipIndex.value = idx
}

function isClosed(r: PollSearchResult): boolean {
  return !!r.closeDate && new Date(r.closeDate).getTime() <= Date.now()
}

const results = ref<PollSearchResult[]>([])
const loading = ref(false)
const searched = ref(false)
const error = ref<string | null>(null)

function routeMap(type: string): string {
  switch (type) {
    case 'Questionnaire': return 'questionnaire'
    case 'Election': return 'election'
    case 'BallotMeasure': return 'ballot-measure'
    default: return type.toLowerCase()
  }
}

async function search() {
  loading.value = true
  error.value = null
  try {
    const params: Record<string, string> = {}
    if (filters.title.trim()) params.title = filters.title.trim()
    if (selectedStateIds.value.length === 0) {
      // Typeahead mode: no state picked. The text the user typed is
      // the single zipcode they're searching for.
      const v = zipFilter.value.trim()
      if (v) params.zipcode = v
    } else if (selectedZipcodes.value.length > 0) {
      // States + explicit zip picks → those zips.
      params.zipcode = selectedZipcodes.value.join(',')
    } else if (selectedCountyId.value !== '') {
      // States + county, no picks → any zip in this county.
      params.countyId = String(selectedCountyId.value)
    } else {
      // States only, no picks → any zip in those states.
      params.stateId = selectedStateIds.value.join(',')
    }
    if (filters.candidateName.trim()) params.candidateName = filters.candidateName.trim()
    if (filters.type) params.type = filters.type
    if (filters.includeClosed) params.includeClosed = 'true'
    const res = await axios.get<PollSearchResult[]>('/api/polls/search', { params })
    results.value = res.data
    searched.value = true
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('search.errorFailed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="mx-auto max-w-5xl py-8">
    <h1 class="mb-2 text-2xl font-semibold text-slate-800">
      {{ auth.isAuthenticated ? $t('search.headingAuthed') : $t('search.headingGuest') }}
    </h1>
    <p v-if="!auth.isAuthenticated" class="mb-6 text-sm text-slate-600">
      {{ $t('search.guestIntroBefore') }}
      <router-link to="/login" class="text-slate-800 underline">{{ $t('search.guestIntroSignIn') }}</router-link>
      {{ $t('search.guestIntroAfter') }}
    </p>

    <form
      @submit.prevent="search"
      class="mb-6 grid grid-cols-1 items-end gap-3 rounded-md bg-slate-50 p-4 sm:grid-cols-[repeat(auto-fit,minmax(180px,1fr))]"
    >
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        {{ $t('search.filters.titleContains') }}
        <input
          v-model="filters.title"
          type="text"
          list="title-suggestions"
          autocomplete="off"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        />
        <datalist id="title-suggestions">
          <option v-for="s in suggestions.titles" :key="s" :value="s" />
        </datalist>
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        <span :title="$t('search.filters.stateHelp')" class="cursor-help">
          {{ $t('search.filters.state') }}
        </span>
        <div data-state-picker class="relative">
          <button
            type="button"
            @click.stop="statePickerOpen = !statePickerOpen"
            :aria-expanded="statePickerOpen"
            class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50 focus:border-slate-500 focus:outline-none"
          >
            <span>{{ statePickerSummary }}</span>
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 20 20"
              class="ml-2 h-4 w-4 text-slate-500 transition-transform"
              :class="{ 'rotate-180': statePickerOpen }"
              aria-hidden="true"
            ><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
          </button>
          <div
            v-if="statePickerOpen"
            tabindex="0"
            @keydown="onStateKeydown"
            class="absolute left-0 right-0 z-20 mt-1 max-h-48 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg focus:outline-none focus:ring-1 focus:ring-slate-400"
          >
            <label
              v-for="(s, idx) in states"
              :key="s.id"
              class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50"
            >
              <input
                type="checkbox"
                :checked="selectedStateIds.includes(s.id)"
                @click="onStateClick($event, idx, s.id)"
                class="h-3.5 w-3.5"
              />
              <span>{{ s.name }}</span>
            </label>
          </div>
        </div>
        <span
          v-if="statePickerOpen && states.length > 1"
          class="text-xs font-normal text-slate-500"
        >{{ $t('search.filters.zipcodeShiftHint') }}</span>
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        {{ $t('search.filters.county') }}
        <div data-county-picker class="relative">
          <button
            type="button"
            @click.stop="countyPickerOpen = selectedStateIds.length > 0 ? !countyPickerOpen : false"
            :disabled="selectedStateIds.length === 0"
            :aria-expanded="countyPickerOpen"
            class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50 focus:border-slate-500 focus:outline-none disabled:bg-slate-100 disabled:text-slate-400 disabled:hover:bg-slate-100"
          >
            <span>{{ selectedCountyLabel }}</span>
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 20 20"
              class="ml-2 h-4 w-4 text-slate-500 transition-transform"
              :class="{ 'rotate-180': countyPickerOpen }"
              aria-hidden="true"
            ><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
          </button>
          <div
            v-if="countyPickerOpen"
            class="absolute left-0 right-0 z-20 mt-1 max-h-48 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg"
          >
            <button
              type="button"
              @click="pickCounty('')"
              :class="['flex w-full items-center rounded px-2 py-1 text-left text-xs hover:bg-slate-50', selectedCountyId === '' ? 'bg-slate-100 font-semibold' : '']"
            >{{ $t('search.filters.countyAny') }}</button>
            <button
              v-for="c in counties"
              :key="c.id"
              type="button"
              @click="pickCounty(c.id)"
              :class="['flex w-full items-center rounded px-2 py-1 text-left text-xs hover:bg-slate-50', selectedCountyId === c.id ? 'bg-slate-100 font-semibold' : '']"
            >{{ c.name }}</button>
          </div>
        </div>
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        <span :title="$t('search.filters.zipcodeHelp')" class="cursor-help">
          {{ $t('search.filters.zipcode') }}
        </span>

        <!-- Mode A: no state picked → typeahead input with native datalist
             of prefix matches. Single-zip search; multi-zip picking
             unlocks once any state is selected. -->
        <template v-if="selectedStateIds.length === 0">
          <input
            v-model="zipFilter"
            type="text"
            inputmode="numeric"
            maxlength="5"
            autocomplete="off"
            list="zip-typeahead-options"
            :placeholder="$t('search.filters.zipcodeTypeahead')"
            class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
          />
          <datalist id="zip-typeahead-options">
            <option v-for="z in displayedZipcodes" :key="z.id" :value="z.zipcode" />
          </datalist>
        </template>

        <!-- Mode B: a state is selected → pre-populated dropdown of zips
             in that state (or in the picked county). Click to open;
             checkboxes for multi-select. -->
        <template v-else>
          <div
            v-if="displayedZipcodes.length === 0"
            class="rounded border border-slate-300 bg-slate-50 p-2 text-xs font-normal text-slate-500"
          >{{ $t('search.filters.zipcodeNone') }}</div>
          <div v-else data-zip-picker class="relative">
            <button
              type="button"
              @click.stop="zipPickerOpen = !zipPickerOpen"
              :aria-expanded="zipPickerOpen"
              class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50 focus:border-slate-500 focus:outline-none"
            >
              <span class="font-mono">{{ zipPickerSummary }}</span>
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 20 20"
                class="ml-2 h-4 w-4 text-slate-500 transition-transform"
                :class="{ 'rotate-180': zipPickerOpen }"
                aria-hidden="true"
              >
                <path
                  fill="currentColor"
                  d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z"
                />
              </svg>
            </button>
            <div
              v-if="zipPickerOpen"
              tabindex="0"
              @keydown="onZipKeydown"
              class="absolute left-0 right-0 z-20 mt-1 max-h-32 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg focus:outline-none focus:ring-1 focus:ring-slate-400"
            >
              <label
                v-for="(z, idx) in displayedZipcodes"
                :key="z.id"
                class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50"
              >
                <input
                  type="checkbox"
                  :checked="selectedZipcodes.includes(z.zipcode)"
                  @click="onZipClick($event, idx, z.zipcode)"
                  class="h-3.5 w-3.5"
                />
                <span class="font-mono">{{ z.zipcode }}</span>
              </label>
            </div>
          </div>
          <span
            v-if="displayedZipcodes.length > 1 && zipPickerOpen"
            class="text-xs font-normal text-slate-500"
          >{{ $t('search.filters.zipcodeShiftHint') }}</span>
        </template>
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        {{ $t('search.filters.candidateName') }}
        <input
          v-model="filters.candidateName"
          type="text"
          list="candidate-suggestions"
          autocomplete="off"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        />
        <datalist id="candidate-suggestions">
          <option v-for="s in suggestions.candidates" :key="s" :value="s" />
        </datalist>
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        {{ $t('search.filters.type') }}
        <select
          v-model="filters.type"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        >
          <option value="">{{ $t('search.filters.typeAny') }}</option>
          <option value="Questionnaire">{{ $t('search.filters.typeQuestionnaire') }}</option>
          <option value="Election">{{ $t('search.filters.typeElection') }}</option>
          <option value="BallotMeasure">{{ $t('search.filters.typeBallotMeasure') }}</option>
        </select>
      </label>
      <label class="flex items-center gap-2 text-xs font-semibold text-slate-700">
        <input
          v-model="filters.includeClosed"
          type="checkbox"
          class="h-4 w-4 rounded border-slate-300 text-slate-800 focus:ring-slate-500"
        />
        {{ $t('search.filters.includeClosed') }}
      </label>
      <button
        type="submit"
        :disabled="loading"
        class="h-fit rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ loading ? $t('search.searching') : $t('search.search') }}
      </button>
    </form>

    <p v-if="error" class="mb-2 text-sm text-red-700">{{ error }}</p>

    <p v-if="searched && results.length === 0" class="text-sm text-slate-500">
      {{ $t('search.noMatches') }}
    </p>

    <table v-if="results.length > 0" class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('search.table.title') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('search.table.type') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('search.table.zipcodes') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('search.table.closes') }}</th>
          <th class="border-b border-slate-200 p-2"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in results" :key="`${r.type}-${r.id}`" :class="isClosed(r) ? 'text-slate-500' : ''">
          <td class="border-b border-slate-100 p-2">
            {{ r.title }}
            <span
              v-if="isClosed(r)"
              class="ml-2 inline-block rounded bg-slate-200 px-1.5 py-0.5 text-xs font-semibold text-slate-700"
            >{{ $t('search.closedBadge') }}</span>
          </td>
          <td class="border-b border-slate-100 p-2">{{ r.type }}</td>
          <td class="border-b border-slate-100 p-2 font-mono text-xs">
            <template v-if="r.zipcodes.length === 0">—</template>
            <template v-else-if="r.zipcodes.length === 1">
              {{ r.zipcodes[0].code }} <span class="text-slate-500">({{ r.zipcodes[0].state }})</span>
            </template>
            <template v-else>
              <span class="relative inline-block">
                {{ r.zipcodes[0].code }}
                <span class="text-slate-500">({{ r.zipcodes[0].state }})</span>,
                <button
                  type="button"
                  data-zip-trigger
                  @click="toggleExpand(r, $event)"
                  class="ml-0.5 rounded px-1 font-semibold text-slate-800 hover:bg-slate-100 focus:outline-none focus:ring focus:ring-slate-400"
                  :title="t('search.showMore', { n: r.zipcodes.length - 1 })"
                  :aria-expanded="expandedKey === rowKey(r)"
                >…</button>
                <span class="ml-0.5 text-slate-500">+{{ r.zipcodes.length - 1 }}</span>

                <!-- Anchored popover; closes on outside-click or Esc -->
                <div
                  v-if="expandedKey === rowKey(r)"
                  data-zip-popover
                  role="dialog"
                  class="absolute left-0 top-full z-20 mt-1 w-56 rounded-md border border-slate-300 bg-white shadow-lg"
                >
                  <header class="flex items-center justify-between border-b border-slate-200 px-3 py-2">
                    <span class="text-xs font-semibold text-slate-700">
                      {{ $t('search.additionalZipcodes', { n: r.zipcodes.length - 1 }) }}
                    </span>
                    <button
                      type="button"
                      @click.stop="closeExpanded"
                      class="rounded p-0.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus:outline-none focus:ring focus:ring-slate-400"
                      :aria-label="$t('common.close')"
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        viewBox="0 0 20 20"
                        class="h-4 w-4"
                        aria-hidden="true"
                      >
                        <path
                          fill="currentColor"
                          d="M5.7 4.3 4.3 5.7 8.6 10l-4.3 4.3 1.4 1.4L10 11.4l4.3 4.3 1.4-1.4L11.4 10l4.3-4.3-1.4-1.4L10 8.6 5.7 4.3z"
                        />
                      </svg>
                    </button>
                  </header>
                  <ul
                    class="m-0 list-none overflow-y-auto p-0"
                    :style="{ maxHeight: '33vh' }"
                  >
                    <li
                      v-for="z in r.zipcodes.slice(1)"
                      :key="z.code"
                      class="flex justify-between border-b border-slate-100 px-3 py-1.5 last:border-b-0"
                    >
                      <span class="font-mono">{{ z.code }}</span>
                      <span class="text-slate-500">{{ z.state }}</span>
                    </li>
                  </ul>
                </div>
              </span>
            </template>
          </td>
          <td class="border-b border-slate-100 p-2">
            {{ r.closeDate ? new Date(r.closeDate).toLocaleString() : $t('search.noCloseDate') }}
          </td>
          <td class="border-b border-slate-100 p-2">
            <div class="flex gap-3 whitespace-nowrap">
              <router-link
                :to="`/polls/${routeMap(r.type)}/${r.id}/results`"
                class="text-slate-800 underline"
              >
                {{ $t('search.viewResults') }}
              </router-link>
              <router-link
                v-if="auth.isAuthenticated && !isClosed(r)"
                :to="`/polls/${routeMap(r.type)}/${r.id}`"
                class="text-slate-800 underline"
              >
                {{ $t('search.vote') }}
              </router-link>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
