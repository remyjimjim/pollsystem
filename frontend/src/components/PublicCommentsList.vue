<script setup lang="ts">
// Public comments on one result item, anonymous. Folded by default so long
// lists don't push the results apart.
defineProps<{ comments: string[] }>()
</script>

<template>
  <details v-if="comments.length" class="mt-2 text-sm" data-test="public-comments">
    <summary class="cursor-pointer text-slate-600 hover:text-slate-800">
      {{ $t('comments.publicCount', { n: comments.length }, comments.length) }}
    </summary>
    <ul class="m-0 mt-1 list-none p-0">
      <li
        v-for="(c, i) in comments"
        :key="i"
        class="border-l-2 border-slate-200 py-1 pl-2 whitespace-pre-wrap text-slate-700"
      >{{ c }}</li>
    </ul>
  </details>
</template>
