<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import axios from 'axios'

// Creator reports, phase 2: one poll's report for its creator. Totals only;
// the backend withholds small groups (PollReportService) and the results
// endpoints apply the same privacy threshold to the inside/outside split.
const route = useRoute()
const { t } = useI18n()
const type = computed(() => String(route.params.type)) // questionnaire | election | ballot-measure
const id = computed(() => Number(route.params.id))

interface TimelineBucket { from: string; to: string; count: number; runningTotal: number }
interface ReachRow { label: string; count: number | null }
interface PollReport {
  respondents: number
  inArea: number | null
  threshold: number
  timeline: TimelineBucket[] | null
  reach: { level: 'STATE' | 'COUNTY' | 'ZIP'; rows: ReachRow[]; outside: number | null; covered: number; total: number } | null
  engagement: { changedAnswers: number; commented: number } | null
}
interface Results {
  title: string
  totalRespondents: number
  suppressed: boolean
  suppressionMessage: string | null
  perQuestion?: { questionId: number; text: string; totalResponses: number; byAnswer: Record<string, number> }[]
  perCandidate?: { candidateId: number; name: string; affiliation: string; officeName: string; yes: number; no: number; total: number }[]
  yes?: number
  no?: number
}

const report = ref<PollReport | null>(null)
const all = ref<Results | null>(null)
const inside = ref<Results | null>(null)
const outside = ref<Results | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

const RESULTS_PATH: Record<string, string> = {
  questionnaire: 'questionnaires', election: 'elections', 'ballot-measure': 'ballot-measures',
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const base = `/api/polls/${RESULTS_PATH[type.value]}/${id.value}/results`
    const [r, a, i, o] = await Promise.all([
      axios.get<PollReport>(`/api/creator/polls/${type.value}/${id.value}/report`),
      axios.get<Results>(base),
      axios.get<Results>(base, { params: { outsidePurview: 'false' } }),
      axios.get<Results>(base, { params: { withinPurview: 'false' } }),
    ])
    report.value = r.data
    all.value = a.data
    inside.value = i.data
    outside.value = o.data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('creator.report.loadFailed')
  } finally {
    loading.value = false
  }
}
onMounted(load)

const pct = (n: number, of: number) => (of > 0 ? Math.round((n / of) * 100) : 0)
const yesOf = (byAnswer: Record<string, number>) =>
  Object.entries(byAnswer).reduce((s, [k, v]) => (k.toLowerCase() === 'yes' ? s + v : s), 0)

// ---------- participation over time ----------
function period(b: TimelineBucket): string {
  const f = new Date(b.from + 'T00:00:00Z').toLocaleDateString(undefined, { timeZone: 'UTC' })
  const to = new Date(b.to + 'T00:00:00Z').toLocaleDateString(undefined, { timeZone: 'UTC' })
  return b.from === b.to ? f : `${f} – ${to}`
}
const maxBucket = computed(() => Math.max(1, ...(report.value?.timeline ?? []).map(b => b.count)))

// ---------- highlights ----------
interface Highlight { label: string; detail: string }
const highlights = computed<Highlight[]>(() => {
  const r = all.value
  if (!r || r.suppressed || r.totalRespondents === 0) return []
  if (r.perCandidate) {
    const offices = new Map<string, NonNullable<Results['perCandidate']>>()
    for (const c of r.perCandidate) offices.set(c.officeName, [...(offices.get(c.officeName) ?? []), c])
    return [...offices.entries()].map(([office, cs]) => {
      const sorted = [...cs].sort((a, b) => b.yes - a.yes)
      const [lead, next] = sorted
      const detail = next
        ? t('creator.report.leads', { name: lead.name, votes: lead.yes, margin: lead.yes - next.yes })
        : t('creator.report.onlyCandidate', { name: lead.name, votes: lead.yes })
      return { label: office, detail }
    })
  }
  if (r.perQuestion) {
    // Most agreed (furthest from a 50/50 split) first, most divided last.
    return [...r.perQuestion]
      .filter(q => q.totalResponses > 0)
      .map(q => ({ q, share: pct(yesOf(q.byAnswer), q.totalResponses) }))
      .sort((a, b) => Math.abs(b.share - 50) - Math.abs(a.share - 50))
      .map(({ q, share }) => ({ label: q.text, detail: t('creator.report.yesShare', { pct: share }) }))
  }
  const yes = r.yes ?? 0, no = r.no ?? 0
  return [{ label: t('creator.report.measure'), detail: t('creator.report.measureMargin', {
    yes: pct(yes, yes + no), no: pct(no, yes + no), margin: Math.abs(yes - no),
    side: yes >= no ? t('common.yes') : t('common.no'),
  }) }]
})

