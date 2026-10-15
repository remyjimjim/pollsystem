<script setup lang="ts">
import type { BadWord } from '../composables/useCommentModeration'

// "Can I replace X with Y?" for a public comment. Shown as a bar at the bottom,
// so the comment box being asked about stays focused and visible.
defineProps<{ asking: BadWord }>()
defineEmits<{ answer: [yes: boolean] }>()
</script>

<template>
  <div
    role="dialog"
    aria-labelledby="replace-word-title"
    data-test="replace-word-dialog"
    class="fixed inset-x-0 bottom-0 z-50 flex justify-center p-4"
  >
    <div class="w-full max-w-md rounded border border-slate-300 bg-white p-4 shadow-xl">
      <h2 id="replace-word-title" class="mb-1 text-base font-semibold text-slate-800">
        {{ $t('comments.replaceQuestion', { word: asking.word, replace: asking.replace }) }}
      </h2>
      <p class="mb-3 text-xs text-slate-500">{{ $t('comments.replaceHint') }}</p>
      <div class="flex justify-end gap-2">
        <button
          type="button"
          data-test="replace-no"
          class="rounded border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50"
          @click="$emit('answer', false)"
        >{{ $t('comments.replaceNo') }}</button>
        <button
          type="button"
          data-test="replace-yes"
          class="rounded bg-slate-800 px-3 py-1 text-sm text-white hover:bg-slate-900"
          @click="$emit('answer', true)"
        >{{ $t('comments.replaceYes') }}</button>
      </div>
    </div>
  </div>
</template>
