<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, watchEffect } from 'vue'
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
const selectAllStatesRef = ref<HTMLInputElement | null>(null)

const allStatesSelected = computed(() =>
  states.value.length > 0 && selectedStateIds.value.length === states.value.length
)
const someStatesSelected = computed(() =>
  selectedStateIds.value.length > 0 && selectedStateIds.value.length < states.value.length
)
// Native checkbox indeterminate is a JS property — not bindable via :attr.
// watchEffect (rather than watch+immediate) so the indeterminate flag
// re-applies when the dropdown closes/reopens and the template ref
// gets re-mounted.
watchEffect(() => {
  if (selectAllStatesRef.value) {
    selectAllStatesRef.value.indeterminate = someStatesSelected.value
  }
})

function toggleAllStates() {
  selectedStateIds.value = allStatesSelected.value
    ? []
    : states.value.map(s => s.id)
  lastClickedStateIndex.value = null
  onStateChange()
}
const selectedCountyIds = ref<number[]>([])
const lastClickedCountyIndex = ref<number | null>(null)

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
async function loadZipcodesByCounty(countyIds: number[]) {
  try {
    const res = await axios.get<CountyZipRow[]>('/api/zipcodes', {
      params: { county_ids: countyIds.join(',') }
    })
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

// User-typed county-search input. Drives a debounced prefix lookup
// when no state is selected; when a state is selected, narrows the
// already-loaded county list locally.
const countyFilter = ref('')
let countyFilterTimer: ReturnType<typeof setTimeout> | null = null
async function loadCountiesByPrefix(prefix: string) {
  try {
    const res = await axios.get<CountyRow[]>('/api/counties', { params: { prefix } })
    counties.value = res.data
  } catch {
    counties.value = []
  }
}
watch(countyFilter, (newVal) => {
  if (selectedStateIds.value.length > 0) return // state context: local filter only
  if (countyFilterTimer) clearTimeout(countyFilterTimer)
  const trimmed = newVal.trim()
  if (trimmed === '') {
    counties.value = []
    return
  }
  countyFilterTimer = setTimeout(() => loadCountiesByPrefix(trimmed), 200)
})
const displayedCounties = computed<CountyRow[]>(() => {
  // When a state is selected, counties are pre-loaded and we filter
  // locally. With no state the list comes from prefix-search already.
  if (selectedStateIds.value.length === 0) return counties.value
  const prefix = countyFilter.value.trim().toLowerCase()
  if (prefix === '') return counties.value
  return counties.value.filter(c => c.name.toLowerCase().startsWith(prefix))
})

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

// Select-all wiring for counties and zips. Both target the currently
// VISIBLE list (post-filter), so the toggle only adds/removes what the
// user can see. Existing selections outside the visible window are
// preserved when collapsing the filter.
const selectAllCountiesRef = ref<HTMLInputElement | null>(null)
const allCountiesSelected = computed(() =>
  displayedCounties.value.length > 0 &&
  displayedCounties.value.every(c => selectedCountyIds.value.includes(c.id))
)
const someCountiesSelected = computed(() => {
  const visible = displayedCounties.value
  if (visible.length === 0) return false
  const checked = visible.filter(c => selectedCountyIds.value.includes(c.id)).length
  return checked > 0 && checked < visible.length
})
watchEffect(() => {
  if (selectAllCountiesRef.value) {
    selectAllCountiesRef.value.indeterminate = someCountiesSelected.value
  }
})
function toggleAllCounties() {
  const visibleIds = displayedCounties.value.map(c => c.id)
  if (allCountiesSelected.value) {
    const drop = new Set(visibleIds)
    selectedCountyIds.value = selectedCountyIds.value.filter(id => !drop.has(id))
  } else {
    selectedCountyIds.value = Array.from(new Set([...selectedCountyIds.value, ...visibleIds]))
  }
  lastClickedCountyIndex.value = null
  onCountyChange()
}

const selectAllZipsRef = ref<HTMLInputElement | null>(null)
const allZipsSelected = computed(() =>
  displayedZipcodes.value.length > 0 &&
  displayedZipcodes.value.every(z => selectedZipcodes.value.includes(z.zipcode))
)
const someZipsSelected = computed(() => {
  const visible = displayedZipcodes.value
  if (visible.length === 0) return false
  const checked = visible.filter(z => selectedZipcodes.value.includes(z.zipcode)).length
  return checked > 0 && checked < visible.length
})
watchEffect(() => {
  if (selectAllZipsRef.value) {
    selectAllZipsRef.value.indeterminate = someZipsSelected.value
  }
})
function toggleAllZips() {
  const visibleCodes = displayedZipcodes.value.map(z => z.zipcode)
  if (allZipsSelected.value) {
    const drop = new Set(visibleCodes)
    selectedZipcodes.value = selectedZipcodes.value.filter(z => !drop.has(z))
  } else {
    selectedZipcodes.value = Array.from(new Set([...selectedZipcodes.value, ...visibleCodes]))
  }
  lastClickedZipIndex.value = null
}

async function onStateChange() {
  selectedCountyIds.value = []
  lastClickedCountyIndex.value = null
  selectedZipcodes.value = []
  lastClickedZipIndex.value = null
  // Drop any leftover typeahead text. Otherwise it would keep filtering
  // the state-set dropdown — e.g. typed "982" + picked Arizona = empty.
  zipFilter.value = ''
  countyFilter.value = ''
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
  if (selectedCountyIds.value.length > 0) {
    await loadZipcodesByCounty(selectedCountyIds.value)
  } else if (selectedStateIds.value.length > 0) {
    // No counties ticked — fall back to the full state-set zip list.
    await loadZipcodesByState(selectedStateIds.value)
  } else {
    zipcodeOptions.value = []
  }
}

function onZipKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    zipFilter.value = ''
    return
  }
  const ctrlOrCmd = e.ctrlKey || e.metaKey
  const isA = e.key.toLowerCase() === 'a'
  const isSelectAll = e.key === '*'
    || (e.shiftKey && e.code === 'Digit8')
    || (ctrlOrCmd && !e.shiftKey && isA)
  const isDeselectAll = e.key === ')'
    || (e.shiftKey && e.code === 'Digit0')
    || (ctrlOrCmd && e.shiftKey && isA)
  if (isSelectAll) {
    e.preventDefault()
    const visibleCodes = displayedZipcodes.value.map(z => z.zipcode)
    selectedZipcodes.value = Array.from(
      new Set([...selectedZipcodes.value, ...visibleCodes])
    )
    lastClickedZipIndex.value = null
  } else if (isDeselectAll) {
    e.preventDefault()
    const drop = new Set(displayedZipcodes.value.map(z => z.zipcode))
    selectedZipcodes.value = selectedZipcodes.value.filter(z => !drop.has(z))
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

// Candidates only exist on Elections, so the candidate-name field is
// only relevant when the type filter is empty (Any) or 'Election'.
const candidateFilterApplicable = computed(
  () => filters.type === '' || filters.type === 'Election'
)
// Clear a stale candidateName the moment the user switches the type
// filter to something candidate-less, so it doesn't get tacked onto
// the next search request.
watch(() => filters.type, () => {
  if (!candidateFilterApplicable.value) filters.candidateName = ''
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
const countyPickerSummary = computed<string>(() => {
  if (selectedCountyIds.value.length === 0) return t('search.filters.countyAny')
  if (selectedCountyIds.value.length === 1) {
    return counties.value.find(c => c.id === selectedCountyIds.value[0])?.name
      ?? t('search.filters.countyAny')
  }
  return t('search.filters.countyNSelected', { n: selectedCountyIds.value.length })
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
  // State picker has no filter input — Enter/Escape just dismiss the
  // dropdown rather than acting on a filter string.
  if (e.key === 'Enter' || e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    statePickerOpen.value = false
    return
  }
  const ctrlOrCmd = e.ctrlKey || e.metaKey
  const isA = e.key.toLowerCase() === 'a'
  const isSelectAll = e.key === '*'
    || (e.shiftKey && e.code === 'Digit8')
    || (ctrlOrCmd && !e.shiftKey && isA)
  const isDeselectAll = e.key === ')'
    || (e.shiftKey && e.code === 'Digit0')
    || (ctrlOrCmd && e.shiftKey && isA)
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
function onCountyClick(e: MouseEvent, idx: number, id: number) {
  const target = e.target as HTMLInputElement
  const willBeChecked = target.checked
  if (e.shiftKey && lastClickedCountyIndex.value !== null) {
    const a = lastClickedCountyIndex.value
    const b = idx
    const [start, end] = a < b ? [a, b] : [b, a]
    const rangeIds = displayedCounties.value.slice(start, end + 1).map(c => c.id)
    if (willBeChecked) {
      const merged = new Set([...selectedCountyIds.value, ...rangeIds])
      selectedCountyIds.value = Array.from(merged)
    } else {
      const remove = new Set(rangeIds)
      selectedCountyIds.value = selectedCountyIds.value.filter(x => !remove.has(x))
    }
  } else {
    if (willBeChecked) {
      if (!selectedCountyIds.value.includes(id)) {
        selectedCountyIds.value = [...selectedCountyIds.value, id]
      }
    } else {
      selectedCountyIds.value = selectedCountyIds.value.filter(x => x !== id)
    }
  }
  lastClickedCountyIndex.value = idx
  onCountyChange()
}
function onCountyKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    countyFilter.value = ''
    return
  }
  const ctrlOrCmd = e.ctrlKey || e.metaKey
  const isA = e.key.toLowerCase() === 'a'
  const isSelectAll = e.key === '*'
    || (e.shiftKey && e.code === 'Digit8')
    || (ctrlOrCmd && !e.shiftKey && isA)
  const isDeselectAll = e.key === ')'
    || (e.shiftKey && e.code === 'Digit0')
    || (ctrlOrCmd && e.shiftKey && isA)
  if (isSelectAll) {
    e.preventDefault()
    const visibleIds = displayedCounties.value.map(c => c.id)
    selectedCountyIds.value = Array.from(
      new Set([...selectedCountyIds.value, ...visibleIds])
    )
    lastClickedCountyIndex.value = null
    onCountyChange()
  } else if (isDeselectAll) {
    e.preventDefault()
    const drop = new Set(displayedCounties.value.map(c => c.id))
    selectedCountyIds.value = selectedCountyIds.value.filter(id => !drop.has(id))
    lastClickedCountyIndex.value = null
    onCountyChange()
  }
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

// ---------- column sorting (client-side, on top of the backend's
// active-first ordering — picking a column overrides that order) ----------
type SortKey = 'title' | 'type' | 'zipcode' | 'closeDate'
const sortKey = ref<SortKey | null>(null)
const sortDir = ref<'asc' | 'desc'>('asc')
function toggleSort(k: SortKey) {
  if (sortKey.value === k) sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  else { sortKey.value = k; sortDir.value = 'asc' }
}
function sortValue(r: PollSearchResult, k: SortKey): string {
  switch (k) {
    case 'title': return r.title.toLowerCase()
    case 'type': return r.type
    // The zipcodes payload is already sorted by code from the backend,
    // so taking the first item gives a stable alphanumeric anchor.
    case 'zipcode': return r.zipcodes[0]?.code ?? ''
    // ISO timestamps are lex-sortable. Null close-date = "never closes",
    // sorts after every real date in ascending order.
    case 'closeDate': return r.closeDate ?? '￿'
  }
}
const sortedResults = computed<PollSearchResult[]>(() => {
  if (sortKey.value === null) return results.value
  const dir = sortDir.value === 'asc' ? 1 : -1
  const k = sortKey.value
  return results.value.slice().sort((a, b) => {
    const av = sortValue(a, k), bv = sortValue(b, k)
    if (av < bv) return -dir
    if (av > bv) return dir
    return 0
  })
})
function sortIndicator(k: SortKey): string {
  if (sortKey.value !== k) return ''
  return sortDir.value === 'asc' ? ' ▲' : ' ▼'
}

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
    } else if (selectedCountyIds.value.length > 0) {
      // States + counties, no zip picks → any zip in those counties.
      params.countyId = selectedCountyIds.value.join(',')
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
  <div data-component="poll-search-view" class="mx-auto max-w-5xl py-8">
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
              v-if="states.length > 0"
              class="sticky top-0 flex items-center gap-2 rounded border-b border-slate-200 bg-slate-50 px-2 py-1 text-xs font-semibold text-slate-700"
            >
              <input
                ref="selectAllStatesRef"
                type="checkbox"
                :checked="allStatesSelected"
                @change="toggleAllStates"
                class="h-3.5 w-3.5"
              />
              <span>{{ $t('zipSetter.selectAll', { total: states.length }) }}</span>
            </label>
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
        <span :title="$t('search.filters.countyHelp')" class="cursor-help">
          {{ $t('search.filters.county') }}
        </span>
        <div data-county-picker class="relative">
          <button
            type="button"
            @click.stop="countyPickerOpen = !countyPickerOpen"
            :aria-expanded="countyPickerOpen"
            class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50 focus:border-slate-500 focus:outline-none"
          >
            <span>{{ countyPickerSummary }}</span>
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
            tabindex="0"
            @keydown="onCountyKeydown"
            class="absolute left-0 right-0 z-20 mt-1 rounded border border-slate-300 bg-white text-sm font-normal text-slate-900 shadow-lg focus:outline-none focus:ring-1 focus:ring-slate-400"
          >
            <input
              v-model="countyFilter"
              type="text"
              autocomplete="off"
              :placeholder="selectedStateIds.length === 0 ? $t('search.filters.countyTypeahead') : $t('search.filters.countyFilter')"
              class="block w-full rounded-t border-b border-slate-300 p-2 text-xs font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
            />
            <div class="max-h-48 overflow-y-auto p-1">
              <div
                v-if="displayedCounties.length === 0"
                class="px-2 py-1 text-xs text-slate-500"
              >{{ selectedStateIds.length === 0 && countyFilter.trim() === '' ? $t('search.filters.countyStartHint') : $t('search.filters.countyNoMatches') }}</div>
              <label
                v-if="displayedCounties.length > 0"
                class="sticky top-0 z-10 flex items-center gap-2 border-b border-slate-200 bg-slate-50 px-2 py-1 text-xs font-semibold text-slate-700"
              >
                <input
                  ref="selectAllCountiesRef"
                  type="checkbox"
                  :checked="allCountiesSelected"
                  @change="toggleAllCounties"
                  class="h-3.5 w-3.5"
                />
                <span>{{ $t('zipSetter.selectAll', { total: displayedCounties.length }) }}</span>
              </label>
              <label
                v-for="(c, idx) in displayedCounties"
                :key="c.id"
                class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50"
              >
                <input
                  type="checkbox"
                  :checked="selectedCountyIds.includes(c.id)"
                  @click="onCountyClick($event, idx, c.id)"
                  class="h-3.5 w-3.5"
                />
                <span>{{ c.name }}</span>
              </label>
            </div>
          </div>
        </div>
        <span
          v-if="countyPickerOpen && displayedCounties.length > 1"
          class="text-xs font-normal text-slate-500"
        >{{ $t('search.filters.zipcodeShiftHint') }}</span>
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
              class="absolute left-0 right-0 z-20 mt-1 rounded border border-slate-300 bg-white text-sm font-normal text-slate-900 shadow-lg focus:outline-none focus:ring-1 focus:ring-slate-400"
            >
              <input
                v-model="zipFilter"
                type="text"
                inputmode="numeric"
                maxlength="5"
                autocomplete="off"
                :placeholder="$t('search.filters.countyFilter')"
                class="block w-full rounded-t border-b border-slate-300 p-2 text-xs font-mono text-slate-900 focus:border-slate-500 focus:outline-none"
              />
              <div class="max-h-32 overflow-y-auto p-1">
                <div
                  v-if="displayedZipcodes.length === 0"
                  class="px-2 py-1 text-xs text-slate-500"
                >{{ $t('search.filters.zipcodeNone') }}</div>
                <label
                  v-if="displayedZipcodes.length > 0"
                  class="sticky top-0 z-10 flex items-center gap-2 border-b border-slate-200 bg-slate-50 px-2 py-1 text-xs font-semibold text-slate-700"
                >
                  <input
                    ref="selectAllZipsRef"
                    type="checkbox"
                    :checked="allZipsSelected"
                    @change="toggleAllZips"
                    class="h-3.5 w-3.5"
                  />
                  <span>{{ $t('zipSetter.selectAll', { total: displayedZipcodes.length }) }}</span>
                </label>
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
          </div>
          <span
            v-if="displayedZipcodes.length > 1 && zipPickerOpen"
            class="text-xs font-normal text-slate-500"
          >{{ $t('search.filters.zipcodeShiftHint') }}</span>
        </template>
      </label>
      <label
        v-if="candidateFilterApplicable"
        class="flex flex-col gap-1 text-xs font-semibold text-slate-700"
      >
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
          <th @click="toggleSort('title')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('search.table.title') }}{{ sortIndicator('title') }}</th>
          <th @click="toggleSort('type')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('search.table.type') }}{{ sortIndicator('type') }}</th>
          <th @click="toggleSort('zipcode')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('search.table.zipcodes') }}{{ sortIndicator('zipcode') }}</th>
          <th @click="toggleSort('closeDate')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('search.table.closes') }}{{ sortIndicator('closeDate') }}</th>
          <th class="border-b border-slate-200 p-2"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in sortedResults" :key="`${r.type}-${r.id}`" :class="isClosed(r) ? 'text-slate-500' : ''">
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
