<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'

interface CreatorRequestDto {
  id: number
  userId: number
  userEmail: string
  assignedAdminId: number | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  reason: string
  zipcodes: string[]
  pollTypeIds: number[]
  submittedAt: string
  processedAt: string | null
  processedByEmail: string | null
}

const route = useRoute()
const id = computed(() => Number(route.params.id))

const data = ref<CreatorRequestDto | null>(null)
const loading = ref(true)
const acting = ref(false)
const error = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await axios.get<CreatorRequestDto>(`/api/admin/creator-requests/${id.value}`)
    data.value = res.data
  } catch (e: any) {
    error.value = e?.response?.status === 404
      ? 'Creator request not found.'
      : (e?.response?.data?.message ?? 'Failed to load request')
  } finally {
    loading.value = false
  }
}

async function act(path: 'batch-approve' | 'batch-reject') {
  acting.value = true
  error.value = null
  try {
    await axios.post(`/api/admin/creator-requests/${path}`, { requestIds: [id.value] })
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Action failed'
  } finally {
    acting.value = false
  }
}

function statusClasses(status: string): string {
  switch (status) {
    case 'APPROVED': return 'bg-green-100 text-green-900'
    case 'REJECTED': return 'bg-red-100 text-red-900'
    default:         return 'bg-slate-200 text-slate-700'
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-3xl py-8">
    <p class="mb-2 text-sm">
      <router-link to="/admin/dashboard" class="text-slate-800 underline">← Dashboard</router-link>
    </p>

    <p v-if="loading" class="text-sm text-slate-600">Loading…</p>
    <p v-else-if="error" class="text-sm text-red-700">{{ error }}</p>

    <template v-else-if="data">
      <h1 class="mb-2 text-2xl font-semibold text-slate-800">
        Creator Request #{{ data.id }}
      </h1>
      <p class="mb-6 flex items-center gap-2">
        <span
          :class="['rounded px-2 py-0.5 text-xs font-semibold', statusClasses(data.status)]"
        >{{ data.status }}</span>
        <span class="text-sm text-slate-600">
          submitted {{ new Date(data.submittedAt).toLocaleString() }}
        </span>
      </p>

      <dl class="mb-6 grid grid-cols-[140px_1fr] gap-y-2 text-sm">
        <dt class="font-semibold text-slate-700">Requester</dt>
        <dd>{{ data.userEmail }}</dd>

        <dt class="font-semibold text-slate-700">Zipcodes</dt>
        <dd class="font-mono text-xs">{{ data.zipcodes.join(', ') }}</dd>

        <dt class="font-semibold text-slate-700">Poll Types</dt>
        <dd>{{ data.pollTypeIds.join(', ') }}</dd>

        <dt class="font-semibold text-slate-700">Assigned admin</dt>
        <dd>{{ data.assignedAdminId ?? '— unassigned —' }}</dd>

        <template v-if="data.processedAt">
          <dt class="font-semibold text-slate-700">Decided</dt>
          <dd>
            {{ new Date(data.processedAt).toLocaleString() }}
            <span v-if="data.processedByEmail" class="text-slate-500">
              by {{ data.processedByEmail }}
            </span>
          </dd>
        </template>

        <dt class="font-semibold text-slate-700 self-start pt-1">Reason</dt>
        <dd class="whitespace-pre-wrap rounded bg-slate-50 p-2 text-slate-700">
          {{ data.reason || '— no reason provided —' }}
        </dd>
      </dl>

      <div v-if="data.status === 'PENDING'" class="flex gap-2">
        <button
          @click="act('batch-approve')"
          :disabled="acting"
          class="rounded bg-green-700 px-4 py-2 text-sm text-white hover:bg-green-800 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {{ acting ? 'Working…' : '✓ Approve' }}
        </button>
        <button
          @click="act('batch-reject')"
          :disabled="acting"
          class="rounded bg-red-700 px-4 py-2 text-sm text-white hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {{ acting ? 'Working…' : '✗ Reject' }}
        </button>
      </div>
      <p v-else class="text-sm text-slate-500">
        This request has already been decided.
      </p>
    </template>
  </div>
</template>
