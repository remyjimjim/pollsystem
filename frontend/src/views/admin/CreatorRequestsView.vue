<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import type { RequestStatus } from '@/types'

const { t } = useI18n()

interface CreatorRequestDto {
  id: number
  userId: number
  userEmail: string
  assignedAdminId: number | null
  status: RequestStatus
  reason: string
  regionLabel: string
  zipcodes: string[]
  pollTypeIds: number[]
  submittedAt: string
  processedAt: string | null
}

const requests = ref<CreatorRequestDto[]>([])
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
    const res = await axios.get<CreatorRequestDto[]>('/api/admin/creator-requests')
    requests.value = res.data
    selected.value = new Set()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('admin.creatorRequests.errorLoad')
  } finally {
    loading.value = false
  }
}

function toggle(id: number) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
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
    await axios.post(`/api/admin/creator-requests/${path}`, { requestIds: ids })
    message.value = t('admin.creatorRequests.countMessage', { n: ids.length, verb })
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('admin.creatorRequests.failedTo', { verb })
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-6xl py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('admin.creatorRequests.heading') }}</h1>

    <div class="mb-4 flex flex-wrap items-center gap-2">
      <button
        @click="load"
        :disabled="loading"
        class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {{ loading ? $t('common.loading') : $t('admin.creatorRequests.refresh') }}
      </button>
      <span class="flex-1" />
      <button
        :disabled="acting || selected.size === 0"
        @click="decide('batch-approve', $t('admin.creatorRequests.approvedVerb'))"
        class="rounded bg-green-700 px-4 py-2 text-sm text-white hover:bg-green-800 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {{ $t('admin.creatorRequests.approveSelected', { n: selected.size }) }}
      </button>
      <button
        :disabled="acting || selected.size === 0"
        @click="decide('batch-reject', $t('admin.creatorRequests.rejectedVerb'))"
        class="rounded bg-red-700 px-4 py-2 text-sm text-white hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {{ $t('admin.creatorRequests.rejectSelected', { n: selected.size }) }}
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-700">{{ message }}</p>

    <p v-if="!loading && requests.length === 0" class="text-sm text-slate-500">
      {{ $t('admin.creatorRequests.noPending') }}
    </p>

    <template v-else-if="requests.length > 0">
    <table class="hidden w-full border-collapse text-sm sm:table">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th class="border-b border-slate-200 p-2">
            <input type="checkbox" :checked="allChecked" @change="toggleAll" />
          </th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.creatorRequests.colUser') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.creatorRequests.colArea') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.creatorRequests.colReason') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.creatorRequests.colSubmitted') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.creatorRequests.colStatus') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="r in requests"
          :key="r.id"
          :class="r.assignedAdminId == null ? 'bg-orange-50' : ''"
        >
          <td class="border-b border-slate-100 p-2 align-top">
            <input
              type="checkbox"
              :checked="selected.has(r.id)"
              @change="toggle(r.id)"
            />
          </td>
          <td class="border-b border-slate-100 p-2 align-top">{{ r.userEmail }}</td>
          <td class="border-b border-slate-100 p-2 align-top text-xs">
            {{ r.regionLabel }}
          </td>
          <td class="border-b border-slate-100 p-2 align-top" style="max-width: 360px">
            {{ r.reason }}
          </td>
          <td class="border-b border-slate-100 p-2 align-top">
            {{ new Date(r.submittedAt).toLocaleString() }}
          </td>
          <td class="border-b border-slate-100 p-2 align-top">
            {{ r.status }}
            <span
              v-if="r.assignedAdminId == null"
              class="ml-2 inline-block rounded bg-orange-500 px-1.5 py-0.5 text-xs text-white"
            >
              {{ $t('admin.creatorRequests.badgeUnassigned') }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
    <!-- Phones: one card per request (2026-10-28); the checkboxes feed the same bulk actions. -->
    <div class="sm:hidden" data-test="request-cards">
      <label class="mb-2 flex items-center gap-2 text-sm text-slate-700">
        <input type="checkbox" :checked="allChecked" @change="toggleAll" class="h-4 w-4" />
        {{ $t('admin.creatorRequests.selectAll') }}
      </label>
      <ul class="m-0 flex list-none flex-col gap-3 p-0">
        <li
          v-for="r in requests"
          :key="`card-${r.id}`"
          class="rounded-md border border-slate-200 p-3 text-sm"
          :class="r.assignedAdminId == null ? 'bg-orange-50' : ''"
        >
          <label class="mb-1 flex items-start gap-2">
            <input type="checkbox" :checked="selected.has(r.id)" @change="toggle(r.id)" class="mt-0.5 h-4 w-4 shrink-0" data-test="card-select" />
            <span class="break-all font-semibold text-slate-800">{{ r.userEmail }}</span>
          </label>
          <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-xs text-slate-600">
            <dt>{{ $t('admin.creatorRequests.colArea') }}</dt><dd class="m-0">{{ r.regionLabel }}</dd>
            <dt>{{ $t('admin.creatorRequests.colSubmitted') }}</dt><dd class="m-0">{{ new Date(r.submittedAt).toLocaleString() }}</dd>
            <dt>{{ $t('admin.creatorRequests.colStatus') }}</dt>
            <dd class="m-0">
              {{ r.status }}
              <span v-if="r.assignedAdminId == null" class="ml-1 rounded bg-orange-500 px-1.5 py-0.5 text-white">{{ $t('admin.creatorRequests.badgeUnassigned') }}</span>
            </dd>
          </dl>
          <p v-if="r.reason" class="m-0 mt-1 whitespace-pre-wrap text-slate-700">{{ r.reason }}</p>
        </li>
      </ul>
    </div>
    </template>
  </div>
</template>
