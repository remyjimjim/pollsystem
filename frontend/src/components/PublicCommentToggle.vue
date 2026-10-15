<script setup lang="ts">
// The "make comment public (and anonymous)" box under a comment input.
const model = defineModel<boolean>({ required: true })
defineProps<{ id: string }>()
</script>

<template>
  <label :for="id" class="mt-1 inline-flex items-center gap-1 text-xs text-slate-600" :title="$t('comments.makePublicHint')">
    <input :id="id" v-model="model" type="checkbox" class="h-3.5 w-3.5" data-test="comment-public" />
    {{ $t('comments.makePublic') }}
  </label>
</template>
