<script setup lang="ts">
// A dropdown of checkboxes (2026-11-06, Manage Polls' Status and Poll type
// filters): a button showing "Label: summary" opens a panel of options, in
// optional titled groups; clicking outside or Escape closes it. v-model is the
// list of ticked values. The panel stays in the page while closed (v-show), so
// its checkboxes keep their labels for tests and screen readers.
// labelAbove (2026-11-06): the label sits above the button, like the page's
// other fields, and the button shows only the summary. allOption: a first
// "All" checkbox that ticks or clears every option.
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

interface Option { value: string; label: string }
interface Group { title?: string; options: Option[] }

const props = defineProps<{ label: string; groups: Group[]; testId?: string; labelAbove?: boolean; allOption?: boolean }>()
const selected = defineModel<string[]>({ required: true })
const { t } = useI18n()

const open = ref(false)
const root = ref<HTMLElement | null>(null)
const all = computed(() => props.groups.flatMap(g => g.options))

/** "All", "None", the one or two ticked, or "3 selected". */
const summary = computed(() => {
  const ticked = all.value.filter(o => selected.value.includes(o.value))
  if (ticked.length === all.value.length) return t('multiSelect.all')
  if (ticked.length === 0) return t('multiSelect.none')
  if (ticked.length <= 2) return ticked.map(o => o.label).join(', ')
  return t('multiSelect.count', { n: ticked.length })
})

const allTicked = computed(() => all.value.every(o => selected.value.includes(o.value)))
const someTicked = computed(() => !allTicked.value && all.value.some(o => selected.value.includes(o.value)))
function toggleAll(on: boolean) { selected.value = on ? all.value.map(o => o.value) : [] }
const labelId = `msd-${Math.random().toString(36).slice(2, 9)}`

function toggle(value: string, on: boolean) {
  selected.value = on ? [...new Set([...selected.value, value])] : selected.value.filter(v => v !== value)
}
function onDocClick(e: MouseEvent) {
  if (open.value && root.value && !root.value.contains(e.target as Node)) open.value = false
}
function onKey(e: KeyboardEvent) { if (e.key === 'Escape') open.value = false }
onMounted(() => { document.addEventListener('click', onDocClick); document.addEventListener('keydown', onKey) })
onBeforeUnmount(() => { document.removeEventListener('click', onDocClick); document.removeEventListener('keydown', onKey) })
</script>

<template>
  <div ref="root" class="relative text-sm leading-5" :class="labelAbove ? 'flex flex-col gap-1' : 'inline-block'" :data-test="testId">
    <span v-if="labelAbove" :id="labelId" class="block font-semibold text-slate-700">{{ label }}</span>
    <button
      type="button"
      class="flex w-full items-center justify-between gap-2 rounded border border-slate-300 bg-white text-left font-normal text-slate-800 hover:bg-slate-50"
      :class="labelAbove ? 'p-2' : 'px-3 py-1.5'"
      :aria-expanded="open"
      :aria-labelledby="labelAbove ? labelId : undefined"
      :data-test="testId ? `${testId}-button` : undefined"
      @click="open = !open"
    >
      <span><span v-if="!labelAbove" class="font-semibold">{{ label }}: </span>{{ summary }}</span>
      <span aria-hidden="true" class="text-xs">▾</span>
    </button>
    <div
      v-show="open"
      class="absolute left-0 top-full z-30 mt-1 min-w-full rounded border border-slate-300 bg-white p-2 shadow-lg"
      role="group"
      :aria-label="label"
    >
      <label v-if="allOption" class="mb-1 flex cursor-pointer items-center gap-2 whitespace-nowrap border-b border-slate-100 pb-1 font-normal text-slate-800">
        <input
          type="checkbox"
          class="h-4 w-4"
          :checked="allTicked"
          :indeterminate="someTicked"
          :data-test="testId ? `${testId}-all` : undefined"
          @change="toggleAll(($event.target as HTMLInputElement).checked)"
        />
        {{ t('multiSelect.all') }}
      </label>
      <div v-for="(g, gi) in groups" :key="gi" :class="gi > 0 ? 'mt-2 border-t border-slate-100 pt-2' : ''">
        <div v-if="g.title" class="mb-1 text-xs font-semibold text-slate-500">{{ g.title }}</div>
        <label v-for="o in g.options" :key="o.value" class="flex cursor-pointer items-center gap-2 whitespace-nowrap py-0.5 font-normal text-slate-800">
          <input
            type="checkbox"
            class="h-4 w-4"
            :checked="selected.includes(o.value)"
            :data-test="testId ? `${testId}-${o.value.toLowerCase()}` : undefined"
            @change="toggle(o.value, ($event.target as HTMLInputElement).checked)"
          />
          {{ o.label }}
        </label>
      </div>
    </div>
  </div>
</template>
