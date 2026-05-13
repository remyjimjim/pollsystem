<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import axios from 'axios'
import QuestionnaireForm from '@/components/QuestionnaireForm.vue'
import ElectionForm from '@/components/ElectionForm.vue'
import BallotMeasureForm from '@/components/BallotMeasureForm.vue'

const { t } = useI18n()

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
        error.value = t('creator.editPoll.draftOnly')
      } else {
        questionnaire.value = res.data
      }
    } else if (type.value === 'election') {
      const res = await axios.get<ElectionDto>(`/api/polls/elections/${id.value}`)
      if (res.data.status !== 'DRAFT') {
        error.value = t('creator.editPoll.draftOnly')
      } else {
        election.value = res.data
      }
    } else if (type.value === 'ballot-measure') {
      const res = await axios.get<BallotMeasureDto>(`/api/polls/ballot-measures/${id.value}`)
      if (res.data.status !== 'DRAFT') {
        error.value = t('creator.editPoll.draftOnly')
      } else {
        ballotMeasure.value = res.data
      }
    } else {
      error.value = t('creator.editPoll.notSupported', { type: type.value })
    }
  } catch (e: any) {
    error.value = e?.response?.status === 404
      ? t('creator.editPoll.notFound')
      : (e?.response?.data?.message ?? t('creator.editPoll.loadFailed'))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="mx-auto max-w-3xl py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('creator.editPoll.heading') }}</h1>
    <p v-if="loading" class="text-sm text-slate-600">{{ $t('common.loading') }}</p>
    <p v-else-if="error" class="text-sm text-red-700">{{ error }}</p>

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
