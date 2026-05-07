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
    // drop selections that are no longer available
    selectedZipcodes.value = selectedZipcodes.value.filter(z =>
      res.data.some(cz => cz.zipcode === z)
    )
  } catch {
    error.value = 'Failed to load zipcodes'
  } finally {
    loadingZips.value = false
  }
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
  <div class="zip-setter">
    <div class="step">
      <label>State</label>
      <select v-model="selectedStateId" :disabled="loadingStates">
        <option :value="null" disabled>
          {{ loadingStates ? 'Loading…' : 'Select a state' }}
        </option>
        <option v-for="s in states" :key="s.id" :value="s.id">
          {{ s.name }} ({{ s.initial }})
        </option>
      </select>
    </div>

    <div class="step" v-if="selectedStateId != null">
      <label>Counties</label>
      <p v-if="loadingCounties" class="hint">Loading…</p>
      <p v-else-if="counties.length === 0" class="hint">No counties available</p>
      <div v-else class="checkbox-grid">
        <label v-for="c in counties" :key="c.id" class="check">
          <input type="checkbox" :value="c.id" v-model="selectedCountyIds" />
          {{ c.name }}
        </label>
      </div>
    </div>

    <div class="step" v-if="selectedCountyIds.length > 0">
      <label>Zipcodes</label>
      <p v-if="loadingZips" class="hint">Loading…</p>
      <p v-else-if="zips.length === 0" class="hint">No zipcodes available</p>
      <div v-else class="checkbox-grid">
        <label v-for="z in zips" :key="z.id" class="check">
          <input type="checkbox" :value="z.zipcode" v-model="selectedZipcodes" />
          {{ z.zipcode }}
        </label>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.zip-setter {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.step {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.step > label {
  font-weight: 600;
  font-size: 0.9rem;
  color: #2d3748;
}
select {
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font-size: 1rem;
}
.checkbox-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 0.4rem;
}
.check {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.9rem;
}
.hint {
  margin: 0;
  font-size: 0.85rem;
  color: #718096;
}
.error {
  color: #c53030;
  margin: 0;
}
</style>
