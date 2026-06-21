<script setup lang="ts">
import { watch } from 'vue'
import { useGeoPicker } from '@/composables/useGeoPicker'

const props = defineProps<{
  modelValue: string[]
  total?: number
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', zipcodes: string[]): void
  (e: 'update:total', total: number): void
}>()

// Headless logic lives in useGeoPicker — fetches, selection state,
// filter logic, Select-all + indeterminate, and keystroke shortcuts. This
// shell binds the picker to ZipSetter's inline <details> presentation.
const { states, counties, zips, error } = useGeoPicker()

// Seed the zip selection from the incoming modelValue so a parent form
// resuming an existing draft pre-checks the right zipcodes.
zips.selected.value = [...props.modelValue]

watch(zips.selected, (z) => {
  emit('update:modelValue', [...z])
}, { deep: true })

// Tell the parent how many zips are currently visible so summaries
// ("All N selected") know when to apply.
watch(zips.items, (list) => {
  emit('update:total', list.length)
}, { deep: true, immediate: true })
</script>

<template>
  <div data-component="zipsetter" class="flex flex-col gap-4">
    <details
      open
      @keydown="states.onFilterKeydown"
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
            {{ $t('zipSetter.state') }}<template v-if="!states.loading.value && states.items.value.length > 0">
              <span class="ml-1 text-xs font-normal text-slate-500">
                {{ $t('zipSetter.selectedCount', { selected: states.selected.value.length, total: states.items.value.length }) }}
              </span>
            </template>
          </span>
        </div>
        <label
          v-if="!states.loading.value && states.items.value.length > 0"
          class="flex items-center gap-2 text-xs text-slate-600"
          @click.stop
        >
          <input
            :ref="el => states.selectAllRef.value = el as HTMLInputElement | null"
            type="checkbox"
            :checked="states.allSelected.value"
            @change="states.toggleAll"
          />
          {{ $t('zipSetter.selectAll', { total: states.displayed.value.length }) }}
        </label>
      </summary>
      <div class="flex flex-col gap-2 border-t border-slate-200 p-3">
        <p v-if="states.loading.value" class="m-0 text-sm text-slate-500">{{ $t('common.loading') }}</p>
        <p v-else-if="states.items.value.length === 0" class="m-0 text-sm text-slate-500">
          {{ $t('zipSetter.selectState') }}
        </p>
        <template v-else>
          <input
            v-model="states.filter.value"
            type="text"
            autocomplete="off"
            :placeholder="$t('search.filters.countyFilter')"
            class="rounded border border-slate-300 p-1.5 text-sm focus:border-slate-500 focus:outline-none"
          />
          <p
            v-if="states.displayed.value.length === 0"
            class="m-0 text-sm text-slate-500"
          >{{ $t('search.filters.countyNoMatches') }}</p>
          <div
            v-else
            class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))]"
          >
            <label v-for="s in states.displayed.value" :key="s.id" class="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" :value="s.id" v-model="states.selected.value" />
              {{ s.name }} ({{ s.initial }})
            </label>
          </div>
        </template>
      </div>
    </details>

    <details
      v-if="states.selected.value.length > 0"
      open
      @keydown="counties.onFilterKeydown"
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
            {{ $t('zipSetter.counties') }}<template v-if="!counties.loading.value && counties.items.value.length > 0">
              <span class="ml-1 text-xs font-normal text-slate-500">
                {{ $t('zipSetter.selectedCount', { selected: counties.selected.value.length, total: counties.items.value.length }) }}
              </span>
            </template>
          </span>
        </div>
        <label
          v-if="!counties.loading.value && counties.items.value.length > 0"
          class="flex items-center gap-2 text-xs text-slate-600"
          @click.stop
        >
          <input
            :ref="el => counties.selectAllRef.value = el as HTMLInputElement | null"
            type="checkbox"
            :checked="counties.allSelected.value"
            @change="counties.toggleAll"
          />
          {{ $t('zipSetter.selectAll', { total: counties.displayed.value.length }) }}
        </label>
      </summary>
      <div class="flex flex-col gap-2 border-t border-slate-200 p-3">
        <p v-if="counties.loading.value" class="m-0 text-sm text-slate-500">{{ $t('common.loading') }}</p>
        <div
          v-else-if="counties.items.value.length === 0"
          class="rounded-md border border-orange-400 bg-orange-50 p-3 text-sm text-orange-900"
        >
          {{ $t('zipSetter.noCountiesSeeded') }}
        </div>
        <template v-else>
          <input
            v-model="counties.filter.value"
            type="text"
            autocomplete="off"
            :placeholder="$t('search.filters.countyFilter')"
            class="rounded border border-slate-300 p-1.5 text-sm focus:border-slate-500 focus:outline-none"
          />
          <p
            v-if="counties.displayed.value.length === 0"
            class="m-0 text-sm text-slate-500"
          >{{ $t('search.filters.countyNoMatches') }}</p>
          <div
            v-else
            class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))]"
          >
            <label v-for="c in counties.displayed.value" :key="c.id" class="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" :value="c.id" v-model="counties.selected.value" />
              {{ c.name }}
            </label>
          </div>
        </template>
      </div>
    </details>

    <details
      v-if="counties.selected.value.length > 0"
      open
      @keydown="zips.onFilterKeydown"
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
            {{ $t('zipSetter.zipcodes') }}<template v-if="!zips.loading.value && zips.items.value.length > 0">
              <span class="ml-1 text-xs font-normal text-slate-500">
                {{ $t('zipSetter.selectedCount', { selected: zips.selected.value.length, total: zips.items.value.length }) }}
              </span>
            </template>
          </span>
        </div>
        <label
          v-if="!zips.loading.value && zips.items.value.length > 0"
          class="flex items-center gap-2 text-xs text-slate-600"
          @click.stop
        >
          <input
            :ref="el => zips.selectAllRef.value = el as HTMLInputElement | null"
            type="checkbox"
            :checked="zips.allSelected.value"
            @change="zips.toggleAll"
          />
          {{ $t('zipSetter.selectAll', { total: zips.displayed.value.length }) }}
        </label>
      </summary>
      <div class="flex flex-col gap-2 border-t border-slate-200 p-3">
        <p v-if="zips.loading.value" class="m-0 text-sm text-slate-500">{{ $t('common.loading') }}</p>
        <p v-else-if="zips.items.value.length === 0" class="m-0 text-sm text-slate-500">
          {{ $t('zipSetter.noZipcodesAvailable') }}
        </p>
        <template v-else>
          <input
            v-model="zips.filter.value"
            type="text"
            inputmode="numeric"
            maxlength="5"
            autocomplete="off"
            :placeholder="$t('search.filters.countyFilter')"
            class="rounded border border-slate-300 p-1.5 text-sm font-mono focus:border-slate-500 focus:outline-none"
          />
          <p
            v-if="zips.displayed.value.length === 0"
            class="m-0 text-sm text-slate-500"
          >{{ $t('search.filters.zipcodeNone') }}</p>
          <div
            v-else
            class="grid gap-1 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))]"
          >
            <label v-for="z in zips.displayed.value" :key="z.id" class="flex items-center gap-2 text-sm text-slate-700">
              <input type="checkbox" :value="z.zipcode" v-model="zips.selected.value" />
              {{ z.zipcode }}
            </label>
          </div>
        </template>
      </div>
    </details>

    <p v-if="error" class="m-0 text-sm text-red-700">{{ error }}</p>
  </div>
</template>
