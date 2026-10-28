<script setup lang="ts">
// "View past ballots" (2026-10-28): every poll the member has answered, newest
// vote first; View opens it read-only with their choices highlighted (also
// once it has closed). A midterm district ballot is listed once.
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import ScrollEnds from '@/components/ScrollEnds.vue'

interface MyBallot { type: 'questionnaire' | 'election'; id: number; title: string; votedAt: string; open: boolean }

const { t } = useI18n()
const ballots = ref<MyBallot[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    ballots.value = (await axios.get<MyBallot[]>('/api/my/ballots')).data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('myBallots.loadFailed')
  } finally {
    loading.value = false
  }
})

const viewLink = (b: MyBallot) => ({ path: `/polls/${b.type}/${b.id}`, query: { review: '1' } })
const date = (iso: string) => new Date(iso).toLocaleString()
</script>

<template>
  <div class="mx-auto max-w-4xl py-8">
    <h1 class="mb-1 text-2xl font-semibold text-slate-800">{{ $t('myBallots.title') }}</h1>
    <p class="mb-4 text-sm text-slate-600">{{ $t('myBallots.intro') }}</p>

    <p v-if="loading" class="text-sm text-slate-600">{{ $t('common.loading') }}</p>
    <p v-else-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-else-if="!ballots.length" class="text-sm text-slate-500" data-test="my-ballots-empty">
      {{ $t('myBallots.empty') }}
      <router-link to="/polls/search" class="text-slate-800 underline">{{ $t('myBallots.findPolls') }}</router-link>
    </p>
    <ScrollEnds v-else>
      <table class="hidden w-full border-collapse text-sm sm:table" data-test="my-ballots-table">
        <thead>
          <tr class="bg-slate-50 text-left">
            <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('myBallots.colPoll') }}</th>
            <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('myBallots.colVoted') }}</th>
            <th class="border-b border-slate-200 p-2"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in ballots" :key="`${b.type}-${b.id}`" data-test="my-ballot-row">
            <td class="border-b border-slate-100 p-2">
              {{ b.title }}
              <span v-if="!b.open" class="ml-1 rounded bg-slate-200 px-1.5 py-0.5 text-xs font-semibold text-slate-700">{{ $t('myBallots.closed') }}</span>
            </td>
            <td class="whitespace-nowrap border-b border-slate-100 p-2 text-slate-600">{{ date(b.votedAt) }}</td>
            <td class="border-b border-slate-100 p-2 text-right">
              <router-link :to="viewLink(b)" class="inline-block rounded bg-slate-800 px-3 py-1 text-xs font-semibold text-white no-underline hover:bg-slate-900" data-test="my-ballot-view">
                {{ $t('myBallots.view') }}
              </router-link>
            </td>
          </tr>
        </tbody>
      </table>
      <!-- Phones: one card per ballot. -->
      <ul class="m-0 flex list-none flex-col gap-3 p-0 sm:hidden" data-test="my-ballot-cards">
        <li v-for="b in ballots" :key="`card-${b.type}-${b.id}`" class="flex items-center justify-between gap-3 rounded-md border border-slate-200 p-3 text-sm">
          <div class="min-w-0">
            <div class="font-semibold text-slate-800">
              {{ b.title }}
              <span v-if="!b.open" class="ml-1 rounded bg-slate-200 px-1.5 py-0.5 text-xs font-semibold text-slate-700">{{ $t('myBallots.closed') }}</span>
            </div>
            <div class="text-xs text-slate-500">{{ $t('myBallots.colVoted') }}: {{ date(b.votedAt) }}</div>
          </div>
          <router-link :to="viewLink(b)" class="shrink-0 rounded bg-slate-800 px-3 py-1.5 text-xs font-semibold text-white no-underline hover:bg-slate-900">
            {{ $t('myBallots.view') }}
          </router-link>
        </li>
      </ul>
    </ScrollEnds>
  </div>
</template>
