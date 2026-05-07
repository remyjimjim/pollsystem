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
  <div class="view">
    <div class="header-row">
      <h1>Creator Dashboard</h1>
      <div class="header-actions">
        <router-link to="/admin-request" class="secondary">Request admin access</router-link>
        <router-link to="/creator/polls/new" class="primary">+ New Poll</router-link>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading">Loading…</p>

    <p v-else-if="polls.length === 0" class="hint">
      You haven't created any polls yet.
    </p>

    <table v-else class="grid">
      <thead>
        <tr>
          <th>Title</th>
          <th>Type</th>
          <th>Status</th>
          <th>Close date</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in polls" :key="`${p.type}-${p.id}`">
          <td>{{ p.title }}</td>
          <td>{{ p.type }}</td>
          <td>
            <span :class="['status', `status-${p.status}`]">{{ p.status }}</span>
          </td>
          <td>{{ p.closeDate ? new Date(p.closeDate).toLocaleString() : '—' }}</td>
          <td>
            <router-link
              v-if="p.status === 'DRAFT'"
              :to="`/creator/polls/${editSlug(p.type)}/${p.id}/edit`"
            >Edit</router-link>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.view {
  padding: 2rem 0;
  max-width: 1000px;
  margin: 0 auto;
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}
h1 {
  color: #1a365d;
  margin: 0;
}
.header-actions { display: flex; gap: 0.5rem; }
.primary {
  background: #1a365d;
  color: white;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  text-decoration: none;
}
.secondary {
  background: white;
  color: #1a365d;
  padding: 0.5rem 1rem;
  border: 1px solid #1a365d;
  border-radius: 4px;
  text-decoration: none;
}
.grid {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
.grid th,
.grid td {
  padding: 0.5rem;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
}
.grid th {
  background: #f7fafc;
}
.status {
  font-size: 0.75rem;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  background: #e2e8f0;
  color: #2d3748;
}
.status-PUBLISHED {
  background: #c6f6d5;
  color: #22543d;
}
.status-DRAFT {
  background: #fefcbf;
  color: #744210;
}
.status-CLOSED {
  background: #fed7d7;
  color: #742a2a;
}
.error {
  color: #c53030;
}
.hint {
  color: #718096;
}
</style>
