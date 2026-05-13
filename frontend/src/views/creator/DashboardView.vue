<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import type { PollStatus } from '@/types'
import { AccessLevel } from '@/types'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()

interface CreatorPollSummary {
  id: number
  type: 'Questionnaire' | 'Election' | 'BallotMeasure'
  title: string
  status: PollStatus
  closeDate: string | null
  createdAt: string
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
    <div class="mb-4 flex items-center justify-between">
      <h1 class="m-0 text-2xl font-semibold text-slate-800">{{ $t('creator.dashboard.title') }}</h1>
      <div class="flex gap-2">
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

    <table v-else class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('creator.dashboard.tableTitle') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('creator.dashboard.tableType') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('creator.dashboard.tableStatus') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('creator.dashboard.tableCloseDate') }}</th>
          <th class="border-b border-slate-200 p-2"></th>
          <th class="border-b border-slate-200 p-2"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in polls" :key="`${p.type}-${p.id}`">
          <td class="border-b border-slate-100 p-2">{{ p.title }}</td>
          <td class="border-b border-slate-100 p-2">{{ p.type }}</td>
          <td class="border-b border-slate-100 p-2">
            <span
              :class="['inline-block rounded px-2 py-0.5 text-xs', statusClasses(p.status)]"
            >
              {{ p.status }}
            </span>
          </td>
          <td
            class="border-b border-slate-100 p-2"
            :class="closeDatePast(p.closeDate) ? 'bg-orange-50 text-orange-900' : ''"
          >
            {{ p.closeDate ? new Date(p.closeDate).toLocaleString() : '—' }}
          </td>
          <td class="border-b border-slate-100 p-2">
            <router-link
              v-if="p.status === 'DRAFT'"
              :to="`/creator/polls/${editSlug(p.type)}/${p.id}/edit`"
              class="text-slate-800 underline"
            >{{ $t('creator.dashboard.edit') }}</router-link>
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
</template>
