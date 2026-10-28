<script setup lang="ts">
// A long list's arrow row (2026-10-27, shared 2026-10-28): wraps the list, and
// while it runs past the screen a row pinned to the bottom has a round arrow.
// ↓ scrolls to the end of the list; once there it turns ↑, back to its top.
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps<{ endLabel?: string; topLabel?: string }>()

const box = ref<HTMLElement | null>(null)
const atEnd = ref(false)
const overflow = ref(false)
function update() {
  const el = box.value
  if (!el) { overflow.value = false; return }
  const r = el.getBoundingClientRect()
  overflow.value = r.height > window.innerHeight * 0.8 || r.bottom > window.innerHeight
  atEnd.value = r.bottom <= window.innerHeight + 8
}
function go() {
  const el = box.value
  if (!el) return
  if (atEnd.value) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  else window.scrollTo({ top: window.scrollY + el.getBoundingClientRect().bottom - window.innerHeight + 72, behavior: 'smooth' })
}
// New rows, sorting or columns change the list's size.
let observer: ResizeObserver | null = null
onMounted(() => {
  window.addEventListener('scroll', update, { passive: true })
  window.addEventListener('resize', update)
  if (typeof ResizeObserver !== 'undefined' && box.value) {
    observer = new ResizeObserver(update)
    observer.observe(box.value)
  }
  update()
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', update)
  window.removeEventListener('resize', update)
  observer?.disconnect()
})
</script>

<template>
  <div ref="box" class="scroll-mt-4">
    <slot />
  </div>
  <div
    v-if="overflow"
    class="sticky bottom-0 mt-2 flex justify-center border-t border-slate-200 bg-slate-50 py-2"
    data-test="results-scroll-row"
  >
    <button
      type="button"
      class="flex h-10 w-10 items-center justify-center rounded-full bg-slate-800 text-xl leading-none text-white shadow hover:bg-slate-700"
      :aria-label="atEnd ? (props.topLabel ?? $t('common.scrollToTop')) : (props.endLabel ?? $t('common.scrollToEnd'))"
      :title="atEnd ? (props.topLabel ?? $t('common.scrollToTop')) : (props.endLabel ?? $t('common.scrollToEnd'))"
      :data-direction="atEnd ? 'up' : 'down'"
      data-test="results-scroll-arrow"
      @click="go"
    >
      <span aria-hidden="true">{{ atEnd ? '↑' : '↓' }}</span>
    </button>
  </div>
</template>
