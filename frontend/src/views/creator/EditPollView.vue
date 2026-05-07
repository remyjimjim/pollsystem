<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import QuestionnaireForm from '@/components/QuestionnaireForm.vue'
import ElectionForm from '@/components/ElectionForm.vue'
import BallotMeasureForm from '@/components/BallotMeasureForm.vue'

interface QuestionDto { id: number; text: string }
interface DomainDto { zipcode: string; countyId: number; stateId: number }
interface QuestionnaireDto {
  id: number
  pollTypeId: number
  title: string
  summary: string
  status: string
  closeDate: string | null
  questions: QuestionDto[]
  domains: DomainDto[]
}

interface CandidateDto {
  id: number
  name: string
  affiliation: string
  officeId: number
  officeName: string
}
interface ElectionDto {
  id: number
  pollTypeId: number
  title: string
  date: string
  zipcode: string
  status: string
  closeDate: string | null
  candidates: CandidateDto[]
}

interface BallotMeasureDto {
  id: number
  pollTypeId: number
  electionId: number
  title: string
  summary: string
  effectiveDate: string
  status: string
  closeDate: string | null
}

const route = useRoute()
const type = computed(() => String(route.params.type))
const id = computed(() => Number(route.params.id))

const questionnaire = ref<QuestionnaireDto | null>(null)
const election = ref<ElectionDto | null>(null)
const ballotMeasure = ref<BallotMeasureDto | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    if (type.value === 'questionnaire') {
      const res = await axios.get<QuestionnaireDto>(`/api/polls/questionnaires/${id.value}`)
      if (res.data.status !== 'DRAFT') {
        error.value = 'Only DRAFT polls can be edited.'
      } else {
        questionnaire.value = res.data
      }
    } else if (type.value === 'election') {
      const res = await axios.get<ElectionDto>(`/api/polls/elections/${id.value}`)
      if (res.data.status !== 'DRAFT') {
        error.value = 'Only DRAFT polls can be edited.'
      } else {
        election.value = res.data
      }
    } else if (type.value === 'ballot-measure') {
      const res = await axios.get<BallotMeasureDto>(`/api/polls/ballot-measures/${id.value}`)
      if (res.data.status !== 'DRAFT') {
        error.value = 'Only DRAFT polls can be edited.'
      } else {
        ballotMeasure.value = res.data
      }
    } else {
      error.value = `Editing ${type.value} polls is not supported yet.`
    }
  } catch (e: any) {
    error.value = e?.response?.status === 404
      ? 'Poll not found.'
      : (e?.response?.data?.message ?? 'Failed to load poll')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="view">
    <h1>Edit Draft</h1>
    <p v-if="loading">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>

    <QuestionnaireForm
      v-else-if="questionnaire"
      :poll-type-id="questionnaire.pollTypeId"
      :initial="{
        id: questionnaire.id,
        pollTypeId: questionnaire.pollTypeId,
        title: questionnaire.title,
        summary: questionnaire.summary,
        closeDate: questionnaire.closeDate,
        questions: questionnaire.questions.map(q => ({ text: q.text })),
        zipcodes: questionnaire.domains.map(d => d.zipcode)
      }"
    />

    <ElectionForm
      v-else-if="election"
      :poll-type-id="election.pollTypeId"
      :initial="{
        id: election.id,
        pollTypeId: election.pollTypeId,
        title: election.title,
        date: election.date,
        zipcode: election.zipcode,
        closeDate: election.closeDate,
        candidates: election.candidates.map(c => ({
          name: c.name,
          affiliation: c.affiliation,
          officeName: c.officeName
        }))
      }"
    />

    <BallotMeasureForm
      v-else-if="ballotMeasure"
      :poll-type-id="ballotMeasure.pollTypeId"
      :initial="{
        id: ballotMeasure.id,
        pollTypeId: ballotMeasure.pollTypeId,
        electionId: ballotMeasure.electionId,
        title: ballotMeasure.title,
        summary: ballotMeasure.summary,
        effectiveDate: ballotMeasure.effectiveDate,
        closeDate: ballotMeasure.closeDate
      }"
    />
  </div>
</template>

<style scoped>
.view { padding: 2rem 0; max-width: 720px; margin: 0 auto; }
h1 { margin-bottom: 1rem; color: #1a365d; }
.error { color: #c53030; }
</style>
