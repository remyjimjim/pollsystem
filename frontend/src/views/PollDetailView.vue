<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import axios from 'axios'
import { useRoute, useRouter } from 'vue-router'
import QuestionnaireResponseForm from '@/components/QuestionnaireResponseForm.vue'
import ElectionResponseForm from '@/components/ElectionResponseForm.vue'

const route = useRoute()
const router = useRouter()
const type = computed(() => String(route.params.type))
const id = computed(() => Number(route.params.id))
const focusMeasure = computed(() => Number(route.query.measure) || null)
// ?review=1 ("View past ballots"): open your answers read-only, even once closed.
const review = computed(() => route.query.review === '1')

// A ballot measure is voted on with its election (decided 2026-10-15): its own
// page sends you to the election's form, scrolled to the measure.
const measureError = ref(false)
watch([type, id], async ([t, i]) => {
  if (t !== 'ballot-measure') return
  measureError.value = false
  try {
    const m = (await axios.get<{ electionId: number }>(`/api/polls/ballot-measures/${i}`)).data
    router.replace({ path: `/polls/election/${m.electionId}`, query: { measure: String(i) } })
  } catch {
    measureError.value = true
  }
}, { immediate: true })
</script>

<template>
  <div class="mx-auto max-w-3xl py-8">
    <QuestionnaireResponseForm
      v-if="type === 'questionnaire'"
      :id="id"
      :review="review"
    />
    <ElectionResponseForm
      v-else-if="type === 'election'"
      :id="id"
      :key="id"
      :focus-measure="focusMeasure"
      :review="review"
    />
    <template v-else-if="type === 'ballot-measure'">
      <p v-if="measureError" class="text-sm text-red-700">{{ $t('ballotMeasure.loadFailed') }}</p>
      <p v-else class="text-sm text-slate-600">{{ $t('common.loading') }}</p>
    </template>
    <div v-else class="rounded-md border border-orange-400 bg-orange-50 p-4">
      <h1 class="mb-2 text-xl font-semibold text-slate-800">{{ type }}</h1>
      <p class="mb-2 text-sm text-slate-700">{{ $t('pollDetail.unknownType') }}</p>
      <router-link to="/polls/search" class="text-sm font-semibold text-slate-800 underline">
        {{ $t('pollDetail.backToSearch') }}
      </router-link>
    </div>
  </div>
</template>
