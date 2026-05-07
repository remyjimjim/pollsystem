<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'

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
      ? 'Poll not found'
      : (e?.response?.data?.message ?? 'Failed to load results')
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
  <div class="view">
    <div v-if="!isSupported" class="todo">
      <h1>Results</h1>
      <p>Results for <strong>{{ type }}</strong> are not yet supported.</p>
    </div>

    <template v-else>
      <h1 v-if="data">{{ data.title }} — Results</h1>
      <h1 v-else>Results</h1>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading">Loading…</p>

      <form v-if="data" @submit.prevent="load" class="filter-bar">
        <label>
          Filter by zipcode
          <input
            v-model="zipFilter"
            type="text"
            maxlength="5"
            pattern="[0-9]{5}"
            placeholder="e.g. 90001"
          />
        </label>
        <button type="submit" :disabled="loading" class="primary">Apply</button>
        <button v-if="zipFilter" type="button" @click="clearFilter">Clear</button>
      </form>

      <div v-if="data?.suppressed" class="suppressed">
        <p>{{ data.suppressionMessage }}</p>
      </div>

      <template v-else-if="data">
        <p class="hint">
          {{ data.totalRespondents }} respondent(s)
          <span v-if="data.filterApplied">
            · filter: {{ Object.entries(data.filterApplied).map(([k, v]) => `${k}=${v}`).join(', ') }}
          </span>
        </p>

        <!-- Questionnaire results -->
        <template v-if="qData">
          <section v-for="q in qData.perQuestion" :key="q.questionId" class="block">
            <h3>{{ q.text }}</h3>
            <p class="hint">{{ q.totalResponses }} responses</p>
            <table v-if="q.totalResponses > 0" class="bars">
              <tbody>
                <tr v-for="[answer, count] in Object.entries(q.byAnswer)" :key="answer">
                  <td class="label">{{ answer }}</td>
                  <td class="bar-cell">
                    <div class="bar" :style="{ width: pct(count, q.totalResponses) }">
                      <span>{{ count }} ({{ pct(count, q.totalResponses) }})</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else class="hint">No responses yet.</p>
          </section>
        </template>

        <!-- Ballot measure results -->
        <template v-if="bData">
          <section class="block">
            <table v-if="bData.totalRespondents > 0" class="bars">
              <tbody>
                <tr>
                  <td class="label">Yes</td>
                  <td class="bar-cell">
                    <div class="bar yes" :style="{ width: pct(bData.yes, bData.totalRespondents) }">
                      <span>{{ bData.yes }} ({{ pct(bData.yes, bData.totalRespondents) }})</span>
                    </div>
                  </td>
                </tr>
                <tr>
                  <td class="label">No</td>
                  <td class="bar-cell">
                    <div class="bar no" :style="{ width: pct(bData.no, bData.totalRespondents) }">
                      <span>{{ bData.no }} ({{ pct(bData.no, bData.totalRespondents) }})</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else class="hint">No votes yet.</p>
          </section>
        </template>

        <!-- Election results -->
        <template v-if="eData">
          <section v-for="[officeName, group] in groupedCandidates" :key="officeName" class="block">
            <h3>{{ officeName }}</h3>
            <div v-for="c in group" :key="c.candidateId" class="candidate-result">
              <div class="candidate-info">
                <strong>{{ c.name }}</strong>
                <span class="affiliation">{{ c.affiliation }}</span>
              </div>
              <table v-if="c.total > 0" class="bars">
                <tbody>
                  <tr>
                    <td class="label">Yes</td>
                    <td class="bar-cell">
                      <div class="bar yes" :style="{ width: pct(c.yes, c.total) }">
                        <span>{{ c.yes }} ({{ pct(c.yes, c.total) }})</span>
                      </div>
                    </td>
                  </tr>
                  <tr>
                    <td class="label">No</td>
                    <td class="bar-cell">
                      <div class="bar no" :style="{ width: pct(c.no, c.total) }">
                        <span>{{ c.no }} ({{ pct(c.no, c.total) }})</span>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-else class="hint">No votes yet.</p>
            </div>
          </section>
        </template>
      </template>
    </template>
  </div>
</template>

<style scoped>
.view { padding: 2rem 0; max-width: 800px; margin: 0 auto; }
h1 { color: #1a365d; margin-bottom: 1rem; }
h3 { color: #2d3748; margin: 0 0 0.25rem; }
.filter-bar {
  display: flex;
  gap: 0.5rem;
  align-items: end;
  background: #f7fafc;
  padding: 0.75rem;
  border-radius: 6px;
  margin-bottom: 1rem;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: #2d3748;
}
input {
  padding: 0.4rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font: inherit;
  font-weight: 400;
}
button {
  padding: 0.5rem 1rem;
  background: white;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  cursor: pointer;
}
button.primary {
  background: #1a365d;
  color: white;
  border-color: #1a365d;
}
button:disabled { opacity: 0.6; cursor: not-allowed; }
.suppressed {
  background: #fffaf0;
  border: 1px solid #ed8936;
  padding: 1rem;
  border-radius: 6px;
}
.block {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1rem;
  margin-bottom: 1rem;
}
.candidate-result {
  padding: 0.75rem 0;
  border-bottom: 1px solid #edf2f7;
}
.candidate-result:last-child { border-bottom: none; }
.candidate-info {
  display: flex;
  flex-direction: column;
  margin-bottom: 0.4rem;
}
.affiliation { color: #4a5568; font-size: 0.85rem; }
.bars {
  width: 100%;
  border-collapse: collapse;
  margin-top: 0.5rem;
}
.bars td {
  padding: 0.25rem 0;
  vertical-align: middle;
}
.bars td.label {
  font-family: monospace;
  width: 30%;
  padding-right: 0.75rem;
  font-size: 0.85rem;
}
.bar-cell {
  background: #edf2f7;
  border-radius: 4px;
}
.bar {
  background: #4299e1;
  color: white;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  min-width: 2rem;
  font-size: 0.8rem;
  white-space: nowrap;
}
.bar.yes { background: #2f855a; }
.bar.no { background: #c53030; }
.hint { color: #718096; font-size: 0.9rem; margin: 0; }
.error { color: #c53030; }
.todo {
  padding: 1rem;
  background: #fffaf0;
  border: 1px solid #ed8936;
  border-radius: 6px;
}
</style>
