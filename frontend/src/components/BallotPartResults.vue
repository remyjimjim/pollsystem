<script setup lang="ts">
// One more part of a district ballot on its results page: the statewide
// election it includes (its races), or a ballot measure. Each part keeps its
// own small-poll privacy: hidden until it has the minimum respondents.
import { computed } from 'vue'
import PublicCommentsList from './PublicCommentsList.vue'

interface Visibility { mode: 'HIDDEN' | 'STEPPED' | 'LIVE'; respondentsSoFar: number }
interface CandidateResult {
  candidateId: number; name: string; affiliation: string; officeName: string
  yes: number | null; no: number | null; yesPct: number; noPct: number; comments?: string[]
}
export interface BallotPart {
  key: string
  title: string
  visibility: Visibility
  suppressed?: boolean
  suppressionMessage?: string | null
  perCandidate?: CandidateResult[]
  measure?: { yes: number | null; no: number | null; yesPct: number; noPct: number; comments?: string[] }
}

const props = defineProps<{ part: BallotPart }>()

const live = computed(() => props.part.visibility.mode === 'LIVE')
const offices = computed(() => {
  const map = new Map<string, CandidateResult[]>()
  for (const c of props.part.perCandidate ?? []) map.set(c.officeName, [...(map.get(c.officeName) ?? []), c])
  return Array.from(map.entries())
})
function label(percent: number, count: number | null | undefined) {
  return live.value && count != null ? `${count} · ${percent}%` : `${percent}%`
}
</script>

<template>
  <div v-if="part.visibility.mode === 'HIDDEN' || part.suppressed" class="mt-4 rounded-md border border-slate-200 p-4 text-sm text-slate-600">
    <h3 class="mb-1 text-base font-semibold text-slate-700">{{ part.title }}</h3>
    {{ part.suppressed ? part.suppressionMessage : $t('results.soFar', { n: part.visibility.respondentsSoFar }, part.visibility.respondentsSoFar) }}
  </div>

  <template v-else-if="part.perCandidate">
    <section v-for="[officeName, group] in offices" :key="officeName" class="mt-4 rounded-md border border-slate-200 p-4">
      <h3 class="mb-1 text-base font-semibold text-slate-700">{{ officeName }}</h3>
      <div v-for="c in group" :key="c.candidateId" class="border-b border-slate-100 py-3 last:border-b-0">
        <div class="mb-1 flex flex-col">
          <strong class="font-semibold text-slate-800">{{ c.name }}</strong>
          <span class="text-sm text-slate-600">{{ c.affiliation }}</span>
        </div>
        <table v-if="c.yesPct + c.noPct > 0" class="mt-2 w-full border-collapse">
          <tbody>
            <tr>
              <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.yes') }}</td>
              <td class="rounded bg-slate-100 py-1 align-middle">
                <div class="rounded bg-sky-500 px-2 py-0.5 text-xs text-[#000] whitespace-nowrap" style="min-width: 2.5rem" :style="{ width: `${c.yesPct}%` }">{{ label(c.yesPct, c.yes) }}</div>
              </td>
            </tr>
            <tr>
              <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.no') }}</td>
              <td class="rounded bg-slate-100 py-1 align-middle">
                <div class="rounded bg-sky-500 px-2 py-0.5 text-xs text-[#000] whitespace-nowrap" style="min-width: 2.5rem" :style="{ width: `${c.noPct}%` }">{{ label(c.noPct, c.no) }}</div>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="m-0 text-sm text-slate-500">{{ $t('results.noVotesYet') }}</p>
        <PublicCommentsList :comments="c.comments ?? []" />
      </div>
    </section>
  </template>

  <section v-else-if="part.measure" class="mt-4 rounded-md border border-slate-200 p-4">
    <h3 class="mb-1 text-base font-semibold text-slate-700">{{ part.title }}</h3>
    <table v-if="part.measure.yesPct + part.measure.noPct > 0" class="mt-2 w-full border-collapse">
      <tbody>
        <tr>
          <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.yes') }}</td>
          <td class="rounded bg-slate-100 py-1 align-middle">
            <div class="rounded bg-sky-500 px-2 py-0.5 text-xs text-[#000] whitespace-nowrap" style="min-width: 2.5rem" :style="{ width: `${part.measure.yesPct}%` }">{{ label(part.measure.yesPct, part.measure.yes) }}</div>
          </td>
        </tr>
        <tr>
          <td class="py-1 pr-3 align-middle font-mono text-sm" style="width: 30%">{{ $t('common.no') }}</td>
          <td class="rounded bg-slate-100 py-1 align-middle">
            <div class="rounded bg-sky-500 px-2 py-0.5 text-xs text-[#000] whitespace-nowrap" style="min-width: 2.5rem" :style="{ width: `${part.measure.noPct}%` }">{{ label(part.measure.noPct, part.measure.no) }}</div>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="m-0 text-sm text-slate-500">{{ $t('results.noVotesYet') }}</p>
    <PublicCommentsList :comments="part.measure.comments ?? []" />
  </section>
</template>
