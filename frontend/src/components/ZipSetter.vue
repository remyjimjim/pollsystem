<script setup lang="ts">
import { computed, ref, watch, watchEffect } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import type { State, County, CountyZip } from '@/types'

const { t } = useI18n()

const props = defineProps<{
  modelValue: string[]
  total?: number
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', zipcodes: string[]): void
  (e: 'update:total', total: number): void
}>()

const states = ref<State[]>([])
const counties = ref<County[]>([])
const zips = ref<CountyZip[]>([])

const selectedStateIds = ref<number[]>([])
const selectedCountyIds = ref<number[]>([])
const selectedZipcodes = ref<string[]>([...props.modelValue])

const loadingStates = ref(false)
const loadingCounties = ref(false)
const loadingZips = ref(false)
const error = ref<string | null>(null)

// Alphanumeric prefix filters (local). States/counties/zips are pre-loaded
// for the current scope; we narrow the visible list in-memory.
const stateFilter = ref('')
const countyFilter = ref('')
const zipFilter = ref('')

async function loadStates() {
  loadingStates.value = true
  error.value = null
  try {
    const res = await axios.get<State[]>('/api/states')
    states.value = res.data
  } catch {
    error.value = t('zipSetter.loadStatesFailed')
  } finally {
    loadingStates.value = false
  }
}

async function loadCounties(stateIds: number[]) {
  loadingCounties.value = true
  error.value = null
  counties.value = []
  zips.value = []
  selectedCountyIds.value = []
  countyFilter.value = ''
  zipFilter.value = ''
  if (stateIds.length === 0) {
    loadingCounties.value = false
    return
  }
  try {
    // Backend GeographyController binds state_id as List<Long>, so a
    // comma-separated value covers the multi-state case in one round-trip.
    const res = await axios.get<County[]>('/api/counties', {
      params: { state_id: stateIds.join(',') }
    })
    counties.value = res.data
  } catch {
    error.value = t('zipSetter.loadCountiesFailed')
  } finally {
    loadingCounties.value = false
  }
}

async function loadZips(countyIds: number[]) {
  if (countyIds.length === 0) {
    zips.value = []
    zipFilter.value = ''
    return
  }
  loadingZips.value = true
  error.value = null
  zipFilter.value = ''
  try {
    const res = await axios.get<CountyZip[]>('/api/zipcodes', {
      params: { county_ids: countyIds.join(',') }
    })
    zips.value = res.data
    selectedZipcodes.value = selectedZipcodes.value.filter(z =>
      res.data.some(cz => cz.zipcode === z)
    )
  } catch {
    error.value = t('zipSetter.loadZipcodesFailed')
  } finally {
    loadingZips.value = false
  }
}

const displayedStates = computed(() => {
  const prefix = stateFilter.value.trim().toLowerCase()
  if (prefix === '') return states.value
  return states.value.filter(s =>
    s.name.toLowerCase().startsWith(prefix) ||
    s.initial.toLowerCase().startsWith(prefix)
  )
})

const displayedCounties = computed(() => {
  const prefix = countyFilter.value.trim().toLowerCase()
  if (prefix === '') return counties.value
  return counties.value.filter(c => c.name.toLowerCase().startsWith(prefix))
})

const displayedZips = computed(() => {
  const prefix = zipFilter.value.trim()
  if (prefix === '') return zips.value
  return zips.value.filter(z => z.zipcode.startsWith(prefix))
})

// Select-all toggles act on the currently VISIBLE list (post-filter)
// so a typed prefix scopes what gets checked / unchecked. Selections
// outside the filter window are preserved across toggles.
const selectAllStatesRef = ref<HTMLInputElement | null>(null)
const allStatesSelected = computed(() =>
  displayedStates.value.length > 0 &&
  displayedStates.value.every(s => selectedStateIds.value.includes(s.id))
)
const someStatesSelected = computed(() => {
  const visible = displayedStates.value
  if (visible.length === 0) return false
  const n = visible.filter(s => selectedStateIds.value.includes(s.id)).length
  return n > 0 && n < visible.length
})
watchEffect(() => {
  if (selectAllStatesRef.value) {
    selectAllStatesRef.value.indeterminate = someStatesSelected.value
  }
})
function toggleAllStates() {
  const visibleIds = displayedStates.value.map(s => s.id)
  if (allStatesSelected.value) {
    const drop = new Set(visibleIds)
    selectedStateIds.value = selectedStateIds.value.filter(id => !drop.has(id))
  } else {
    selectedStateIds.value = Array.from(
      new Set([...selectedStateIds.value, ...visibleIds])
    )
  }
}

