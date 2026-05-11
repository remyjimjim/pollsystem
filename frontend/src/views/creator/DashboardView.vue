<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import type { PollStatus } from '@/types'

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

function editSlug(type: 'Questionnaire' | 'Election' | 'BallotMeasure'): string {
  switch (type) {
    case 'Questionnaire': return 'questionnaire'
    case 'Election': return 'election'
    case 'BallotMeasure': return 'ballot-measure'
  }
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
    const res = await axios.get<CreatorPollSummary[]>('/api/creator/polls')
    polls.value = res.data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load polls'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-5xl py-8">
    <div class="mb-4 flex items-center justify-between">
      <h1 class="m-0 text-2xl font-semibold text-slate-800">Creator Dashboard</h1>
      <div class="flex gap-2">
        <router-link
          to="/admin-request"
          class="rounded border border-slate-800 bg-white px-4 py-2 text-sm text-slate-800 no-underline hover:bg-slate-50"
        >
          Request admin access
        </router-link>
        <router-link
          to="/creator/polls/new"
          class="rounded bg-slate-800 px-4 py-2 text-sm text-white no-underline hover:bg-slate-900"
        >
          + New Poll
        </router-link>
      </div>
    </div>

    <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-if="loading" class="text-sm text-slate-600">Loading…</p>

    <p v-else-if="polls.length === 0" class="text-sm text-slate-500">
      You haven't created any polls yet.
    </p>

    <table v-else class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Title</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Type</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Status</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Close date</th>
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
          <td class="border-b border-slate-100 p-2">
            {{ p.closeDate ? new Date(p.closeDate).toLocaleString() : '—' }}
          </td>
          <td class="border-b border-slate-100 p-2">
            <router-link
              v-if="p.status === 'DRAFT'"
              :to="`/creator/polls/${editSlug(p.type)}/${p.id}/edit`"
              class="text-slate-800 underline"
            >Edit</router-link>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
