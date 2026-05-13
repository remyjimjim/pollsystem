<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import axios from 'axios'

const { t } = useI18n()

interface QuestionResultDto {
  questionId: number
  text: string
  totalResponses: number
  byAnswer: Record<string, number>
}
interface QuestionnaireResultsDto {
  questionnaireId: number
  title: string
  totalRespondents: number
  perQuestion: QuestionResultDto[]
  filterApplied: Record<string, string> | null
  suppressed: boolean
  suppressionMessage: string | null
}

interface CandidateResultDto {
  candidateId: number
  name: string
  affiliation: string
  officeName: string
  yes: number
  no: number
  total: number
}
interface ElectionResultsDto {
  electionId: number
  title: string
  totalRespondents: number
  perCandidate: CandidateResultDto[]
  filterApplied: Record<string, string> | null
  suppressed: boolean
  suppressionMessage: string | null
}

interface BallotMeasureResultsDto {
  measureId: number
  title: string
  totalRespondents: number
  yes: number
  no: number
  filterApplied: Record<string, string> | null
  suppressed: boolean
  suppressionMessage: string | null
}

const route = useRoute()
const type = computed(() => String(route.params.type))
const id = computed(() => Number(route.params.id))

const qData = ref<QuestionnaireResultsDto | null>(null)
const eData = ref<ElectionResultsDto | null>(null)
const bData = ref<BallotMeasureResultsDto | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const zipFilter = ref('')

const isSupported = computed(
  () => type.value === 'questionnaire' || type.value === 'election' || type.value === 'ballot-measure'
)

const data = computed<{
  title: string
  totalRespondents: number
  filterApplied: Record<string, string> | null
  suppressed: boolean
  suppressionMessage: string | null
} | null>(() => {
  if (type.value === 'questionnaire') return qData.value
  if (type.value === 'election') return eData.value
  if (type.value === 'ballot-measure') return bData.value
  return null
})

async function load() {
  if (!isSupported.value) return
  loading.value = true
  error.value = null
  try {
    const params: Record<string, string> = {}
    if (zipFilter.value.trim()) params.zipcode = zipFilter.value.trim()
    if (type.value === 'questionnaire') {
      const res = await axios.get<QuestionnaireResultsDto>(
        `/api/polls/questionnaires/${id.value}/results`,
        { params }
      )
      qData.value = res.data
      eData.value = null
      bData.value = null
    } else if (type.value === 'election') {
      const res = await axios.get<ElectionResultsDto>(
        `/api/polls/elections/${id.value}/results`,
        { params }
      )
      eData.value = res.data
      qData.value = null
      bData.value = null
    } else {
      const res = await axios.get<BallotMeasureResultsDto>(
        `/api/polls/ballot-measures/${id.value}/results`,
        { params }
      )
      bData.value = res.data
      qData.value = null
      eData.value = null
    }
  } catch (e: any) {
    error.value = e?.response?.status === 404
      ? t('results.poll404')
      : (e?.response?.data?.message ?? t('results.loadFailed'))
  } finally {
    loading.value = false
  }
}

function clearFilter() {
  zipFilter.value = ''
  load()
}

function pct(count: number, total: number): string {
  if (total === 0) return '0%'
  return `${Math.round((count / total) * 100)}%`
}

const groupedCandidates = computed(() => {
  if (!eData.value) return [] as Array<[string, CandidateResultDto[]]>
  const map = new Map<string, CandidateResultDto[]>()
  for (const c of eData.value.perCandidate) {
    const list = map.get(c.officeName) ?? []
    list.push(c)
    map.set(c.officeName, list)
  }
  return Array.from(map.entries())
})

