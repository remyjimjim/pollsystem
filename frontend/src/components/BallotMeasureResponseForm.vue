<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface BallotMeasureDto {
  id: number
  title: string
  summary: string
  effectiveDate: string
  zipcode: string
  status: string
  closeDate: string | null
  electionTitle: string
}

interface MyBallotResponseDto {
  measureId: number
  hasResponse: boolean
  response: boolean | null
  comment: string | null
  dateSubmitted: string | null
  lastModified: string | null
}

const props = defineProps<{ id: number }>()
const router = useRouter()

const measure = ref<BallotMeasureDto | null>(null)
const mine = ref<MyBallotResponseDto | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref<string | null>(null)
const closedReason = ref<string | null>(null)
const message = ref<string | null>(null)

const answer = ref<boolean | null>(null)
const comment = ref('')

type Mode = 'fresh' | 'editing' | 'readonly' | 'choosing'
const mode = ref<Mode>('fresh')

const isClosed = computed(() => {
  const m = measure.value
  if (!m) return false
  if (m.status !== 'PUBLISHED') return true
  if (!m.closeDate) return false
  return new Date(m.closeDate).getTime() <= Date.now()
})

async function load() {
  loading.value = true
  error.value = null
  closedReason.value = null
  try {
    const res = await axios.get<BallotMeasureDto>(`/api/polls/ballot-measures/${props.id}`)
    measure.value = res.data
    if (isClosed.value) {
      closedReason.value = t('ballotMeasure.closedNote')
      return
    }
    const mineRes = await axios.get<MyBallotResponseDto>(
      `/api/polls/ballot-measures/${props.id}/responses/me`
    )
    mine.value = mineRes.data
    if (mine.value.hasResponse) {
      answer.value = mine.value.response
      comment.value = mine.value.comment ?? ''
      mode.value = 'choosing'
    } else {
      mode.value = 'fresh'
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('ballotMeasure.loadFailed')
  } finally {
    loading.value = false
  }
}

function chooseEdit() { mode.value = 'editing' }
function chooseReadonly() { mode.value = 'readonly' }

async function submit() {
  if (answer.value == null) {
    error.value = t('ballotMeasure.validation.pleaseAnswer')
    return
  }
  submitting.value = true
  error.value = null
  try {
    await axios.post(`/api/polls/ballot-measures/${props.id}/responses`, {
      response: answer.value,
      comment: comment.value.trim() || null
    })
    message.value = t('ballotMeasure.submittedOk')
    setTimeout(() => router.push(`/polls/ballot-measure/${props.id}/results`), 600)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('ballotMeasure.submissionFailed')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-if="loading" class="text-sm text-slate-600">{{ $t('common.loading') }}</div>
  <div v-else-if="error" class="text-sm text-red-700">{{ error }}</div>
  <div v-else-if="closedReason" class="rounded-md border border-orange-400 bg-orange-50 p-4">
    <p class="mb-2 text-sm text-orange-900">{{ closedReason }}</p>
    <router-link
      :to="`/polls/ballot-measure/${props.id}/results`"
      class="text-sm font-semibold text-slate-800 underline"
    >{{ $t('nav.viewResults') }}</router-link>
  </div>

  <div v-else-if="measure" class="flex flex-col gap-4">
    <header class="mb-2">
      <h2 class="mb-1 text-xl font-semibold text-slate-800">{{ measure.title }}</h2>
      <p class="mb-2 text-sm text-slate-500">
        {{ $t('ballotMeasure.partOf') }} {{ measure.electionTitle }}
        · {{ $t('ballotMeasure.zipcodeLabel') }} {{ measure.zipcode }}
        · {{ $t('ballotMeasure.effectiveLabel') }} {{ new Date(measure.effectiveDate).toLocaleDateString() }}
        <template v-if="measure.closeDate">
          · {{ $t('ballotMeasure.closesLabel') }} {{ new Date(measure.closeDate).toLocaleString() }}
        </template>
      </p>
      <p
        class="m-0 whitespace-pre-wrap rounded-r bg-slate-50 p-3 text-slate-700 [border-left:3px_solid_#4299e1]"
      >
        {{ measure.summary }}
      </p>
    </header>

    <div v-if="mode === 'choosing' && mine" class="rounded-md border border-sky-300 bg-sky-50 p-4">
      <p class="mb-2 text-sm text-slate-700">
        {{ $t('ballotMeasure.votedOn') }}
        <strong class="font-semibold">{{ new Date(mine.dateSubmitted!).toLocaleDateString() }}</strong>:
        <strong class="font-semibold">{{ mine.response ? $t('common.yes') : $t('common.no') }}</strong>.
        {{ $t('ballotMeasure.wouldYouChange') }}
      </p>
      <div class="flex gap-2">
        <button
          @click="chooseEdit"
          class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900"
        >{{ $t('ballotMeasure.yesEdit') }}</button>
        <button
          @click="chooseReadonly"
          class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50"
        >{{ $t('ballotMeasure.noReview') }}</button>
      </div>
    </div>

    <div
      v-if="mode === 'readonly'"
      class="rounded-md border border-slate-300 bg-slate-50 p-3 text-sm text-slate-600"
    >
      {{ $t('ballotMeasure.readonlyNote') }}
    </div>

    <fieldset
      :disabled="mode === 'readonly'"
      class="rounded-md border border-slate-200 p-4 disabled:opacity-90"
    >
      <legend class="px-2 text-sm font-semibold text-slate-700">{{ $t('ballotMeasure.yourVoteLegend') }}</legend>
      <div class="mb-3 flex gap-6">
        <label class="flex items-center gap-2 text-sm">
          <input type="radio" :value="true" v-model="answer" name="vote" />
          {{ $t('common.yes') }}
        </label>
        <label class="flex items-center gap-2 text-sm">
          <input type="radio" :value="false" v-model="answer" name="vote" />
          {{ $t('common.no') }}
        </label>
      </div>
      <label class="flex flex-col gap-1 text-sm text-slate-600">
        {{ $t('common.comment') }}
        <textarea
          v-model="comment"
          rows="2"
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
        {{ submitting ? $t('common.submitting') : (mine?.hasResponse ? $t('ballotMeasure.updateVote') : $t('ballotMeasure.submitVote')) }}
      </button>
    </div>
  </div>
</template>
