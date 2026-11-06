<script setup lang="ts">
// A dropdown of checkboxes (2026-11-06, Manage Polls' Status and Poll type
// filters): a button showing "Label: summary" opens a panel of options, in
// optional titled groups; clicking outside or Escape closes it. v-model is the
// list of ticked values. The panel stays in the page while closed (v-show), so
// its checkboxes keep their labels for tests and screen readers.
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

interface Option { value: string; label: string }
interface Group { title?: string; options: Option[] }

const props = defineProps<{ label: string; groups: Group[]; testId?: string }>()
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
  <div ref="root" class="relative inline-block text-sm" :data-test="testId">
    <button
      type="button"
      class="flex w-full items-center justify-between gap-2 rounded border border-slate-300 bg-white px-3 py-1.5 text-left font-normal text-slate-800 hover:bg-slate-50"
      :aria-expanded="open"
      :data-test="testId ? `${testId}-button` : undefined"
      @click="open = !open"
    >
      <span><span class="font-semibold">{{ label }}:</span> {{ summary }}</span>
      <span aria-hidden="true" class="text-xs">▾</span>
    </button>
    <div
      v-show="open"
      class="absolute left-0 z-30 mt-1 min-w-full rounded border border-slate-300 bg-white p-2 shadow-lg"
      role="group"
      :aria-label="label"
    >
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
