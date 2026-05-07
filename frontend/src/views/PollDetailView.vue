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
  <div class="view">
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
    <div v-else class="todo">
      <h1>{{ type }}</h1>
      <p>Unknown poll type.</p>
      <router-link to="/polls/search">Back to search</router-link>
    </div>
  </div>
</template>

<style scoped>
.view {
  padding: 2rem 0;
  max-width: 720px;
  margin: 0 auto;
}
.todo {
  padding: 1rem;
  background: #fffaf0;
  border: 1px solid #ed8936;
  border-radius: 6px;
}
h1 {
  margin: 0 0 0.5rem;
  color: #1a365d;
}
</style>
