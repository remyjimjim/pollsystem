<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

interface CandidateDto {
  id: number
  name: string
  affiliation: string
  officeId: number
  officeName: string
}

interface ElectionDto {
  id: number
  title: string
  date: string
  zipcode: string
  status: string
  closeDate: string | null
  candidates: CandidateDto[]
}

interface MyCandidateResponseDto {
  candidateId: number
  response: boolean
  comment: string | null
  dateSubmitted: string
  lastModified: string | null
}

interface MyElectionResponsesDto {
  electionId: number
  hasResponses: boolean
  firstSubmittedAt: string | null
  responses: MyCandidateResponseDto[]
}

const props = defineProps<{ id: number }>()
const router = useRouter()

const election = ref<ElectionDto | null>(null)
const mine = ref<MyElectionResponsesDto | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref<string | null>(null)
const closedReason = ref<string | null>(null)
const message = ref<string | null>(null)

const answers = reactive<Record<number, { response: boolean | null; comment: string }>>({})

type Mode = 'fresh' | 'editing' | 'readonly' | 'choosing'
const mode = ref<Mode>('fresh')

const isClosed = computed(() => {
  const e = election.value
  if (!e) return false
  if (e.status !== 'PUBLISHED') return true
  if (!e.closeDate) return false
  return new Date(e.closeDate).getTime() <= Date.now()
})

async function load() {
  loading.value = true
  error.value = null
  closedReason.value = null
  try {
    const res = await axios.get<ElectionDto>(`/api/polls/elections/${props.id}`)
    election.value = res.data
    if (isClosed.value) {
      closedReason.value = 'This poll is no longer available for responses.'
      return
    }
    const mineRes = await axios.get<MyElectionResponsesDto>(
      `/api/polls/elections/${props.id}/responses/me`
    )
    mine.value = mineRes.data
    seedAnswers()
    mode.value = mine.value.hasResponses ? 'choosing' : 'fresh'
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load election'
  } finally {
    loading.value = false
  }
}

function seedAnswers() {
  if (!election.value) return
  const prev = new Map((mine.value?.responses ?? []).map(r => [r.candidateId, r]))
  for (const c of election.value.candidates) {
    const p = prev.get(c.id)
    answers[c.id] = {
      response: p ? p.response : null,
      comment: p?.comment ?? ''
    }
  }
}

function chooseEdit() { mode.value = 'editing' }
function chooseReadonly() { mode.value = 'readonly' }

async function submit() {
  if (!election.value) return
  for (const c of election.value.candidates) {
    if (answers[c.id]?.response == null) {
      error.value = `Please answer Yes or No for: ${c.name}`
      return
    }
  }
  submitting.value = true
  error.value = null
  try {
    await axios.post(`/api/polls/elections/${props.id}/responses`, {
      answers: election.value.candidates.map(c => ({
        candidateId: c.id,
        response: answers[c.id].response,
        comment: answers[c.id].comment.trim() || null
      }))
    })
    message.value = 'Responses submitted successfully!'
    setTimeout(() => router.push(`/polls/election/${props.id}/results`), 600)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Submission failed'
  } finally {
    submitting.value = false
  }
}

const groupedByOffice = computed(() => {
  const map = new Map<string, CandidateDto[]>()
  for (const c of election.value?.candidates ?? []) {
    const list = map.get(c.officeName) ?? []
    list.push(c)
    map.set(c.officeName, list)
  }
  return Array.from(map.entries())
})

onMounted(load)
</script>

<template>
  <div v-if="loading" class="text-sm text-slate-600">Loading…</div>
  <div v-else-if="error" class="text-sm text-red-700">{{ error }}</div>
  <div v-else-if="closedReason" class="rounded-md border border-orange-400 bg-orange-50 p-4">
    <p class="mb-2 text-sm text-orange-900">{{ closedReason }}</p>
    <router-link
      :to="`/polls/election/${props.id}/results`"
      class="text-sm font-semibold text-slate-800 underline"
    >
      View results
    </router-link>
  </div>

  <div v-else-if="election" class="flex flex-col gap-4">
    <header class="mb-2">
      <h2 class="mb-1 text-xl font-semibold text-slate-800">{{ election.title }}</h2>
      <p class="m-0 text-sm text-slate-500">
        Election date: {{ new Date(election.date).toLocaleDateString() }}
        · Zipcode {{ election.zipcode }}
        <template v-if="election.closeDate">
          · Closes {{ new Date(election.closeDate).toLocaleString() }}
        </template>
      </p>
    </header>

    <div v-if="mode === 'choosing' && mine" class="rounded-md border border-sky-300 bg-sky-50 p-4">
      <p class="mb-2 text-sm text-slate-700">
        You voted on
        <strong class="font-semibold">{{ new Date(mine.firstSubmittedAt!).toLocaleDateString() }}</strong>.
        Would you like to change your responses?
      </p>
      <div class="flex gap-2">
        <button
          @click="chooseEdit"
          class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900"
        >Yes, edit</button>
        <button
          @click="chooseReadonly"
          class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50"
        >No, just review</button>
      </div>
    </div>

    <div
      v-if="mode === 'readonly'"
      class="rounded-md border border-slate-300 bg-slate-50 p-3 text-sm text-slate-600"
    >
      Your previous votes are shown below. They cannot be modified here.
    </div>

    <fieldset
      v-for="[officeName, group] in groupedByOffice"
      :key="officeName"
      :disabled="mode === 'readonly'"
      class="rounded-md border border-slate-200 p-4 disabled:opacity-90"
    >
      <legend class="px-2 text-sm font-semibold text-slate-700">{{ officeName }}</legend>
      <div
        v-for="c in group"
        :key="c.id"
        class="grid grid-cols-[2fr_1fr] gap-2 border-b border-slate-100 py-2 last:border-b-0"
      >
        <div class="flex flex-col">
          <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
          <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
        </div>
        <div class="flex gap-3">
          <label class="flex items-center gap-1 text-sm">
            <input type="radio" :name="`c-${c.id}`" :value="true" v-model="answers[c.id].response" />
            Yes
          </label>
          <label class="flex items-center gap-1 text-sm">
            <input type="radio" :name="`c-${c.id}`" :value="false" v-model="answers[c.id].response" />
            No
          </label>
        </div>
        <input
          v-model="answers[c.id].comment"
          type="text"
          placeholder="Comment (optional)"
          class="col-span-2 rounded border border-slate-200 p-2 text-sm focus:border-slate-500 focus:outline-none"
        />
      </div>
    </fieldset>

    <p v-if="message" class="m-0 text-sm text-green-700">{{ message }}</p>

    <div v-if="mode !== 'readonly' && mode !== 'choosing'" class="flex gap-2">
      <button
        @click="submit"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? 'Submitting…' : (mine?.hasResponses ? 'Update votes' : 'Submit votes') }}
      </button>
    </div>
  </div>
</template>
