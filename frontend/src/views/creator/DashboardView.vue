<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import type { PollStatus } from '@/types'
import { AccessLevel } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import ScrollEnds from '@/components/ScrollEnds.vue'
import ColumnChooser from '@/components/ColumnChooser.vue'
import { useColumns } from '@/composables/useColumns'

const { t } = useI18n()
const auth = useAuthStore()

// ---------- Columns: choose which to show ----------
const COLUMN_KEYS = ['title', 'type', 'election', 'enabled', 'status', 'closeDate', 'responses', 'emails'] as const
type ColumnKey = (typeof COLUMN_KEYS)[number]
const columns = useColumns<ColumnKey>('creatorDashboard.columns', COLUMN_KEYS, ['election', 'enabled', 'emails'])
const columnOptions = computed(() => ([
  ['title', 'tableTitle'], ['type', 'tableType'], ['election', 'tableForElection'], ['enabled', 'tableEnabled'],
  ['status', 'tableStatus'], ['closeDate', 'tableCloseDate'], ['responses', 'tableResponses'], ['emails', 'tableEmails'],
] as const).map(([key, label]) => ({ key, label: t(`creator.dashboard.${label}`) })))

/** The first 15 characters of an election's title, then "..." (full title on hover). */
const shortTitle = (title: string) => title.length > 15 ? `${title.slice(0, 15)}...` : title
function enabledClasses(e: CreatorPollSummary['enabled']): string {
  if (e === 'NO') return 'bg-red-100 text-red-800'
  if (e === 'PARTLY') return 'bg-amber-100 text-amber-800'
  return 'text-slate-700'
}

interface CreatorPollSummary {
  id: number
  type: 'Questionnaire' | 'Election' | 'BallotMeasure'
  title: string
  status: PollStatus
  closeDate: string | null
  createdAt: string
  /** People who have answered (0 for drafts). */
  respondents: number
  /** How many live inside the poll's area; null = withheld (a group is too small). */
  inArea: number | null
  /** Milestone emails on for this poll (results visible, closed); the creator can pause them. */
  emails: boolean
  /** A ballot measure's election (null for other types). */
  electionId: number | null
  electionTitle: string | null
  /** Switched off by an admin: not at all, in some areas, or everywhere. */
  enabled: 'YES' | 'PARTLY' | 'NO'
}