// ---------- inside vs outside ----------
interface SplitRow { label: string; inside: string; outside: string }
function cell(r: Results | null, pick: (r: Results) => string): string {
  if (!r) return '—'
  if (r.suppressed) return t('creator.report.withheld')
  if (r.totalRespondents === 0) return t('creator.report.noneYet')
  return pick(r)
}
const split = computed<SplitRow[]>(() => {
  const a = all.value
  if (!a) return []
  if (a.perQuestion) {
    return a.perQuestion.map(q => {
      const yesPct = (r: Results) => {
        const x = r.perQuestion?.find(p => p.questionId === q.questionId)
        return x && x.totalResponses ? `${pct(yesOf(x.byAnswer), x.totalResponses)}% ${t('common.yes')}` : '—'
      }
      return { label: q.text, inside: cell(inside.value, yesPct), outside: cell(outside.value, yesPct) }
    })
  }
  if (a.perCandidate) {
    return a.perCandidate.map(c => {
      const votes = (r: Results) => {
        const x = r.perCandidate?.find(p => p.candidateId === c.candidateId)
        return x ? `${x.yes} (${pct(x.yes, x.total)}%)` : '—'
      }
      return { label: `${c.name} · ${c.officeName}`, inside: cell(inside.value, votes), outside: cell(outside.value, votes) }
    })
  }
  const share = (side: 'yes' | 'no') => (r: Results) => `${pct(r[side] ?? 0, (r.yes ?? 0) + (r.no ?? 0))}%`
  return [
    { label: t('common.yes'), inside: cell(inside.value, share('yes')), outside: cell(outside.value, share('yes')) },
    { label: t('common.no'), inside: cell(inside.value, share('no')), outside: cell(outside.value, share('no')) },
  ]
})

const reachLevel = computed(() => {
  switch (report.value?.reach?.level) {
    case 'STATE': return t('creator.report.levelStates')
    case 'COUNTY': return t('creator.report.levelCounties')
    default: return t('creator.report.levelZips')
  }
})
</script>