const selectAllCountiesRef = ref<HTMLInputElement | null>(null)
const allCountiesSelected = computed(() =>
  displayedCounties.value.length > 0 &&
  displayedCounties.value.every(c => selectedCountyIds.value.includes(c.id))
)
const someCountiesSelected = computed(() => {
  const visible = displayedCounties.value
  if (visible.length === 0) return false
  const n = visible.filter(c => selectedCountyIds.value.includes(c.id)).length
  return n > 0 && n < visible.length
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
    selectedCountyIds.value = Array.from(
      new Set([...selectedCountyIds.value, ...visibleIds])
    )
  }
}

const selectAllZipsRef = ref<HTMLInputElement | null>(null)
const allZipcodesSelected = computed(() =>
  displayedZips.value.length > 0 &&
  displayedZips.value.every(z => selectedZipcodes.value.includes(z.zipcode))
)
const someZipsSelected = computed(() => {
  const visible = displayedZips.value
  if (visible.length === 0) return false
  const n = visible.filter(z => selectedZipcodes.value.includes(z.zipcode)).length
  return n > 0 && n < visible.length
})
watchEffect(() => {
  if (selectAllZipsRef.value) {
    selectAllZipsRef.value.indeterminate = someZipsSelected.value
  }
})

function toggleAllZipcodes() {
  const visibleCodes = displayedZips.value.map(z => z.zipcode)
  if (allZipcodesSelected.value) {
    const drop = new Set(visibleCodes)
    selectedZipcodes.value = selectedZipcodes.value.filter(z => !drop.has(z))
  } else {
    selectedZipcodes.value = Array.from(
      new Set([...selectedZipcodes.value, ...visibleCodes])
    )
  }
}

// Section-level keydown shortcuts (bound on each <details>, so they fire
// no matter which child has focus — input, checkbox, or summary):
//   Enter / Escape          → clear the filter, return to the full list
//                             (preserves every checkbox change just made)
//   Ctrl/Cmd-A    or Shift-*→ add every visible item to the selection
//   Ctrl/Cmd-Shift-A or Sh-)→ remove every visible item from the selection
// Selections are additive/subtractive against the existing set, so a typed
// prefix never wipes choices made outside the current filter window. Enter
// stops propagation as well as preventing default — the component embeds
// inside <form> elements (creator/admin request) where implicit submission
// would otherwise fire on Enter.
function onStateFilterKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    stateFilter.value = ''
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
    const visibleIds = displayedStates.value.map(s => s.id)
    selectedStateIds.value = Array.from(
      new Set([...selectedStateIds.value, ...visibleIds])
    )
  } else if (isDeselectAll) {
    e.preventDefault()
    const drop = new Set(displayedStates.value.map(s => s.id))
    selectedStateIds.value = selectedStateIds.value.filter(id => !drop.has(id))
  }
}
function onCountyFilterKeydown(e: KeyboardEvent) {
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
  } else if (isDeselectAll) {
    e.preventDefault()
    const drop = new Set(displayedCounties.value.map(c => c.id))
    selectedCountyIds.value = selectedCountyIds.value.filter(id => !drop.has(id))
  }
}
function onZipFilterKeydown(e: KeyboardEvent) {
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
    const visibleCodes = displayedZips.value.map(z => z.zipcode)
    selectedZipcodes.value = Array.from(
      new Set([...selectedZipcodes.value, ...visibleCodes])
    )
  } else if (isDeselectAll) {
    e.preventDefault()
    const drop = new Set(displayedZips.value.map(z => z.zipcode))
    selectedZipcodes.value = selectedZipcodes.value.filter(z => !drop.has(z))
  }
}

