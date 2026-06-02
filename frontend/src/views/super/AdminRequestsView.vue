<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import type { RequestStatus } from '@/types'
import { formatZipList } from '@/utils/formatZipList'

const { t } = useI18n()

interface AdminRequestDto {
  id: number
  userId: number
  userEmail: string
  status: RequestStatus
  reason: string
  zipcodes: string[]
  submittedAt: string
  processedAt: string | null
  processedByEmail: string | null
}

const requests = ref<AdminRequestDto[]>([])
const selected = ref<Set<number>>(new Set())
const loading = ref(false)
const acting = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)

const allChecked = computed(() =>
  requests.value.length > 0 && selected.value.size === requests.value.length
)

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await axios.get<AdminRequestDto[]>('/api/super/admin-requests')
    requests.value = res.data
    selected.value = new Set()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.adminRequests.errorLoad')
  } finally {
    loading.value = false
  }
}

function toggle(id: number) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id); else next.add(id)
  selected.value = next
}
function toggleAll() {
  selected.value = allChecked.value
    ? new Set()
    : new Set(requests.value.map(r => r.id))
}

async function decide(path: 'batch-approve' | 'batch-reject', verb: string) {
  if (selected.value.size === 0) return
  acting.value = true
  error.value = null
  message.value = null
  try {
    const ids = Array.from(selected.value)
    await axios.post(`/api/super/admin-requests/${path}`, { requestIds: ids })
    message.value = t('super.adminRequests.countMessage', { n: ids.length, verb })
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.adminRequests.failedTo', { verb })
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-6xl py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('super.adminRequests.heading') }}</h1>

    <div class="mb-4 flex items-center gap-2">
      <button
        @click="load"
        :disabled="loading"
        class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {{ loading ? $t('common.loading') : $t('super.adminRequests.refresh') }}
      </button>
      <span class="flex-1" />
      <button
        :disabled="acting || selected.size === 0"
        @click="decide('batch-approve', $t('super.adminRequests.approvedVerb'))"
        class="rounded bg-green-700 px-4 py-2 text-sm text-white hover:bg-green-800 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {{ $t('super.adminRequests.approveSelected', { n: selected.size }) }}
      </button>
      <button
        :disabled="acting || selected.size === 0"
        @click="decide('batch-reject', $t('super.adminRequests.rejectedVerb'))"
        class="rounded bg-red-700 px-4 py-2 text-sm text-white hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {{ $t('super.adminRequests.rejectSelected', { n: selected.size }) }}
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-700">{{ message }}</p>

    <p v-if="!loading && requests.length === 0" class="text-sm text-slate-500">
      {{ $t('super.adminRequests.noPending') }}
    </p>

    <table v-else-if="requests.length > 0" class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th class="border-b border-slate-200 p-2">
            <input type="checkbox" :checked="allChecked" @change="toggleAll" />
          </th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.adminRequests.colUser') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.adminRequests.colZipcodes') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.adminRequests.colReason') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.adminRequests.colSubmitted') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in requests" :key="r.id">
          <td class="border-b border-slate-100 p-2 align-top">
            <input
              type="checkbox"
              :checked="selected.has(r.id)"
              @change="toggle(r.id)"
            />
          </td>
          <td class="border-b border-slate-100 p-2 align-top">{{ r.userEmail }}</td>
          <td class="border-b border-slate-100 p-2 align-top font-mono text-xs">
            {{ formatZipList(r.zipcodes) }}
          </td>
          <td class="border-b border-slate-100 p-2 align-top" style="max-width: 360px">
            {{ r.reason }}
          </td>
          <td class="border-b border-slate-100 p-2 align-top">
            {{ new Date(r.submittedAt).toLocaleString() }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
