<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import QuestionnaireResponseForm from '@/components/QuestionnaireResponseForm.vue'
import ElectionResponseForm from '@/components/ElectionResponseForm.vue'
import BallotMeasureResponseForm from '@/components/BallotMeasureResponseForm.vue'

const route = useRoute()
const type = computed(() => String(route.params.type))
const id = computed(() => Number(route.params.id))
</script>

<template>
  <div class="mx-auto max-w-3xl py-8">
    <QuestionnaireResponseForm
      v-if="type === 'questionnaire'"
      :id="id"
    />
    <ElectionResponseForm
      v-else-if="type === 'election'"
      :id="id"
    />
    <BallotMeasureResponseForm
      v-else-if="type === 'ballot-measure'"
      :id="id"
    />
    <div v-else class="rounded-md border border-orange-400 bg-orange-50 p-4">
      <h1 class="mb-2 text-xl font-semibold text-slate-800">{{ type }}</h1>
      <p class="mb-2 text-sm text-slate-700">Unknown poll type.</p>
      <router-link to="/polls/search" class="text-sm font-semibold text-slate-800 underline">
        Back to search
      </router-link>
    </div>
  </div>
</template>