<template>
  <div class="mx-auto max-w-4xl py-8">
    <router-link to="/creator/dashboard" class="text-sm text-slate-600 underline hover:text-slate-900">
      ← {{ $t('creator.report.back') }}
    </router-link>
    <h1 class="mb-1 mt-2 text-2xl font-semibold text-slate-800">
      {{ all ? $t('creator.report.headingFor', { title: all.title }) : $t('creator.report.heading') }}
    </h1>
    <p class="mb-6 text-sm text-slate-600">{{ $t('creator.report.intro', { k: report?.threshold ?? 10 }) }}</p>

    <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-else-if="loading" class="text-sm text-slate-600">{{ $t('common.loading') }}</p>

    <template v-else-if="report">
      <!-- At a glance -->
      <section class="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4" data-test="summary">
        <div class="rounded border border-slate-200 p-3">
          <p class="m-0 text-2xl font-semibold text-slate-800">{{ report.respondents }}</p>
          <p class="m-0 text-xs text-slate-500">{{ $t('creator.report.respondents') }}</p>
        </div>
        <div class="rounded border border-slate-200 p-3">
          <p class="m-0 text-2xl font-semibold text-slate-800">
            {{ report.inArea === null ? '—' : `${pct(report.inArea, report.respondents)}%` }}
          </p>
          <p class="m-0 text-xs text-slate-500">{{ $t('creator.report.inArea') }}</p>
        </div>
        <div class="rounded border border-slate-200 p-3">
          <p class="m-0 text-2xl font-semibold text-slate-800">{{ report.engagement?.changedAnswers ?? '—' }}</p>
          <p class="m-0 text-xs text-slate-500">{{ $t('creator.report.changed') }}</p>
        </div>
        <div class="rounded border border-slate-200 p-3">
          <p class="m-0 text-2xl font-semibold text-slate-800">{{ report.engagement?.commented ?? '—' }}</p>
          <p class="m-0 text-xs text-slate-500">{{ $t('creator.report.commented') }}</p>
        </div>
      </section>

      <p v-if="report.respondents < report.threshold" class="mb-6 rounded bg-slate-50 p-3 text-sm text-slate-600" data-test="too-few">
        {{ $t('creator.report.tooFew', { k: report.threshold }) }}
      </p>

      <!-- Participation over time -->
      <section v-if="report.timeline" class="mb-6" data-test="timeline">
        <h2 class="mb-1 text-lg font-semibold text-slate-700">{{ $t('creator.report.timelineHeading') }}</h2>
        <p class="mb-2 text-xs text-slate-500">{{ $t('creator.report.timelineNote', { k: report.threshold }) }}</p>
        <table class="w-full border-collapse text-sm">
          <thead>
            <tr class="text-left text-slate-600">
              <th class="border-b border-slate-200 p-2 font-semibold">{{ $t('creator.report.period') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold">{{ $t('creator.report.newRespondents') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold">{{ $t('creator.report.runningTotal') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in report.timeline" :key="b.from">
              <td class="border-b border-slate-100 p-2">{{ period(b) }}</td>
              <td class="border-b border-slate-100 p-2">
                <span class="inline-block h-2 rounded bg-slate-700 align-middle" :style="{ width: `${(b.count / maxBucket) * 8}rem` }" />
                <span class="ml-2">{{ b.count }}</span>
              </td>
              <td class="border-b border-slate-100 p-2">{{ b.runningTotal }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Reach -->
      <section v-if="report.reach" class="mb-6" data-test="reach">
        <h2 class="mb-1 text-lg font-semibold text-slate-700">{{ $t('creator.report.reachHeading') }}</h2>
        <p class="mb-2 text-sm text-slate-600">
          {{ $t('creator.report.coverage', { covered: report.reach.covered, total: report.reach.total, level: reachLevel }) }}
        </p>
        <table class="w-full border-collapse text-sm">
          <tbody>
            <tr v-for="r in report.reach.rows" :key="r.label">
              <td class="border-b border-slate-100 p-2">{{ r.label }}</td>
              <td class="border-b border-slate-100 p-2 text-right">{{ r.count ?? $t('creator.report.fewerThan', { k: report.threshold }) }}</td>
            </tr>
            <tr>
              <td class="border-b border-slate-100 p-2 italic text-slate-600">{{ $t('creator.report.outsideArea') }}</td>
              <td class="border-b border-slate-100 p-2 text-right">{{ report.reach.outside ?? $t('creator.report.fewerThan', { k: report.threshold }) }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Highlights -->
      <section v-if="highlights.length" class="mb-6" data-test="highlights">
        <h2 class="mb-2 text-lg font-semibold text-slate-700">{{ $t('creator.report.highlightsHeading') }}</h2>
        <ul class="m-0 list-none p-0">
          <li v-for="h in highlights" :key="h.label" class="border-b border-slate-100 py-2 text-sm last:border-b-0">
            <strong class="font-semibold text-slate-800">{{ h.label }}</strong>
            <span class="text-slate-600"> — {{ h.detail }}</span>
          </li>
        </ul>
      </section>

      <!-- Inside vs outside -->
      <section v-if="split.length" class="mb-6" data-test="split">
        <h2 class="mb-1 text-lg font-semibold text-slate-700">{{ $t('creator.report.splitHeading') }}</h2>
        <p class="mb-2 text-xs text-slate-500">{{ $t('creator.report.splitNote', { k: report.threshold }) }}</p>
        <table class="w-full border-collapse text-sm">
          <thead>
            <tr class="text-left text-slate-600">
              <th class="border-b border-slate-200 p-2 font-semibold"></th>
              <th class="border-b border-slate-200 p-2 font-semibold">{{ $t('creator.report.insideCol') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold">{{ $t('creator.report.outsideCol') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in split" :key="row.label">
              <td class="border-b border-slate-100 p-2">{{ row.label }}</td>
              <td class="border-b border-slate-100 p-2">{{ row.inside }}</td>
              <td class="border-b border-slate-100 p-2">{{ row.outside }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>
