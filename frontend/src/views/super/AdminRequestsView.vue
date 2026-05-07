<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import type { RequestStatus } from '@/types'

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
    error.value = e?.response?.data?.message ?? 'Failed to load requests'
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
    message.value = `${ids.length} request(s) ${verb}.`
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? `Failed to ${verb}`
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="view">
    <h1>Admin Requests</h1>

    <div class="bar">
      <button @click="load" :disabled="loading">
        {{ loading ? 'Loading…' : 'Refresh' }}
      </button>
      <span class="spacer" />
      <button class="approve" :disabled="acting || selected.size === 0" @click="decide('batch-approve', 'approved')">
        Approve selected ({{ selected.size }})
      </button>
      <button class="reject" :disabled="acting || selected.size === 0" @click="decide('batch-reject', 'rejected')">
        Reject selected ({{ selected.size }})
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="success">{{ message }}</p>

    <p v-if="!loading && requests.length === 0" class="hint">No pending requests.</p>

    <table v-else-if="requests.length > 0" class="grid">
      <thead>
        <tr>
          <th><input type="checkbox" :checked="allChecked" @change="toggleAll" /></th>
          <th>User</th>
          <th>Zipcodes</th>
          <th>Reason</th>
          <th>Submitted</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in requests" :key="r.id">
          <td>
            <input
              type="checkbox"
              :checked="selected.has(r.id)"
              @change="toggle(r.id)"
            />
          </td>
          <td>{{ r.userEmail }}</td>
          <td class="zips">{{ r.zipcodes.join(', ') }}</td>
          <td class="reason">{{ r.reason }}</td>
          <td>{{ new Date(r.submittedAt).toLocaleString() }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.view { padding: 2rem 0; max-width: 1100px; margin: 0 auto; }
h1 { margin-bottom: 1rem; color: #1a365d; }
.bar { display: flex; gap: 0.5rem; align-items: center; margin-bottom: 1rem; }
.spacer { flex: 1; }
button {
  padding: 0.5rem 1rem;
  border: 1px solid #cbd5e0;
  background: white;
  border-radius: 4px;
  cursor: pointer;
}
button:disabled { opacity: 0.5; cursor: not-allowed; }
button.approve { background: #2f855a; color: white; border-color: #2f855a; }
button.reject { background: #c53030; color: white; border-color: #c53030; }
.grid { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.grid th, .grid td {
  padding: 0.5rem;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
  vertical-align: top;
}
.grid th { background: #f7fafc; }
.zips { font-family: monospace; }
.reason { max-width: 360px; }
.error { color: #c53030; }
.success { color: #2f855a; }
.hint { color: #718096; }
</style>
