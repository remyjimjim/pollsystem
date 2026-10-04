<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

// A small trigger (an "i" info icon, a map pin, or custom #trigger content)
// with a popover that opens on hover, keyboard focus, AND tap/click, so it
// works with a mouse, a keyboard and a touchscreen alike. Esc or a click
// outside closes it. The popover content goes in the default slot.
const props = withDefaults(
  defineProps<{
    /** Accessible name for the trigger button. */
    label: string
    icon?: 'info' | 'pin'
    /** Popover alignment relative to the trigger. */
    align?: 'left' | 'right'
    widthClass?: string
  }>(),
  { icon: 'info', align: 'left', widthClass: 'w-72' },
)

const open = ref(false)
const pinned = ref(false) // opened by click/tap: stays open until toggled/closed
const root = ref<HTMLElement | null>(null)
let hoverTimer: ReturnType<typeof setTimeout> | null = null

function show() { if (hoverTimer) clearTimeout(hoverTimer); open.value = true }
function hideSoon() {
  if (pinned.value) return
  if (hoverTimer) clearTimeout(hoverTimer)
  hoverTimer = setTimeout(() => { open.value = false }, 120)
}
function toggle() {
  pinned.value = !pinned.value
  open.value = pinned.value
}
function close() { open.value = false; pinned.value = false }

function onDocClick(e: MouseEvent) {
  if (open.value && root.value && !root.value.contains(e.target as Node)) close()
}
function onKey(e: KeyboardEvent) { if (e.key === 'Escape' && open.value) close() }
document.addEventListener('click', onDocClick)
document.addEventListener('keydown', onKey)
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKey)
  if (hoverTimer) clearTimeout(hoverTimer)
})
</script>

<template>
  <span ref="root" class="relative inline-flex align-middle" @mouseenter="show" @mouseleave="hideSoon">
    <button
      type="button"
      :aria-label="props.label"
      :aria-expanded="open"
      class="inline-flex items-center gap-1 rounded text-slate-500 hover:text-slate-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-400"
      @click.stop="toggle"
      @focus="show"
      @blur="hideSoon"
    >
      <slot name="trigger">
        <svg v-if="props.icon === 'info'" viewBox="0 0 20 20" fill="currentColor" class="h-4 w-4" aria-hidden="true">
          <path fill-rule="evenodd" d="M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Zm-7-4a1 1 0 1 1-2 0 1 1 0 0 1 2 0ZM9 9a1 1 0 0 0 0 2v3a1 1 0 0 0 1 1h1a1 1 0 1 0 0-2v-3a1 1 0 0 0-1-1H9Z" clip-rule="evenodd" />
        </svg>
        <svg v-else viewBox="0 0 20 20" fill="currentColor" class="h-4 w-4" aria-hidden="true">
          <path fill-rule="evenodd" d="M9.69 18.933l.003.001C9.89 19.02 10 19 10 19s.11.02.308-.066l.002-.001.006-.003.018-.008a5.741 5.741 0 0 0 .281-.14c.186-.096.446-.24.757-.433.62-.384 1.445-.966 2.274-1.765C15.302 14.988 17 12.493 17 9A7 7 0 1 0 3 9c0 3.492 1.698 5.988 3.355 7.584a13.731 13.731 0 0 0 2.273 1.765 11.842 11.842 0 0 0 .976.544l.062.029.018.008.006.003ZM10 11.25a2.25 2.25 0 1 0 0-4.5 2.25 2.25 0 0 0 0 4.5Z" clip-rule="evenodd" />
        </svg>
      </slot>
    </button>
    <div
      v-if="open"
      role="tooltip"
      :class="[
        'absolute top-full z-30 mt-1 rounded border border-slate-200 bg-white p-3 text-left text-xs font-normal normal-case text-slate-700 shadow-lg',
        props.widthClass,
        props.align === 'right' ? 'right-0' : 'left-0',
      ]"
      @click.stop
    >
      <slot />
    </div>
  </span>
</template>
