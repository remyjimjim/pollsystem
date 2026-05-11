<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

interface ZipState {
  code: string
  state: string
}
interface PollSearchResult {
  id: number
  type: 'Questionnaire' | 'Election' | 'BallotMeasure'
  title: string
  creatorEmail: string
  closeDate: string | null
  zipcodes: ZipState[]
}

// Which row's "extra zipcodes" popover is open. null = closed.
const expandedKey = ref<string | null>(null)
function rowKey(r: PollSearchResult): string {
  return `${r.type}-${r.id}`
}
function toggleExpand(r: PollSearchResult, e: MouseEvent) {
  e.stopPropagation()
  expandedKey.value = expandedKey.value === rowKey(r) ? null : rowKey(r)
}
function closeExpanded() {
  expandedKey.value = null
}
function onDocClick(e: MouseEvent) {
  // Close if the click was outside any open popover or its trigger.
  const target = e.target as HTMLElement | null
  if (target?.closest('[data-zip-popover]') || target?.closest('[data-zip-trigger]')) return
  expandedKey.value = null
}
function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') expandedKey.value = null
}
onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onEsc)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onEsc)
})

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
            <template v-if="r.zipcodes.length === 0">—</template>
            <template v-else-if="r.zipcodes.length === 1">
              {{ r.zipcodes[0].code }} <span class="text-slate-500">({{ r.zipcodes[0].state }})</span>
            </template>
            <template v-else>
              <span class="relative inline-block">
                {{ r.zipcodes[0].code }}
                <span class="text-slate-500">({{ r.zipcodes[0].state }})</span>,
                <button
                  type="button"
                  data-zip-trigger
                  @click="toggleExpand(r, $event)"
                  class="ml-0.5 rounded px-1 font-semibold text-slate-800 hover:bg-slate-100 focus:outline-none focus:ring focus:ring-slate-400"
                  :title="`Show ${r.zipcodes.length - 1} more`"
                  :aria-expanded="expandedKey === rowKey(r)"
                >…</button>
                <span class="ml-0.5 text-slate-500">+{{ r.zipcodes.length - 1 }}</span>

                <!-- Anchored popover; closes on outside-click or Esc -->
                <div
                  v-if="expandedKey === rowKey(r)"
                  data-zip-popover
                  role="dialog"
                  class="absolute left-0 top-full z-20 mt-1 w-56 rounded-md border border-slate-300 bg-white shadow-lg"
                >
                  <header class="flex items-center justify-between border-b border-slate-200 px-3 py-2">
                    <span class="text-xs font-semibold text-slate-700">
                      Additional zipcodes ({{ r.zipcodes.length - 1 }})
                    </span>
                    <button
                      type="button"
                      @click.stop="closeExpanded"
                      class="rounded p-0.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus:outline-none focus:ring focus:ring-slate-400"
                      aria-label="Close"
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        viewBox="0 0 20 20"
                        class="h-4 w-4"
                        aria-hidden="true"
                      >
                        <path
                          fill="currentColor"
                          d="M5.7 4.3 4.3 5.7 8.6 10l-4.3 4.3 1.4 1.4L10 11.4l4.3 4.3 1.4-1.4L11.4 10l4.3-4.3-1.4-1.4L10 8.6 5.7 4.3z"
                        />
                      </svg>
                    </button>
                  </header>
                  <ul
                    class="m-0 list-none overflow-y-auto p-0"
                    :style="{ maxHeight: '33vh' }"
                  >
                    <li
                      v-for="z in r.zipcodes.slice(1)"
                      :key="z.code"
                      class="flex justify-between border-b border-slate-100 px-3 py-1.5 last:border-b-0"
                    >
                      <span class="font-mono">{{ z.code }}</span>
                      <span class="text-slate-500">{{ z.state }}</span>
                    </li>
                  </ul>
                </div>
              </span>
            </template>
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
