<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
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
  if (target?.closest('[data-zip-popover]') || target?.closest('[data-zip-trigger]')) return
  expandedKey.value = null
}
function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') expandedKey.value = null
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
const selectedStateId = ref<number | ''>('')
const selectedCountyId = ref<number | ''>('')

async function loadStates() {
  try {
    const res = await axios.get<StateRow[]>('/api/states')
    states.value = res.data
  } catch {
    // Cascade is geography UX scaffolding; failures shouldn't block search.
  }
}
async function loadCounties(stateId: number) {
  try {
    const res = await axios.get<CountyRow[]>('/api/counties', { params: { state_id: stateId } })
    counties.value = res.data
  } catch {
    counties.value = []
  }
}
async function loadZipcodes(countyId: number) {
  try {
    const res = await axios.get<CountyZipRow[]>('/api/zipcodes', { params: { county_ids: countyId } })
    zipcodeOptions.value = res.data
  } catch {
    zipcodeOptions.value = []
  }
}

async function onStateChange() {
  selectedCountyId.value = ''
  selectedZipcodes.value = []
  lastClickedZipIndex.value = null
  counties.value = []
  zipcodeOptions.value = []
  if (selectedStateId.value !== '') await loadCounties(selectedStateId.value)
}
async function onCountyChange() {
  selectedZipcodes.value = []
  lastClickedZipIndex.value = null
  zipcodeOptions.value = []
  if (selectedCountyId.value !== '') await loadZipcodes(selectedCountyId.value)
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

function onZipClick(e: MouseEvent, idx: number, code: string) {
  const target = e.target as HTMLInputElement
  const willBeChecked = target.checked
  if (e.shiftKey && lastClickedZipIndex.value !== null) {
    const a = lastClickedZipIndex.value
    const b = idx
    const [start, end] = a < b ? [a, b] : [b, a]
    const rangeCodes = zipcodeOptions.value.slice(start, end + 1).map(z => z.zipcode)
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
    if (selectedZipcodes.value.length > 0) {
      // Spring binds a comma-separated string to List<String> just as
      // it does for the existing /api/zipcodes endpoint.
      params.zipcode = selectedZipcodes.value.join(',')
    } else if (selectedCountyId.value !== '') {
      // No specific zipcodes picked, but county is set: ask the backend
      // to treat that as "any zipcode in this county" rather than no
      // geo filter at all.
      params.countyId = String(selectedCountyId.value)
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
        {{ $t('search.filters.state') }}
        <select
          v-model="selectedStateId"
          @change="onStateChange"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        >
          <option value="">{{ $t('search.filters.stateAny') }}</option>
          <option v-for="s in states" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        {{ $t('search.filters.county') }}
        <select
          v-model="selectedCountyId"
          @change="onCountyChange"
          :disabled="selectedStateId === ''"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none disabled:bg-slate-100 disabled:text-slate-400"
        >
          <option value="">{{ $t('search.filters.countyAny') }}</option>
          <option v-for="c in counties" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        {{ $t('search.filters.zipcode') }}
        <div
          v-if="selectedCountyId === ''"
          class="rounded border border-slate-300 bg-slate-100 p-2 text-sm font-normal text-slate-400"
        >{{ $t('search.filters.zipcodePickCountyFirst') }}</div>
        <div
          v-else-if="zipcodeOptions.length === 0"
          class="rounded border border-slate-300 bg-slate-50 p-2 text-sm font-normal text-slate-500"
        >{{ $t('search.filters.zipcodeNone') }}</div>
        <div
          v-else
          class="max-h-32 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900"
        >
          <label
            v-for="(z, idx) in zipcodeOptions"
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
        <span
          v-if="zipcodeOptions.length > 1 && selectedCountyId !== ''"
          class="text-xs font-normal text-slate-500"
        >{{ $t('search.filters.zipcodeShiftHint') }}</span>
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