watch([id, type], load, { immediate: false })
onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-3xl py-8">
    <div v-if="!isSupported" class="rounded-md border border-orange-400 bg-orange-50 p-4">
      <h1 class="mb-2 text-xl font-semibold text-slate-800">{{ $t('results.heading') }}</h1>
      <p class="text-sm text-slate-700">
        {{ $t('results.unsupportedPre') }} <strong class="font-semibold">{{ type }}</strong> {{ $t('results.unsupportedPost') }}
      </p>
    </div>

    <template v-else>
      <h1 v-if="data" class="mb-4 text-2xl font-semibold text-slate-800">
        {{ $t('results.headingFor', { title: data.title }) }}
      </h1>
      <h1 v-else class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('results.heading') }}</h1>

      <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
      <p v-if="loading" class="text-sm text-slate-600">{{ $t('common.loading') }}</p>

      <form
        v-if="data"
        @submit.prevent="load"
        class="mb-4 flex items-end gap-2 rounded-md bg-slate-50 p-3"
      >
        <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
          {{ $t('results.filterLabel') }}
          <input
            v-model="zipFilter"
            type="text"
            maxlength="5"
            pattern="[0-9]{5}"
            :placeholder="$t('results.filterPlaceholder')"
            class="rounded border border-slate-300 p-2 text-sm font-normal focus:border-slate-500 focus:outline-none"
          />
        </label>
        <button
          type="submit"
          :disabled="loading"
          class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {{ $t('common.apply') }}
        </button>
        <button
          v-if="zipFilter"
          type="button"
          @click="clearFilter"
          class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50"
        >
          {{ $t('common.clear') }}
        </button>
      </form>

      <div
        v-if="data?.suppressed"
        class="rounded-md border border-orange-400 bg-orange-50 p-4"
      >
        <p class="m-0 text-sm text-orange-900">{{ data.suppressionMessage }}</p>
      </div>

      <template v-else-if="data">
        <p class="m-0 text-sm text-slate-500">
          {{ $t('results.respondents', { n: data.totalRespondents }) }}
          <span v-if="data.filterApplied">
            · {{ $t('results.filterPrefix') }} {{ Object.entries(data.filterApplied).map(([k, v]) => `${k}=${v}`).join(', ') }}
          </span>
        </p>

        <!-- Questionnaire results -->
        <template v-if="qData">
          <section
            v-for="q in qData.perQuestion"
            :key="q.questionId"
            class="mt-4 rounded-md border border-slate-200 p-4"
          >
            <h3 class="mb-1 text-base font-semibold text-slate-700">{{ q.text }}</h3>
            <p class="m-0 text-sm text-slate-500">{{ $t('results.responseCount', { n: q.totalResponses }) }}</p>
            <table v-if="q.totalResponses > 0" class="mt-2 w-full border-collapse">
              <tbody>
                <tr v-for="[answer, count] in Object.entries(q.byAnswer)" :key="answer">
                  <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">
                    {{ answer }}
                  </td>
                  <td class="rounded bg-slate-100 py-1 align-middle">
                    <div
                      class="rounded bg-sky-500 px-2 py-0.5 text-xs text-white whitespace-nowrap"
                      style="min-width: 2rem"
                      :style="{ width: pct(count, q.totalResponses) }"
                    >
                      <span>{{ count }} ({{ pct(count, q.totalResponses) }})</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else class="m-0 mt-1 text-sm text-slate-500">{{ $t('results.noResponsesYet') }}</p>
          </section>
        </template>

        <!-- Ballot measure results -->
        <template v-if="bData">
          <section class="mt-4 rounded-md border border-slate-200 p-4">
            <table v-if="bData.totalRespondents > 0" class="w-full border-collapse">
              <tbody>
                <tr>
                  <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.yes') }}</td>
                  <td class="rounded bg-slate-100 py-1 align-middle">
                    <div
                      class="rounded bg-green-700 px-2 py-0.5 text-xs text-white whitespace-nowrap"
                      style="min-width: 2rem"
                      :style="{ width: pct(bData.yes, bData.totalRespondents) }"
                    >
                      <span>{{ bData.yes }} ({{ pct(bData.yes, bData.totalRespondents) }})</span>
                    </div>
                  </td>
                </tr>
                <tr>
                  <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.no') }}</td>
                  <td class="rounded bg-slate-100 py-1 align-middle">
                    <div
                      class="rounded bg-red-700 px-2 py-0.5 text-xs text-white whitespace-nowrap"
                      style="min-width: 2rem"
                      :style="{ width: pct(bData.no, bData.totalRespondents) }"
                    >
                      <span>{{ bData.no }} ({{ pct(bData.no, bData.totalRespondents) }})</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else class="m-0 text-sm text-slate-500">{{ $t('results.noVotesYet') }}</p>
          </section>
        </template>

        <!-- Election results -->
        <template v-if="eData">
          <section
            v-for="[officeName, group] in groupedCandidates"
            :key="officeName"
            class="mt-4 rounded-md border border-slate-200 p-4"
          >
            <h3 class="mb-1 text-base font-semibold text-slate-700">{{ officeName }}</h3>
            <div
              v-for="c in group"
              :key="c.candidateId"
              class="border-b border-slate-100 py-3 last:border-b-0"
            >
              <div class="mb-1 flex flex-col">
                <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
                <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
              </div>
              <table v-if="c.total > 0" class="mt-2 w-full border-collapse">
                <tbody>
                  <tr>
                    <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.yes') }}</td>
                    <td class="rounded bg-slate-100 py-1 align-middle">
                      <div
                        class="rounded bg-green-700 px-2 py-0.5 text-xs text-white whitespace-nowrap"
                        style="min-width: 2rem"
                        :style="{ width: pct(c.yes, c.total) }"
                      >
                        <span>{{ c.yes }} ({{ pct(c.yes, c.total) }})</span>
                      </div>
                    </td>
                  </tr>
                  <tr>
                    <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.no') }}</td>
                    <td class="rounded bg-slate-100 py-1 align-middle">
                      <div
                        class="rounded bg-red-700 px-2 py-0.5 text-xs text-white whitespace-nowrap"
                        style="min-width: 2rem"
                        :style="{ width: pct(c.no, c.total) }"
                      >
                        <span>{{ c.no }} ({{ pct(c.no, c.total) }})</span>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-else class="m-0 text-sm text-slate-500">{{ $t('results.noVotesYet') }}</p>
            </div>
          </section>
        </template>
      </template>
    </template>
  </div>
</template>
