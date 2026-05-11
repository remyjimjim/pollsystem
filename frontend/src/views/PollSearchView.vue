<script setup lang="ts">
import { reactive, ref } from 'vue'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

interface PollSearchResult {
  id: number
  type: 'Questionnaire' | 'Election' | 'BallotMeasure'
  title: string
  creatorEmail: string
  closeDate: string | null
  zipcodes: string[]
}

const filters = reactive({
  title: '',
  zipcode: '',
  creatorEmail: '',
  candidateName: '',
  type: ''
})

const results = ref<PollSearchResult[]>([])
const loading = ref(false)
const searched = ref(false)
const error = ref<string | null>(null)

function routeMap(type: string): string {
  switch (type) {
    case 'Questionnaire': return 'questionnaire'
    case 'Election': return 'election'
    case 'BallotMeasure': return 'ballot-measure'
    default: return type.toLowerCase()
  }
}

async function search() {
  loading.value = true
  error.value = null
  try {
    const params: Record<string, string> = {}
    if (filters.title.trim()) params.title = filters.title.trim()
    if (filters.zipcode.trim()) params.zipcode = filters.zipcode.trim()
    if (filters.creatorEmail.trim()) params.creatorEmail = filters.creatorEmail.trim()
    if (filters.candidateName.trim()) params.candidateName = filters.candidateName.trim()
    if (filters.type) params.type = filters.type
    const res = await axios.get<PollSearchResult[]>('/api/polls/search', { params })
    results.value = res.data
    searched.value = true
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Search failed'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="view">
    <h1>{{ auth.isAuthenticated ? 'Find a Poll' : 'Browse Poll Results' }}</h1>
    <p v-if="!auth.isAuthenticated" class="hint">
      Search and view results for any published poll.
      <router-link to="/login">Sign in</router-link> to vote.
    </p>

    <form @submit.prevent="search" class="filters">
      <label>
        Title contains
        <input v-model="filters.title" type="text" />
      </label>
      <label>
        Zipcode
        <input v-model="filters.zipcode" type="text" maxlength="5" pattern="[0-9]{5}" />
      </label>
      <label>
        Creator email
        <input v-model="filters.creatorEmail" type="text" />
      </label>
      <label>
        Candidate name (Election only)
        <input v-model="filters.candidateName" type="text" />
      </label>
      <label>
        Type
        <select v-model="filters.type">
          <option value="">Any</option>
          <option value="Questionnaire">Questionnaire</option>
          <option value="Election">Election</option>
          <option value="BallotMeasure">Ballot Measure</option>
        </select>
      </label>
      <button type="submit" :disabled="loading" class="primary">
        {{ loading ? 'Searching…' : 'Search' }}
      </button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>

    <p v-if="searched && results.length === 0" class="hint">
      No active polls match your filters.
    </p>

    <table v-if="results.length > 0" class="grid">
      <thead>
        <tr>
          <th>Title</th>
          <th>Type</th>
          <th>Creator</th>
          <th>Zipcodes</th>
          <th>Closes</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in results" :key="`${r.type}-${r.id}`">
          <td>{{ r.title }}</td>
          <td>{{ r.type }}</td>
          <td>{{ r.creatorEmail }}</td>
          <td class="zips">{{ r.zipcodes.join(', ') }}</td>
          <td>{{ r.closeDate ? new Date(r.closeDate).toLocaleString() : 'No close date' }}</td>
          <td class="actions">
            <router-link :to="`/polls/${routeMap(r.type)}/${r.id}/results`">
              View results
            </router-link>
            <router-link
              v-if="auth.isAuthenticated"
              :to="`/polls/${routeMap(r.type)}/${r.id}`"
            >
              Vote →
            </router-link>
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
h1 {
  margin-bottom: 0.5rem;
  color: #1a365d;
}
.hint {
  margin: 0 0 1.5rem;
  color: #4a5568;
  font-size: 0.95rem;
}
.actions {
  display: flex;
  gap: 0.75rem;
  white-space: nowrap;
}
.filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.75rem;
  align-items: end;
  margin-bottom: 1.5rem;
  padding: 1rem;
  background: #f7fafc;
  border-radius: 6px;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: #2d3748;
}
input,
select {
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font: inherit;
  font-weight: 400;
}
button.primary {
  padding: 0.55rem 1rem;
  background: #1a365d;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  height: fit-content;
}
button.primary:disabled { opacity: 0.6; cursor: not-allowed; }
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
.grid th { background: #f7fafc; }
.zips { font-family: monospace; }
.error { color: #c53030; }
.hint { color: #718096; }
</style>
