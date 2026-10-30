<script setup lang="ts">
// A ranked-choice race's results (decided 2026-10-30): the instant-runoff
// count round by round, as the light-blue bars. Each round shows the share of
// the ballots still counting; the last round marks the winner. Totals per
// round only (no "moved votes" detail). Raw counts for supers (live).
import { computed } from 'vue'
import PublicCommentsList from './PublicCommentsList.vue'

export interface RankedRound {
  shares: Record<string, number>
  exhaustedPct: number
  votes: Record<string, number> | null
  exhausted: number | null
  eliminated: number | null
  winner: number | null
}
export interface RankedRace {
  officeId: number
  officeName: string
  candidates: { id: number; name: string; affiliation: string }[]
  rounds: RankedRound[]
  ballots: number | null
}

const props = defineProps<{ race: RankedRace; comments?: Record<number, string[]> }>()
const nameOf = computed(() => Object.fromEntries(props.race.candidates.map(c => [c.id, c.name])))

/** A round's candidates, strongest first. */
function rows(r: RankedRound) {
  return Object.entries(r.shares)
    .map(([id, pct]) => ({ id: Number(id), pct, votes: r.votes?.[id] ?? null }))
    .sort((a, b) => b.pct - a.pct || (nameOf.value[a.id] ?? '').localeCompare(nameOf.value[b.id] ?? ''))
}
const label = (pct: number, votes: number | null) => (votes != null ? `${votes} · ${pct}%` : `${pct}%`)
const allComments = computed(() => props.race.candidates.flatMap(c => props.comments?.[c.id] ?? []))
</script>

<template>
  <section class="mt-4 rounded-md border border-slate-200 p-4" data-test="ranked-race">
    <h3 class="mb-1 text-base font-semibold text-slate-700">{{ race.officeName }}</h3>
    <p v-if="race.ballots != null" class="m-0 mb-2 text-xs text-slate-500">{{ $t('results.ranked.ballots', { n: race.ballots }, race.ballots) }}</p>
    <p v-if="!race.rounds.length" class="m-0 text-sm text-slate-500">{{ $t('results.noVotesYet') }}</p>

    <div v-for="(r, i) in race.rounds" :key="i" class="border-b border-slate-100 py-3 last:border-b-0" data-test="ranked-round">
      <div class="mb-1 text-sm font-semibold text-slate-700">
        {{ i === race.rounds.length - 1 && race.rounds.length > 1 ? $t('results.ranked.finalRound', { n: i + 1 }) : $t('results.ranked.round', { n: i + 1 }) }}
      </div>
      <table class="w-full border-collapse">
        <tbody>
          <tr v-for="row in rows(r)" :key="row.id">
            <td class="py-1 pr-3 align-middle text-sm" style="width: 35%">
              <span class="text-slate-800">{{ nameOf[row.id] }}</span>
              <span v-if="r.winner === row.id" class="ml-1 rounded bg-slate-800 px-1.5 py-0.5 text-xs font-semibold text-white" data-test="ranked-winner">✓ {{ $t('results.ranked.winner') }}</span>
              <span v-else-if="r.eliminated === row.id" class="ml-1 text-xs text-slate-500" data-test="ranked-eliminated">{{ $t('results.ranked.eliminated') }}</span>
            </td>
            <td class="rounded bg-slate-100 py-1 align-middle">
              <div
                class="rounded bg-sky-500 px-2 py-0.5 text-xs font-medium text-[#000] [-webkit-text-stroke:0.3px_currentColor] whitespace-nowrap"
                style="min-width: 2.5rem"
                :style="{ width: `${row.pct}%` }"
              >{{ label(row.pct, row.votes) }}</div>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="r.exhaustedPct > 0" class="m-0 mt-1 text-xs text-slate-500">
        {{ $t('results.ranked.exhausted', { pct: r.exhaustedPct }) }}<template v-if="r.exhausted != null"> ({{ r.exhausted }})</template>
      </p>
    </div>

    <details class="mt-2 text-xs text-slate-600">
      <summary class="cursor-pointer select-none">{{ $t('results.ranked.howTitle') }}</summary>
      <p class="m-0 mt-1">{{ $t('results.ranked.howText') }}</p>
    </details>
    <PublicCommentsList :comments="allComments" />
  </section>
</template>
