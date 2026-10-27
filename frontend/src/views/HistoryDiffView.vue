<script setup lang="ts">
// One History entry's Diff (2026-10-27): the poll at the previous snapshot
// (Before) beside the poll as of this entry (Now). Changed fields and the
// questions or candidates that differ are highlighted.
import { computed, ref, watch } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

interface Snapshot { title: string; summary: string | null; closeDate: string | null; items: string[]; area: string }
interface Diff {
  id: number
  type: 'questionnaire' | 'election' | 'ballot-measure'
  pollId: number
  pollTitle: string
  event: string
  at: string
  before: Snapshot
  now: Snapshot
}

const { t } = useI18n()
const route = useRoute()
const diff = ref<Diff | null>(null)
const error = ref<string | null>(null)

watch(() => route.params.id, async (id) => {
  diff.value = null
  error.value = null
  try {
    diff.value = (await axios.get<Diff>(`/api/poll-history/${id}/diff`)).data
  } catch (e: any) {
    error.value = e?.response?.status === 404 ? t('history.diff.notFound') : (e?.response?.data?.message ?? t('history.loadFailed'))
  }
}, { immediate: true })

const date = (d: string | null) => (d ? new Date(d).toLocaleString() : t('history.diff.none'))
const itemsLabel = computed(() => diff.value?.type === 'election' ? t('history.diff.candidates') : t('history.diff.questions'))
const fields = computed(() => {
  const d = diff.value
  if (!d) return []
  const rows = [
    { key: 'title', label: t('history.diff.title'), before: d.before.title, now: d.now.title },
    { key: 'summary', label: t('history.diff.summary'), before: d.before.summary ?? '', now: d.now.summary ?? '' },
    { key: 'closes', label: t('history.diff.closes'), before: date(d.before.closeDate), now: date(d.now.closeDate) },
    { key: 'area', label: t('history.diff.area'), before: d.before.area, now: d.now.area },
  ]
  // A ballot measure has no summary-less layout to hide; an election has no summary.
  return rows.filter(r => !(r.key === 'summary' && d.type === 'election'))
})
const showItems = computed(() => !!diff.value && diff.value.type !== 'ballot-measure')
const changedTint = 'rounded bg-amber-50 ring-1 ring-amber-300'
</script>

<template>
  <div class="mx-auto max-w-5xl py-8">
    <router-link
      :to="diff ? { path: '/history', query: { type: diff.type, id: diff.pollId } } : '/history'"
      class="text-sm text-slate-700 underline"
    >← {{ $t('history.diff.back') }}</router-link>

    <p v-if="error" class="mt-4 text-sm text-red-700">{{ error }}</p>
    <p v-else-if="!diff" class="mt-4 text-sm text-slate-600">{{ $t('common.loading') }}</p>
    <template v-else>
      <h1 class="mb-1 mt-3 text-2xl font-semibold text-slate-800">{{ diff.pollTitle }}</h1>
      <p class="mb-4 text-sm text-slate-600">
        {{ $t(`history.event.${diff.event}`) }} · {{ new Date(diff.at).toLocaleString() }}
      </p>

      <div class="grid grid-cols-2 gap-x-4 gap-y-3" data-test="history-diff-page">
        <h2 class="m-0 border-b-2 border-slate-300 pb-1 text-lg font-semibold text-slate-800">{{ $t('history.diff.before') }}</h2>
        <h2 class="m-0 border-b-2 border-slate-300 pb-1 text-lg font-semibold text-slate-800">{{ $t('history.diff.now') }}</h2>

        <template v-for="f in fields" :key="f.key">
          <div :class="f.before !== f.now ? changedTint : ''" class="p-2" :data-test="`before-${f.key}`">
            <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">{{ f.label }}</div>
            <div class="whitespace-pre-wrap break-words text-sm text-slate-800">{{ f.before || '—' }}</div>
          </div>
          <div :class="f.before !== f.now ? changedTint : ''" class="p-2" :data-test="`now-${f.key}`">
            <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">{{ f.label }}</div>
            <div class="whitespace-pre-wrap break-words text-sm text-slate-800">{{ f.now || '—' }}</div>
          </div>
        </template>

        <template v-if="showItems">
          <div class="p-2" data-test="before-items">
            <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">{{ itemsLabel }}</div>
            <ol class="m-0 mt-1 flex list-decimal flex-col gap-1 pl-5 text-sm text-slate-800">
              <li v-for="(q, i) in diff.before.items" :key="i" :class="diff.now.items.includes(q) ? '' : `${changedTint} px-1`">{{ q }}</li>
            </ol>
          </div>
          <div class="p-2" data-test="now-items">
            <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">{{ itemsLabel }}</div>
            <ol class="m-0 mt-1 flex list-decimal flex-col gap-1 pl-5 text-sm text-slate-800">
              <li v-for="(q, i) in diff.now.items" :key="i" :class="diff.before.items.includes(q) ? '' : `${changedTint} px-1`">{{ q }}</li>
            </ol>
          </div>
        </template>
      </div>
      <p class="mt-4 text-xs text-slate-500">{{ $t('history.diff.legend') }}</p>
    </template>
  </div>
</template>
