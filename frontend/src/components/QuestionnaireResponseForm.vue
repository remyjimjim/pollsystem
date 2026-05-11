<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

interface QuestionDto { id: number; text: string }
interface QuestionnaireDto {
  id: number
  title: string
  summary: string
  status: string
  closeDate: string | null
  questions: QuestionDto[]
}

interface MyResponseDto {
  questionId: number
  response: string
  comment: string | null
  dateSubmitted: string
  lastModified: string | null
}

interface MyResponsesDto {
  questionnaireId: number
  hasResponses: boolean
  firstSubmittedAt: string | null
  responses: MyResponseDto[]
}

const props = defineProps<{ id: number }>()
const router = useRouter()

const poll = ref<QuestionnaireDto | null>(null)
const mine = ref<MyResponsesDto | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref<string | null>(null)
const closedReason = ref<string | null>(null)
const message = ref<string | null>(null)

const answers = reactive<Record<number, { response: string; comment: string }>>({})
type Mode = 'fresh' | 'editing' | 'readonly' | 'choosing'
const mode = ref<Mode>('fresh')

const isClosed = computed(() => {
  const p = poll.value
  if (!p) return false
  if (p.status !== 'PUBLISHED') return true
  if (!p.closeDate) return false
  return new Date(p.closeDate).getTime() <= Date.now()
})

async function load() {
  loading.value = true
  error.value = null
  closedReason.value = null
  try {
    const pollRes = await axios.get<QuestionnaireDto>(
      `/api/polls/questionnaires/${props.id}`
    )
    poll.value = pollRes.data
    if (isClosed.value) {
      closedReason.value = 'This poll is no longer available for responses.'
      return
    }
    const mineRes = await axios.get<MyResponsesDto>(
      `/api/polls/questionnaires/${props.id}/responses/me`
    )
    mine.value = mineRes.data
    seedAnswers()
    mode.value = mine.value.hasResponses ? 'choosing' : 'fresh'
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load poll'
  } finally {
    loading.value = false
  }
}

function seedAnswers() {
  if (!poll.value) return
  const prev = new Map((mine.value?.responses ?? []).map(r => [r.questionId, r]))
  for (const q of poll.value.questions) {
    const p = prev.get(q.id)
    answers[q.id] = {
      response: p?.response ?? '',
      comment: p?.comment ?? ''
    }
  }
}

function chooseEdit() { mode.value = 'editing' }
function chooseReadonly() { mode.value = 'readonly' }

async function submit() {
  if (!poll.value) return
  for (const q of poll.value.questions) {
    if (!answers[q.id]?.response.trim()) {
      error.value = `Please answer: "${q.text}"`
      return
    }
  }
  submitting.value = true
  error.value = null
  try {
    await axios.post(`/api/polls/questionnaires/${props.id}/responses`, {
      answers: poll.value.questions.map(q => ({
        questionId: q.id,
        response: answers[q.id].response.trim(),
        comment: answers[q.id].comment.trim() || null
      }))
    })
    message.value = 'Responses submitted successfully!'
    setTimeout(() => {
      router.push(`/polls/questionnaire/${props.id}/results`)
    }, 600)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Submission failed'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-if="loading" class="text-sm text-slate-600">Loading…</div>
  <div v-else-if="error" class="text-sm text-red-700">{{ error }}</div>
  <div v-else-if="closedReason" class="rounded-md border border-orange-400 bg-orange-50 p-4">
    <p class="mb-2 text-sm text-orange-900">{{ closedReason }}</p>
    <router-link
      :to="`/polls/questionnaire/${props.id}/results`"
      class="text-sm font-semibold text-slate-800 underline"
    >View results</router-link>
  </div>

  <div v-else-if="poll" class="flex flex-col gap-4">
    <header class="mb-2">
      <h2 class="mb-1 text-xl font-semibold text-slate-800">{{ poll.title }}</h2>
      <p class="m-0 whitespace-pre-wrap text-slate-600">{{ poll.summary }}</p>
      <p v-if="poll.closeDate" class="mt-2 text-sm text-slate-500">
        Closes {{ new Date(poll.closeDate).toLocaleString() }}
      </p>
    </header>

    <div v-if="mode === 'choosing' && mine" class="rounded-md border border-sky-300 bg-sky-50 p-4">
      <p class="mb-2 text-sm text-slate-700">
        You submitted answers on
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
      Your previous responses are shown below. They cannot be modified here.
    </div>

    <fieldset
      v-for="q in poll.questions"
      :key="q.id"
      :disabled="mode === 'readonly'"
      class="rounded-md border border-slate-200 p-4 disabled:opacity-90"
    >
      <legend class="px-2 text-sm font-semibold text-slate-700">{{ q.text }}</legend>
      <div class="mb-2 flex flex-col gap-1 text-sm text-slate-600">
        <span>Answer</span>
        <div class="flex gap-4">
          <label class="flex items-center gap-2 text-base text-slate-700">
            <input
              type="radio"
              :name="`q-${q.id}`"
              value="Yes"
              v-model="answers[q.id].response"
              required
            />
            Yes
          </label>
          <label class="flex items-center gap-2 text-base text-slate-700">
            <input
              type="radio"
              :name="`q-${q.id}`"
              value="No"
              v-model="answers[q.id].response"
              required
            />
            No
          </label>
        </div>
      </div>
      <label class="mb-2 flex flex-col gap-1 text-sm text-slate-600">
        Comment (optional)
        <input
          v-model="answers[q.id].comment"
          type="text"
          class="rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
      </label>
    </fieldset>

    <p v-if="message" class="text-sm text-green-700">{{ message }}</p>

    <div v-if="mode !== 'readonly' && mode !== 'choosing'" class="flex gap-2">
      <button
        @click="submit"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? 'Submitting…' : (mine?.hasResponses ? 'Update responses' : 'Submit responses') }}
      </button>
    </div>
  </div>
</template>
