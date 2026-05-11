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
  <div class="mx-auto max-w-5xl py-8">
    <h1 class="mb-2 text-2xl font-semibold text-slate-800">
      {{ auth.isAuthenticated ? 'Find a Poll' : 'Browse Poll Results' }}
    </h1>
    <p v-if="!auth.isAuthenticated" class="mb-6 text-sm text-slate-600">
      Search and view results for any published poll.
      <router-link to="/login" class="text-slate-800 underline">Sign in</router-link>
      to vote.
    </p>

    <form
      @submit.prevent="search"
      class="mb-6 grid grid-cols-1 items-end gap-3 rounded-md bg-slate-50 p-4 sm:grid-cols-[repeat(auto-fit,minmax(180px,1fr))]"
    >
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        Title contains
        <input
          v-model="filters.title"
          type="text"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        Zipcode
        <input
          v-model="filters.zipcode"
          type="text"
          maxlength="5"
          pattern="[0-9]{5}"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        Creator email
        <input
          v-model="filters.creatorEmail"
          type="text"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        Candidate name (Election only)
        <input
          v-model="filters.candidateName"
          type="text"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        Type
        <select
          v-model="filters.type"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        >
          <option value="">Any</option>
          <option value="Questionnaire">Questionnaire</option>
          <option value="Election">Election</option>
          <option value="BallotMeasure">Ballot Measure</option>
        </select>
      </label>
      <button
        type="submit"
        :disabled="loading"
        class="h-fit rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ loading ? 'Searching…' : 'Search' }}
      </button>
    </form>

    <p v-if="error" class="mb-2 text-sm text-red-700">{{ error }}</p>

    <p v-if="searched && results.length === 0" class="text-sm text-slate-500">
      No active polls match your filters.
    </p>

    <table v-if="results.length > 0" class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Title</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Type</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Creator</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Zipcodes</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Closes</th>
          <th class="border-b border-slate-200 p-2"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in results" :key="`${r.type}-${r.id}`">
          <td class="border-b border-slate-100 p-2">{{ r.title }}</td>
          <td class="border-b border-slate-100 p-2">{{ r.type }}</td>
          <td class="border-b border-slate-100 p-2">{{ r.creatorEmail }}</td>
          <td class="border-b border-slate-100 p-2 font-mono text-xs">
            {{ r.zipcodes.join(', ') }}
          </td>
          <td class="border-b border-slate-100 p-2">
            {{ r.closeDate ? new Date(r.closeDate).toLocaleString() : 'No close date' }}
          </td>
          <td class="border-b border-slate-100 p-2">
            <div class="flex gap-3 whitespace-nowrap">
              <router-link
                :to="`/polls/${routeMap(r.type)}/${r.id}/results`"
                class="text-slate-800 underline"
              >
                View results
              </router-link>
              <router-link
                v-if="auth.isAuthenticated"
                :to="`/polls/${routeMap(r.type)}/${r.id}`"
                class="text-slate-800 underline"
              >
                Vote →
              </router-link>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
