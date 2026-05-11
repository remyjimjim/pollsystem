<script setup lang="ts">
import { ref, watch } from 'vue'
import axios from 'axios'
import type { State, County, CountyZip } from '@/types'

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
    error.value = 'Failed to load states'
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
    error.value = 'Failed to load counties'
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
    error.value = 'Failed to load zipcodes'
  } finally {
    loadingZips.value = false
  }
}

function onStateChange(e: Event) {
  const raw = (e.target as HTMLSelectElement).value
  selectedStateId.value = raw === '' ? null : Number(raw)
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
      <label class="text-sm font-semibold text-slate-700">State</label>
      <select
        :value="selectedStateId ?? ''"
        @change="onStateChange"
        :disabled="loadingStates"
        class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none disabled:opacity-60"
      >
        <option value="" disabled>
          {{ loadingStates ? 'Loading…' : 'Select a state' }}
        </option>
        <option v-for="s in states" :key="s.id" :value="s.id">
          {{ s.name }} ({{ s.initial }})
        </option>
      </select>
    </div>

    <div class="flex flex-col gap-2" v-if="selectedStateId != null">
      <label class="text-sm font-semibold text-slate-700">Counties</label>
      <p v-if="loadingCounties" class="m-0 text-sm text-slate-500">Loading…</p>
      <div
        v-else-if="counties.length === 0"
        class="rounded-md border border-orange-400 bg-orange-50 p-3 text-sm text-orange-900"
      >
        No counties seeded for this state yet. Pick a different state, or extend
        the seed data in <code class="font-mono">backend/src/main/resources/db/migration/</code>.
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

    <div class="flex flex-col gap-2" v-if="selectedCountyIds.length > 0">
      <label class="text-sm font-semibold text-slate-700">Zipcodes</label>
      <p v-if="loadingZips" class="m-0 text-sm text-slate-500">Loading…</p>
      <p v-else-if="zips.length === 0" class="m-0 text-sm text-slate-500">
        No zipcodes available
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

    <p v-if="error" class="m-0 text-sm text-red-700">{{ error }}</p>
  </div>
</template>