watch(selectedStateIds, (ids) => {
  loadCounties(ids)
}, { deep: true })

watch(selectedCountyIds, (ids) => {
  loadZips(ids)
}, { deep: true })

watch(selectedZipcodes, (z) => {
  emit('update:modelValue', [...z])
}, { deep: true })

// Expose how many zipcodes are currently available in the visible list
// so parents that summarize the selection know when "All" applies.
watch(zips, (list) => {
  emit('update:total', list.length)
}, { deep: true, immediate: true })

loadStates()
</script>

<template>
  <div data-component="zipsetter" class="flex flex-col gap-4">
    <details
      open
      @keydown="onStateFilterKeydown"
      class="group rounded-md border border-slate-200 [&_summary::-webkit-details-marker]:hidden"
    >
      <summary
        class="flex cursor-pointer list-none items-center justify-between gap-3 p-3"
      >
        <div class="flex items-center gap-2">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 20 20"
            class="h-4 w-4 text-slate-500 transition-transform group-open:rotate-180"
            aria-hidden="true"
          >
            <path fill="currentColor" d="M5.25 7.5 10 12.25 14.75 7.5z" />
          </svg>
          <span class="text-sm font-semibold text-slate-700">
            {{ $t('zipSetter.state') }}<template v-if="!loadingStates && states.length > 0">
              <span class="ml-1 text-xs font-normal text-slate-500">
                {{ $t('zipSetter.selectedCount', { selected: selectedStateIds.length, total: states.length }) }}
              </span>
            </template>
          </span>
        </div>
        <label
          v-if="!loadingStates && states.length > 0"
          class="flex items-center gap-2 text-xs text-slate-600"
          @click.stop
        >
          <input
            ref="selectAllStatesRef"
            type="checkbox"
            :checked="allStatesSelected"
            @change="toggleAllStates"
          />
          {{ $t('zipSetter.selectAll', { total: displayedStates.length }) }}
        </label>
      </summary>
      <div class="flex flex-col gap-2 border-t border-slate-200 p-3">
        <p v-if="loadingStates" class="m-0 text-sm text-slate-500">{{ $t('common.loading') }}</p>
        <p v-else-if="states.length === 0" class="m-0 text-sm text-slate-500">
          {{ $t('zipSetter.selectState') }}
        </p>
        <template v-else>
          <input
            v-model="stateFilter"
            type="text"
            autocomplete="off"
            :placeholder="$t('search.filters.countyFilter')"
            class="rounded border border-slate-300 p-1.5 text-sm focus:border-slate-500 focus:outline-none"
          />
          <p
            v-if="displayedStates.length === 0"
            class="m-0 text-sm text-slate-500"
          >{{ $t('search.filters.countyNoMatches') }}</p>
          <div
            v-else
            class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))]"
          >
            <label v-for="s in displayedStates" :key="s.id" class="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" :value="s.id" v-model="selectedStateIds" />
              {{ s.name }} ({{ s.initial }})
            </label>
          </div>
        </template>
      </div>
    </details>

    <details
      v-if="selectedStateIds.length > 0"
      open
      @keydown="onCountyFilterKeydown"
      class="group rounded-md border border-slate-200 [&_summary::-webkit-details-marker]:hidden"
    >
      <summary
        class="flex cursor-pointer list-none items-center justify-between gap-3 p-3"
      >
        <div class="flex items-center gap-2">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 20 20"
            class="h-4 w-4 text-slate-500 transition-transform group-open:rotate-180"
            aria-hidden="true"
          >
            <path fill="currentColor" d="M5.25 7.5 10 12.25 14.75 7.5z" />
          </svg>
          <span class="text-sm font-semibold text-slate-700">
            {{ $t('zipSetter.counties') }}<template v-if="!loadingCounties && counties.length > 0">
              <span class="ml-1 text-xs font-normal text-slate-500">
                {{ $t('zipSetter.selectedCount', { selected: selectedCountyIds.length, total: counties.length }) }}
              </span>
            </template>
          </span>
        </div>
        <label
          v-if="!loadingCounties && counties.length > 0"
          class="flex items-center gap-2 text-xs text-slate-600"
          @click.stop
        >
          <input
            ref="selectAllCountiesRef"
            type="checkbox"
            :checked="allCountiesSelected"
            @change="toggleAllCounties"
          />
          {{ $t('zipSetter.selectAll', { total: displayedCounties.length }) }}
        </label>
      </summary>
      <div class="flex flex-col gap-2 border-t border-slate-200 p-3">
        <p v-if="loadingCounties" class="m-0 text-sm text-slate-500">{{ $t('common.loading') }}</p>
        <div
          v-else-if="counties.length === 0"
          class="rounded-md border border-orange-400 bg-orange-50 p-3 text-sm text-orange-900"
        >
          {{ $t('zipSetter.noCountiesSeeded') }}
        </div>
        <template v-else>
          <input
            v-model="countyFilter"
            type="text"
            autocomplete="off"
            :placeholder="$t('search.filters.countyFilter')"
            class="rounded border border-slate-300 p-1.5 text-sm focus:border-slate-500 focus:outline-none"
          />
          <p
            v-if="displayedCounties.length === 0"
            class="m-0 text-sm text-slate-500"
          >{{ $t('search.filters.countyNoMatches') }}</p>
          <div
            v-else
            class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))]"
          >
            <label v-for="c in displayedCounties" :key="c.id" class="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" :value="c.id" v-model="selectedCountyIds" />
              {{ c.name }}
            </label>
          </div>
        </template>
      </div>
    </details>

    <details
      v-if="selectedCountyIds.length > 0"
      open
      @keydown="onZipFilterKeydown"
      class="group rounded-md border border-slate-200 [&_summary::-webkit-details-marker]:hidden"
    >
      <summary
        class="flex cursor-pointer list-none items-center justify-between gap-3 p-3"
      >
        <div class="flex items-center gap-2">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 20 20"
            class="h-4 w-4 text-slate-500 transition-transform group-open:rotate-180"
            aria-hidden="true"
          >
            <path fill="currentColor" d="M5.25 7.5 10 12.25 14.75 7.5z" />
          </svg>
          <span class="text-sm font-semibold text-slate-700">
            {{ $t('zipSetter.zipcodes') }}<template v-if="!loadingZips && zips.length > 0">
              <span class="ml-1 text-xs font-normal text-slate-500">
                {{ $t('zipSetter.selectedCount', { selected: selectedZipcodes.length, total: zips.length }) }}
              </span>
            </template>
          </span>
        </div>
        <label
          v-if="!loadingZips && zips.length > 0"
          class="flex items-center gap-2 text-xs text-slate-600"
          @click.stop
        >
          <input
            ref="selectAllZipsRef"
            type="checkbox"
            :checked="allZipcodesSelected"
            @change="toggleAllZipcodes"
          />
          {{ $t('zipSetter.selectAll', { total: displayedZips.length }) }}
        </label>
      </summary>
      <div class="flex flex-col gap-2 border-t border-slate-200 p-3">
        <p v-if="loadingZips" class="m-0 text-sm text-slate-500">{{ $t('common.loading') }}</p>
        <p v-else-if="zips.length === 0" class="m-0 text-sm text-slate-500">
          {{ $t('zipSetter.noZipcodesAvailable') }}
        </p>
        <template v-else>
          <input
            v-model="zipFilter"
            type="text"
            inputmode="numeric"
            maxlength="5"
            autocomplete="off"
            :placeholder="$t('search.filters.countyFilter')"
            class="rounded border border-slate-300 p-1.5 text-sm font-mono focus:border-slate-500 focus:outline-none"
          />
          <p
            v-if="displayedZips.length === 0"
            class="m-0 text-sm text-slate-500"
          >{{ $t('search.filters.zipcodeNone') }}</p>
          <div
            v-else
            class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))]"
          >
            <label v-for="z in displayedZips" :key="z.id" class="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" :value="z.zipcode" v-model="selectedZipcodes" />
              {{ z.zipcode }}
            </label>
          </div>
        </template>
      </div>
    </details>

    <p v-if="error" class="m-0 text-sm text-red-700">{{ error }}</p>
  </div>
</template>
