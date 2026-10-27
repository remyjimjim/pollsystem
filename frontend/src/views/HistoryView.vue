<script setup lang="ts">
// A poll's history (decided 2026-10-17): published, reopened, closed,
// archived, restored, switched off/on, edited. Creators see their own polls,
// admins also the polls in their area, supers everything (the server decides).
// ?type=&id= shows one poll (the History links on the poll lists).
import { computed, ref, watch } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

interface HistoryRow {
  id: number
  type: 'questionnaire' | 'election' | 'ballot-measure'
  pollId: number
  pollTitle: string
  event: string
  at: string
  by: string
  byEmail: string | null
  details: string | null
  hasDiff?: boolean
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const rows = ref<HistoryRow[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const filterType = computed(() => (typeof route.query.type === 'string' ? route.query.type : null))
const filterId = computed(() => (typeof route.query.id === 'string' ? Number(route.query.id) : null))
const oneSelected = computed(() => !!filterType.value && !!filterId.value)

async function load() {
  loading.value = true
  error.value = null
  try {
    rows.value = (await axios.get<HistoryRow[]>('/api/poll-history', {
      params: oneSelected.value ? { type: filterType.value, id: filterId.value } : {}
    })).data ?? []
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('history.loadFailed')
  } finally {
    loading.value = false
  }
}
watch([filterType, filterId], load, { immediate: true })

// The poll picker lists the polls in what's loaded (all polls, or the one).
const polls = computed(() => {
  const seen = new Map<string, HistoryRow>()
  for (const r of rows.value) seen.set(`${r.type}:${r.pollId}`, r)
  return [...seen.values()].sort((a, b) => a.pollTitle.localeCompare(b.pollTitle))
})
const selected = computed(() => (oneSelected.value ? `${filterType.value}:${filterId.value}` : ''))
function pick(value: string) {
  if (!value) { router.replace({ query: {} }); return }
  const [type, id] = value.split(':')
  router.replace({ query: { type, id } })
}

const when = (r: HistoryRow) => r.event === 'PUBLISHED_BEFORE_HISTORY' ? '—' : new Date(r.at).toLocaleString()
function by(r: HistoryRow): string {
  const who = ['you', 'automatic', 'ADMIN', 'SUPER', 'CREATOR', 'USER'].includes(r.by) ? t(`history.by.${r.by}`) : r.by
  return r.byEmail && r.by !== 'you' ? `${who} (${r.byEmail})` : who
}
</script>

<template>
  <div class="mx-auto max-w-5xl py-8">
    <h1 class="mb-1 text-2xl font-semibold text-slate-800">{{ $t('history.title') }}</h1>
    <p class="mb-4 text-sm text-slate-600">{{ $t('history.intro') }}</p>

    <div class="mb-3 flex flex-wrap items-center gap-2 text-sm">
      <label for="history-poll" class="text-slate-700">{{ $t('history.pollFilter') }}</label>
      <select
        id="history-poll"
        :value="selected"
        class="rounded border border-slate-300 px-2 py-1"
        data-test="history-poll"
        @change="pick(($event.target as HTMLSelectElement).value)"
      >
        <option value="">{{ $t('history.allPolls') }}</option>
        <option v-if="oneSelected && !polls.length" :value="selected">#{{ filterId }}</option>
        <option v-for="p in polls" :key="`${p.type}:${p.pollId}`" :value="`${p.type}:${p.pollId}`">
          {{ p.pollTitle }} ({{ $t(`history.type.${p.type}`) }})
        </option>
      </select>
    </div>

    <p v-if="loading" class="text-sm text-slate-600">{{ $t('common.loading') }}</p>
    <p v-else-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-else-if="!rows.length" class="text-sm text-slate-500">{{ $t('history.empty') }}</p>
    <table v-else class="w-full border-collapse text-sm" data-test="history-table">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('history.colWhen') }}</th>
          <th v-if="!oneSelected" class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('history.colPoll') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('history.colWhat') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('history.colBy') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('history.colDetails') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('history.colDiff') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.id" data-test="history-row">
          <td class="whitespace-nowrap border-b border-slate-100 p-2 align-top text-slate-600">{{ when(r) }}</td>
          <td v-if="!oneSelected" class="border-b border-slate-100 p-2 align-top">
            <a href="#" class="text-slate-800 underline" @click.prevent="pick(`${r.type}:${r.pollId}`)">{{ r.pollTitle }}</a>
            <span class="text-xs text-slate-500"> · {{ $t(`history.type.${r.type}`) }}</span>
          </td>
          <td class="border-b border-slate-100 p-2 align-top font-medium text-slate-800">{{ $t(`history.event.${r.event}`) }}</td>
          <td class="border-b border-slate-100 p-2 align-top text-slate-600">{{ by(r) }}</td>
          <td class="whitespace-pre-line border-b border-slate-100 p-2 align-top text-slate-600">{{ r.details ?? '' }}</td>
          <td class="border-b border-slate-100 p-2 align-top">
            <router-link v-if="r.hasDiff" :to="`/history/diff/${r.id}`" class="text-slate-800 underline" data-test="history-diff">{{ $t('history.diff.link') }}</router-link>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
