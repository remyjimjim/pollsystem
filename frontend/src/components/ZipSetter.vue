<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import type { State, County, CountyZip } from '@/types'

const { t } = useI18n()

const props = defineProps<{
  modelValue: string[]
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', zipcodes: string[]): void
}>()

const states = ref<State[]>([])
const counties = ref<County[]>([])
const zips = ref<CountyZip[]>([])

const selectedStateId = ref<number | null>(null)
const selectedCountyIds = ref<number[]>([])
const selectedZipcodes = ref<string[]>([...props.modelValue])

const loadingStates = ref(false)
const loadingCounties = ref(false)
const loadingZips = ref(false)
const error = ref<string | null>(null)

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

async function loadCounties(stateId: number) {
  loadingCounties.value = true
  error.value = null
  counties.value = []
  zips.value = []
  selectedCountyIds.value = []
  try {
    const res = await axios.get<County[]>('/api/counties', {
      params: { state_id: stateId }
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
    return
  }
  loadingZips.value = true
  error.value = null
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

function onStateChange(e: Event) {
  const raw = (e.target as HTMLSelectElement).value
  selectedStateId.value = raw === '' ? null : Number(raw)
}

const allCountiesSelected = computed(() =>
  counties.value.length > 0 &&
  selectedCountyIds.value.length === counties.value.length
)

function toggleAllCounties() {
  selectedCountyIds.value = allCountiesSelected.value
    ? []
    : counties.value.map(c => c.id)
}

const allZipcodesSelected = computed(() =>
  zips.value.length > 0 &&
  selectedZipcodes.value.length === zips.value.length &&
  zips.value.every(z => selectedZipcodes.value.includes(z.zipcode))
)

function toggleAllZipcodes() {
  selectedZipcodes.value = allZipcodesSelected.value
    ? []
    : zips.value.map(z => z.zipcode)
}

watch(selectedStateId, (id) => {
  if (id != null) loadCounties(id)
})

watch(selectedCountyIds, (ids) => {
  loadZips(ids)
}, { deep: true })

watch(selectedZipcodes, (z) => {
  emit('update:modelValue', [...z])
}, { deep: true })

loadStates()
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex flex-col gap-2">
      <label class="text-sm font-semibold text-slate-700">{{ $t('zipSetter.state') }}</label>
      <select
        :value="selectedStateId ?? ''"
        @change="onStateChange"
        :disabled="loadingStates"
        class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none disabled:opacity-60"
      >
        <option value="" disabled>
          {{ loadingStates ? $t('common.loading') : $t('zipSetter.selectState') }}
        </option>
        <option v-for="s in states" :key="s.id" :value="s.id">
          {{ s.name }} ({{ s.initial }})
        </option>
      </select>
    </div>

    <details
      v-if="selectedStateId != null"
      open
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
            type="checkbox"
            :checked="allCountiesSelected"
            @change="toggleAllCounties"
          />
          {{ $t('zipSetter.selectAll', { total: counties.length }) }}
        </label>
      </summary>
      <div class="border-t border-slate-200 p-3">
        <p v-if="loadingCounties" class="m-0 text-sm text-slate-500">{{ $t('common.loading') }}</p>
        <div
          v-else-if="counties.length === 0"
          class="rounded-md border border-orange-400 bg-orange-50 p-3 text-sm text-orange-900"
        >
          {{ $t('zipSetter.noCountiesSeeded') }}
        </div>
        <div
          v-else
          class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))]"
        >
          <label v-for="c in counties" :key="c.id" class="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" :value="c.id" v-model="selectedCountyIds" />
            {{ c.name }}
          </label>
        </div>
      </div>
    </details>

    <details
      v-if="selectedCountyIds.length > 0"
      open
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
            type="checkbox"
            :checked="allZipcodesSelected"
            @change="toggleAllZipcodes"
          />
          {{ $t('zipSetter.selectAll', { total: zips.length }) }}
        </label>
      </summary>
      <div class="border-t border-slate-200 p-3">
        <p v-if="loadingZips" class="m-0 text-sm text-slate-500">{{ $t('common.loading') }}</p>
        <p v-else-if="zips.length === 0" class="m-0 text-sm text-slate-500">
          {{ $t('zipSetter.noZipcodesAvailable') }}
        </p>
        <div
          v-else
          class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))]"
        >
          <label v-for="z in zips" :key="z.id" class="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" :value="z.zipcode" v-model="selectedZipcodes" />
            {{ z.zipcode }}
          </label>
        </div>
      </div>
    </details>

    <p v-if="error" class="m-0 text-sm text-red-700">{{ error }}</p>
  </div>
</template>
