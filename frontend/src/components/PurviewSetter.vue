<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useGeoPicker } from '@/composables/useGeoPicker'
import ZipSetter from '@/components/ZipSetter.vue'
import { ScopeLevel, type Purview } from '@/types'

// Lets a creator express their requested purview at a scope LEVEL — nationwide,
// whole state(s), whole county(ies), or specific zipcodes — and emits
// { scopeLevel, regionIds, zipcodes }. One useGeoPicker instance drives the
// STATE/COUNTY selections; the shared ZipSetter (its own instance) handles the
// ZIP cascade so we don't reimplement it.
const { t } = useI18n()
const emit = defineEmits<{ (e: 'update:modelValue', v: Purview): void }>()

const level = ref<ScopeLevel>(ScopeLevel.STATE)
const zipModel = ref<string[]>([])
const { states, counties, error } = useGeoPicker()

const LEVELS: { value: ScopeLevel; label: string }[] = [
  { value: ScopeLevel.NATIONAL, label: t('purview.national') },
  { value: ScopeLevel.STATE, label: t('purview.state') },
  { value: ScopeLevel.COUNTY, label: t('purview.county') },
  { value: ScopeLevel.ZIP, label: t('purview.zip') },
]

// Emit whenever the level or any relevant selection changes.
watch(
  [level, states.selected, counties.selected, zipModel],
  () => {
    const l = level.value
    emit('update:modelValue', {
      scopeLevel: l,
      regionIds:
        l === ScopeLevel.STATE ? [...states.selected.value]
        : l === ScopeLevel.COUNTY ? [...counties.selected.value]
        : [],
      zipcodes: l === ScopeLevel.ZIP ? [...zipModel.value] : [],
    })
  },
  { deep: true, immediate: true },
)
</script>

<template>
  <div data-component="purview-setter" class="flex flex-col gap-4">
    <fieldset class="flex flex-wrap gap-x-5 gap-y-2">
      <label v-for="opt in LEVELS" :key="opt.value" class="flex items-center gap-2 text-sm text-slate-700">
        <input type="radio" name="scopeLevel" :value="opt.value" v-model="level" />
        {{ opt.label }}
      </label>
    </fieldset>

    <p
      v-if="level === ScopeLevel.NATIONAL"
      class="m-0 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700"
    >
      {{ $t('purview.nationalNote') }}
    </p>

    <!-- States (for STATE and, as the county narrower, for COUNTY) -->
    <div
      v-if="level === ScopeLevel.STATE || level === ScopeLevel.COUNTY"
      class="rounded-md border border-slate-200 p-3"
    >
      <div class="mb-2 flex items-center justify-between gap-2">
        <span class="text-sm font-semibold text-slate-700">
          {{ $t('zipSetter.state') }}
          <span class="ml-1 text-xs font-normal text-slate-500">
            {{ $t('zipSetter.selectedCount', { selected: states.selected.value.length, total: states.items.value.length }) }}
          </span>
        </span>
        <label v-if="states.items.value.length" class="flex items-center gap-2 text-xs text-slate-600">
          <input
            :ref="el => states.selectAllRef.value = el as HTMLInputElement | null"
            type="checkbox"
            :checked="states.allSelected.value"
            @change="states.toggleAll"
          />
          {{ $t('zipSetter.selectAll', { total: states.displayed.value.length }) }}
        </label>
      </div>
      <input
        v-model="states.filter.value"
        @keydown="states.onFilterKeydown"
        type="text"
        autocomplete="off"
        :placeholder="$t('search.filters.countyFilter')"
        class="mb-2 w-full rounded border border-slate-300 p-1.5 text-sm focus:border-slate-500 focus:outline-none"
      />
      <p v-if="states.loading.value" class="m-0 text-sm text-slate-500">{{ $t('common.loading') }}</p>
      <p
        v-else-if="states.tooMany.value"
        class="m-0 rounded-md border border-orange-300 bg-orange-50 p-2 text-sm text-orange-900"
      >{{ $t('zipSetter.tooMany', { count: states.displayed.value.length }) }}</p>
      <div v-else class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))]">
        <label v-for="s in states.displayed.value" :key="s.id" class="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" :value="s.id" v-model="states.selected.value" />
          {{ s.name }} ({{ s.initial }})
        </label>
      </div>
    </div>

    <!-- Counties (COUNTY level, once states are chosen) -->
    <div
      v-if="level === ScopeLevel.COUNTY && states.selected.value.length"
      class="rounded-md border border-slate-200 p-3"
    >
      <div class="mb-2 flex items-center justify-between gap-2">
        <span class="text-sm font-semibold text-slate-700">
          {{ $t('zipSetter.counties') }}
          <span class="ml-1 text-xs font-normal text-slate-500">
            {{ $t('zipSetter.selectedCount', { selected: counties.selected.value.length, total: counties.items.value.length }) }}
          </span>
        </span>
        <label v-if="counties.items.value.length" class="flex items-center gap-2 text-xs text-slate-600">
          <input
            :ref="el => counties.selectAllRef.value = el as HTMLInputElement | null"
            type="checkbox"
            :checked="counties.allSelected.value"
            @change="counties.toggleAll"
          />
          {{ $t('zipSetter.selectAll', { total: counties.displayed.value.length }) }}
        </label>
      </div>
      <input
        v-model="counties.filter.value"
        @keydown="counties.onFilterKeydown"
        type="text"
        autocomplete="off"
        :placeholder="$t('search.filters.countyFilter')"
        class="mb-2 w-full rounded border border-slate-300 p-1.5 text-sm focus:border-slate-500 focus:outline-none"
      />
      <p v-if="counties.loading.value" class="m-0 text-sm text-slate-500">{{ $t('common.loading') }}</p>
      <p
        v-else-if="counties.tooMany.value"
        class="m-0 rounded-md border border-orange-300 bg-orange-50 p-2 text-sm text-orange-900"
      >{{ $t('zipSetter.tooMany', { count: counties.displayed.value.length }) }}</p>
      <div v-else class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))]">
        <label v-for="c in counties.displayed.value" :key="c.id" class="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" :value="c.id" v-model="counties.selected.value" />
          {{ c.name }}
        </label>
      </div>
    </div>

    <!-- Specific zipcodes: reuse the shared cascade picker -->
    <ZipSetter v-if="level === ScopeLevel.ZIP" v-model="zipModel" />

    <p v-if="error" class="m-0 text-sm text-red-700">{{ error }}</p>
  </div>
</template>