const polls = ref<CreatorPollSummary[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const showArchived = ref(false)

function editSlug(type: 'Questionnaire' | 'Election' | 'BallotMeasure'): string {
  switch (type) {
    case 'Questionnaire': return 'questionnaire'
    case 'Election': return 'election'
    case 'BallotMeasure': return 'ballot-measure'
  }
}

function closeDatePast(iso: string | null): boolean {
  if (!iso) return false
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  return new Date(iso).getTime() <= end.getTime()
}

// Whole days until a live poll closes (0 = closes today); null if no close
// date, already closed, or not live.
function daysToClose(p: CreatorPollSummary): number | null {
  if (p.status !== 'PUBLISHED' || !p.closeDate) return null
  const ms = new Date(p.closeDate).getTime() - Date.now()
  return ms < 0 ? null : Math.floor(ms / 86_400_000)
}

function inAreaPct(p: CreatorPollSummary): number | null {
  return p.inArea === null || p.respondents === 0 ? null : Math.round((p.inArea / p.respondents) * 100)
}

// Results are public once a poll has been published.
function hasResults(p: CreatorPollSummary): boolean {
  return p.status === 'PUBLISHED' || p.status === 'CLOSED'
}

function statusClasses(status: PollStatus): string {
  switch (status) {
    case 'PUBLISHED' as PollStatus: return 'bg-green-100 text-green-900'
    case 'DRAFT'     as PollStatus: return 'bg-yellow-100 text-yellow-900'
    case 'CLOSED'    as PollStatus: return 'bg-red-100 text-red-900'
    default: return 'bg-slate-200 text-slate-700'
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await axios.get<CreatorPollSummary[]>('/api/creator/polls', {
      params: showArchived.value ? { showArchived: true } : {}
    })
    polls.value = res.data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('creator.dashboard.errorLoad')
  } finally {
    loading.value = false
  }
}

function toggleArchived() {
  showArchived.value = !showArchived.value
  load()
}

const acting = ref<number | null>(null)
// The Emails checkbox: pause / resume this poll's milestone emails.
async function setEmails(p: CreatorPollSummary, enabled: boolean) {
  error.value = null
  try {
    await axios.put(`/api/creator/polls/${editSlug(p.type)}/${p.id}/emails`, { enabled })
    p.emails = enabled
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('creator.dashboard.errorEmails')
  }
}

async function archive(p: CreatorPollSummary) {
  if (!window.confirm(t('creator.dashboard.archiveConfirm', { title: p.title }))) return
  acting.value = p.id
  error.value = null
  try {
    await axios.delete(`/api/creator/polls/${editSlug(p.type)}/${p.id}`)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('creator.dashboard.errorArchive')
  } finally {
    acting.value = null
  }
}

// Reopen a published or closed poll to revise it: back to draft (out of search
// and voting until published again), then straight to its edit page.
const router = useRouter()
async function reopen(p: CreatorPollSummary) {
  if (!window.confirm(t('creator.dashboard.reopenConfirm', { title: p.title }))) return
  acting.value = p.id
  error.value = null
  try {
    await axios.post(`/api/creator/polls/${editSlug(p.type)}/${p.id}/reopen`)
    router.push(`/creator/polls/${editSlug(p.type)}/${p.id}/edit`)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('creator.dashboard.errorReopen')
  } finally {
    acting.value = null
  }
}

async function restore(p: CreatorPollSummary) {
  acting.value = p.id
  error.value = null
  try {
    await axios.post(`/api/creator/polls/${editSlug(p.type)}/${p.id}/restore`)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('creator.dashboard.errorRestore')
  } finally {
    acting.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-5xl py-8">
    <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
      <h1 class="m-0 text-2xl font-semibold text-slate-800">{{ $t('creator.dashboard.title') }}</h1>
      <div class="flex flex-wrap gap-2">
        <!-- Widen access (more area or poll types): the home page's "Become a
             Creator" card is gone once someone is a creator. -->
        <router-link
          to="/history"
          class="rounded border border-slate-300 bg-white px-4 py-2 text-sm text-slate-800 no-underline hover:bg-slate-50"
        >{{ $t('history.link') }}</router-link>
        <router-link
          to="/creator/request"
          class="rounded border border-slate-800 bg-white px-4 py-2 text-sm text-slate-800 no-underline hover:bg-slate-50"
        >
          {{ $t('creator.dashboard.requestMoreAccess') }}
        </router-link>
        <router-link
          v-if="!auth.hasAccess(AccessLevel.ADMIN)"
          to="/admin-request"
          class="rounded border border-slate-800 bg-white px-4 py-2 text-sm text-slate-800 no-underline hover:bg-slate-50"
        >
          {{ $t('creator.dashboard.requestAdminAccess') }}
        </router-link>
        <router-link
          to="/creator/polls/new"
          class="rounded bg-slate-800 px-4 py-2 text-sm text-white no-underline hover:bg-slate-900"
        >
          {{ $t('creator.dashboard.newPoll') }}
        </router-link>
      </div>
    </div>

    <div class="mb-3 flex justify-end">
      <button
        type="button"
        @click="toggleArchived"
        class="text-sm text-slate-600 underline hover:text-slate-900"
      >{{ showArchived ? $t('creator.dashboard.hideArchived') : $t('creator.dashboard.showArchived') }}</button>
    </div>

    <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-if="loading" class="text-sm text-slate-600">{{ $t('common.loading') }}</p>

    <p v-else-if="polls.length === 0" class="text-sm text-slate-500">
      {{ showArchived ? $t('creator.dashboard.emptyArchived') : $t('creator.dashboard.emptyDefault') }}
    </p>

    <ScrollEnds v-if="!loading && polls.length > 0">
    <ColumnChooser v-model="columns" :options="columnOptions" />
    <!-- Phones: one card per poll (2026-10-28); wider screens: the table. -->
    <ul class="m-0 flex list-none flex-col gap-3 p-0 sm:hidden" data-test="poll-cards">
      <li v-for="p in polls" :key="`card-${p.type}-${p.id}`" class="rounded-md border border-slate-200 p-3 text-sm">
        <div class="mb-1 flex flex-wrap items-start justify-between gap-2">
          <span class="font-semibold text-slate-800">{{ p.title }}</span>
          <span v-if="columns.status" :class="['inline-block rounded px-2 py-0.5 text-xs', statusClasses(p.status)]">{{ p.status }}</span>
        </div>
        <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-xs text-slate-600">
          <template v-if="columns.type"><dt>{{ $t('creator.dashboard.tableType') }}</dt><dd class="m-0">{{ p.type }}</dd></template>
          <template v-if="columns.election && p.electionId && p.electionTitle">
            <dt>{{ $t('creator.dashboard.tableForElection') }}</dt>
            <dd class="m-0"><router-link :to="`/polls/election/${p.electionId}`" class="text-slate-800 underline">{{ p.electionTitle }}</router-link></dd>
          </template>
          <template v-if="columns.enabled">
            <dt>{{ $t('creator.dashboard.tableEnabled') }}</dt>
            <dd class="m-0"><span :class="['inline-block rounded px-2 py-0.5', enabledClasses(p.enabled)]">{{ $t(`creator.dashboard.enabled${p.enabled}`) }}</span></dd>
          </template>
          <template v-if="columns.closeDate">
            <dt>{{ $t('creator.dashboard.tableCloseDate') }}</dt>
            <dd class="m-0" :class="closeDatePast(p.closeDate) ? 'text-orange-700' : ''">
              {{ p.closeDate ? new Date(p.closeDate).toLocaleString() : '—' }}
              <template v-if="daysToClose(p) !== null"> · {{ $t('creator.dashboard.closesIn', { n: daysToClose(p) }, daysToClose(p)!) }}</template>
            </dd>
          </template>
          <template v-if="columns.responses">
            <dt>{{ $t('creator.dashboard.tableResponses') }}</dt>
            <dd class="m-0">
              <template v-if="p.status === 'DRAFT'">—</template>
              <template v-else>
                {{ p.respondents }}
                <template v-if="inAreaPct(p) !== null"> · {{ $t('creator.dashboard.inAreaShare', { pct: inAreaPct(p) }) }}</template>
                <template v-else-if="p.respondents > 0"> · {{ $t('creator.dashboard.inAreaHidden') }}</template>
              </template>
            </dd>
          </template>
          <template v-if="columns.emails">
            <dt>{{ $t('creator.dashboard.tableEmails') }}</dt>
            <dd class="m-0">
              <input
                type="checkbox"
                class="h-4 w-4 align-middle"
                data-test="card-emails"
                :checked="p.emails"
                :aria-label="$t('creator.dashboard.emailsFor', { title: p.title })"
                @change="setEmails(p, ($event.target as HTMLInputElement).checked)"
              />
            </dd>
          </template>
        </dl>
        <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1">
          <router-link v-if="hasResults(p)" :to="`/polls/${editSlug(p.type)}/${p.id}/results`" class="text-slate-800 underline">{{ $t('creator.dashboard.results') }}</router-link>
          <router-link v-if="hasResults(p)" :to="`/creator/polls/${editSlug(p.type)}/${p.id}/report`" class="text-slate-800 underline">{{ $t('creator.dashboard.report') }}</router-link>
          <router-link :to="{ path: '/history', query: { type: editSlug(p.type), id: String(p.id) } }" class="text-slate-800 underline">{{ $t('history.link') }}</router-link>
          <router-link v-if="p.status === 'DRAFT'" :to="`/creator/polls/${editSlug(p.type)}/${p.id}/edit`" class="text-slate-800 underline">{{ $t('creator.dashboard.edit') }}</router-link>
          <button
            v-else-if="p.status === 'PUBLISHED' || p.status === 'CLOSED'"
            data-test="card-reopen"
            :disabled="acting === p.id"
            class="text-slate-800 underline disabled:opacity-50"
            @click="reopen(p)"
          >{{ $t('creator.dashboard.reopen') }}</button>
          <button
            v-if="p.status !== 'ARCHIVED'"
            :disabled="acting === p.id"
            class="text-slate-800 underline disabled:opacity-50"
            @click="archive(p)"
          >{{ acting === p.id ? $t('creator.dashboard.archiving') : $t('creator.dashboard.archive') }}</button>
          <button
            v-else
            :disabled="acting === p.id"
            class="text-slate-800 underline disabled:opacity-50"
            @click="restore(p)"
          >{{ acting === p.id ? $t('creator.dashboard.restoring') : $t('creator.dashboard.restore') }}</button>
        </div>
      </li>
    </ul>
    <div class="hidden sm:block">
    <table class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th v-if="columns.title" class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('creator.dashboard.tableTitle') }}</th>
          <th v-if="columns.type" class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('creator.dashboard.tableType') }}</th>
          <th v-if="columns.election" class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('creator.dashboard.tableForElection') }}</th>
          <th v-if="columns.enabled" class="border-b border-slate-200 p-2 font-semibold text-slate-700" :title="$t('creator.dashboard.enabledHint')">{{ $t('creator.dashboard.tableEnabled') }}</th>
          <th v-if="columns.status" class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('creator.dashboard.tableStatus') }}</th>
          <th v-if="columns.closeDate" class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('creator.dashboard.tableCloseDate') }}</th>
          <th v-if="columns.responses" class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('creator.dashboard.tableResponses') }}</th>
          <th v-if="columns.emails" class="border-b border-slate-200 p-2 font-semibold text-slate-700" :title="$t('creator.dashboard.emailsHint')">{{ $t('creator.dashboard.tableEmails') }}</th>
          <th class="border-b border-slate-200 p-2"></th>
          <th class="border-b border-slate-200 p-2"></th>
          <th class="border-b border-slate-200 p-2"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in polls" :key="`${p.type}-${p.id}`">
          <td v-if="columns.title" class="border-b border-slate-100 p-2">{{ p.title }}</td>
          <td v-if="columns.type" class="border-b border-slate-100 p-2">{{ p.type }}</td>
          <td v-if="columns.election" class="border-b border-slate-100 p-2" data-test="for-election">
            <router-link
              v-if="p.electionId && p.electionTitle"
              :to="`/polls/election/${p.electionId}`"
              :title="p.electionTitle"
              class="text-slate-800 underline"
            >{{ shortTitle(p.electionTitle) }}</router-link>
            <span v-else class="text-slate-400">—</span>
          </td>
          <td v-if="columns.enabled" class="border-b border-slate-100 p-2" data-test="enabled" :title="$t('creator.dashboard.enabledHint')">
            <span :class="['inline-block rounded px-2 py-0.5 text-xs', enabledClasses(p.enabled)]">{{ $t(`creator.dashboard.enabled${p.enabled}`) }}</span>
          </td>
          <td v-if="columns.status" class="border-b border-slate-100 p-2">
            <span
              :class="['inline-block rounded px-2 py-0.5 text-xs', statusClasses(p.status)]"
            >
              {{ p.status }}
            </span>
          </td>
          <td
            v-if="columns.closeDate"
            class="border-b border-slate-100 p-2"
            :class="closeDatePast(p.closeDate) ? 'bg-orange-50 text-orange-900' : ''"
          >
            {{ p.closeDate ? new Date(p.closeDate).toLocaleString() : '—' }}
            <span v-if="daysToClose(p) !== null" class="block text-xs text-slate-500">
              {{ $t('creator.dashboard.closesIn', { n: daysToClose(p) }, daysToClose(p)!) }}
            </span>
          </td>
          <td v-if="columns.responses" class="border-b border-slate-100 p-2" data-test="responses">
            <template v-if="p.status === 'DRAFT'">—</template>
            <template v-else>
              {{ p.respondents }}
              <span v-if="inAreaPct(p) !== null" class="text-slate-500">· {{ $t('creator.dashboard.inAreaShare', { pct: inAreaPct(p) }) }}</span>
              <span
                v-else-if="p.respondents > 0"
                class="text-slate-500"
                :title="$t('creator.dashboard.inAreaHiddenWhy')"
              >· {{ $t('creator.dashboard.inAreaHidden') }}</span>
            </template>
          </td>
          <td v-if="columns.emails" class="border-b border-slate-100 p-2" :title="$t('creator.dashboard.emailsHint')">
            <input
              type="checkbox"
              class="h-4 w-4"
              data-test="emails"
              :checked="p.emails"
              :aria-label="$t('creator.dashboard.emailsFor', { title: p.title })"
              @change="setEmails(p, ($event.target as HTMLInputElement).checked)"
            />
          </td>
          <td class="border-b border-slate-100 p-2">
            <router-link
              v-if="hasResults(p)"
              :to="`/polls/${editSlug(p.type)}/${p.id}/results`"
              class="text-slate-800 underline"
            >{{ $t('creator.dashboard.results') }}</router-link>
            <router-link
              v-if="hasResults(p)"
              :to="`/creator/polls/${editSlug(p.type)}/${p.id}/report`"
              class="ml-2 text-slate-800 underline"
            >{{ $t('creator.dashboard.report') }}</router-link>
            <router-link
              :to="{ path: '/history', query: { type: editSlug(p.type), id: String(p.id) } }"
              class="ml-2 text-slate-800 underline"
              data-test="poll-history"
            >{{ $t('history.link') }}</router-link>
          </td>
          <td class="border-b border-slate-100 p-2">
            <router-link
              v-if="p.status === 'DRAFT'"
              :to="`/creator/polls/${editSlug(p.type)}/${p.id}/edit`"
              class="text-slate-800 underline"
            >{{ $t('creator.dashboard.edit') }}</router-link>
            <button
              v-else-if="p.status === 'PUBLISHED' || p.status === 'CLOSED'"
              data-test="reopen"
              :title="$t('creator.dashboard.reopenHint')"
              :disabled="acting === p.id"
              class="text-slate-800 underline hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-50"
              @click="reopen(p)"
            >{{ $t('creator.dashboard.reopen') }}</button>
          </td>
          <td class="border-b border-slate-100 p-2">
            <button
              v-if="p.status !== 'ARCHIVED'"
              @click="archive(p)"
              :disabled="acting === p.id"
              class="text-slate-800 underline hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-50"
            >{{ acting === p.id ? $t('creator.dashboard.archiving') : $t('creator.dashboard.archive') }}</button>
            <button
              v-else
              @click="restore(p)"
              :disabled="acting === p.id"
              class="text-slate-800 underline hover:text-slate-900 disabled:cursor-not-allowed disabled:opacity-50"
            >{{ acting === p.id ? $t('creator.dashboard.restoring') : $t('creator.dashboard.restore') }}</button>
          </td>
        </tr>
      </tbody>
    </table>
    </div>
    </ScrollEnds>
  </div>
</template>
