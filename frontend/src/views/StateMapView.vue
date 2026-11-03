<script setup lang="ts">
// A state's district map (/maps/WA, decided 2026-11-03). The shapes come from
// public/maps/XX.json (scripts/maps/build-state-map.py, Census boundaries);
// the numbers from /api/polls/maps/XX/results, which follows the results pages'
// privacy. Left: a radio, a color and a party per row (Dem, Rep, Ind, Other,
// top 2); below: a district and a ballot measure. Top 2 shows the two leading
// parties (each party's candidates added up) as "X% / Y%" in their colors;
// a party shows its share; a measure shows Yes / No among each district
// ballot's voters (the leading answer bold).
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import axios from 'axios'
import { topTwoParties, type Party } from '@/stateMaps'

type Mode = Party | 'TOP2'
/** A color key: a party's, or TEXT (the top 2 row: the "/" and a measure's numbers). */
type ColorKey = Party | 'TEXT'
interface MapCandidate { name: string; affiliation: string; party: Party; pct: number | null; votes: number | null }
interface MapDistrict { number: number; electionId: number; officeName: string | null; shown: boolean; candidates: MapCandidate[]; measure: { yesPct: number; noPct: number } | null }
interface StateMapResults { state: string; stateName: string; live: boolean; measures: { id: number; title: string }[]; districts: MapDistrict[] }
interface Shape { path: string; label: [number, number]; box: [number, number, number, number]; small: boolean }
interface MapShapes { width: number; height: number; outline: string; borders: string; districts: Record<string, Shape> }

const { t } = useI18n()
const route = useRoute()
const code = computed(() => String(route.params.state).toUpperCase())

const shapes = ref<MapShapes | null>(null)
const data = ref<StateMapResults | null>(null)
const error = ref<string | null>(null)
const noMap = ref(false)

const mode = ref<Mode>('TOP2')
const district = ref<number | 'ALL'>('ALL')
const measureId = ref<number | null>(null)

// Each viewer's colors, remembered in this browser (top to bottom: Dem, Rep, Ind, top 2 / others).
const KEY = 'stateMap.colors.v2'   // v2 (2026-11-03): Other turned orange; TEXT is the white
const DEFAULTS = { DEM: '#32cd32', REP: '#ffff00', IND: '#ff00ff', OTHER: '#ff8c00', TEXT: '#ffffff' }
const colors = reactive<Record<ColorKey, string>>({ ...DEFAULTS })
try { Object.assign(colors, JSON.parse(localStorage.getItem(KEY) ?? '{}')) } catch { /* defaults */ }
watch(colors, c => { try { localStorage.setItem(KEY, JSON.stringify(c)) } catch { /* not remembered */ } })

const rows: { mode: Mode; party: ColorKey; label: string }[] = [
  { mode: 'DEM', party: 'DEM', label: 'stateMap.dem' },
  { mode: 'REP', party: 'REP', label: 'stateMap.rep' },
  { mode: 'IND', party: 'IND', label: 'stateMap.ind' },
  { mode: 'OTHER', party: 'OTHER', label: 'stateMap.other' },
  { mode: 'TOP2', party: 'TEXT', label: 'stateMap.top2' },
]

async function loadResults() {
  try {
    data.value = (await axios.get<StateMapResults>(`/api/polls/maps/${code.value}/results`,
      { params: measureId.value ? { measureId: measureId.value } : {} })).data
    error.value = null
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('stateMap.loadFailed')
  }
}
onMounted(async () => {
  try {
    const r = await fetch(`/maps/${code.value}.json`)
    if (!r.ok) throw new Error()
    shapes.value = await r.json()
  } catch {
    noMap.value = true
    return
  }
  await loadResults()
})
watch(measureId, loadResults)

const byNumber = computed(() => new Map((data.value?.districts ?? []).map(d => [d.number, d])))
const selected = computed(() => (district.value === 'ALL' ? null : byNumber.value.get(district.value) ?? null))

// Zoom to the chosen district; labels keep their size on screen.
const view = computed(() => {
  const s = shapes.value
  if (!s) return { box: '0 0 1000 650', scale: 1 }
  const sh = district.value === 'ALL' ? null : s.districts[String(district.value)]
  // A margin so the outline isn't cut off at the edges.
  const m = s.width * 0.03
  if (!sh) return { box: `${-m} ${-m} ${s.width + 2 * m} ${s.height + 2 * m}`, scale: 1 }
  const [x, y, w, h] = sh.box
  const pad = Math.max(w, h) * 0.15
  const vw = Math.max(w + 2 * pad, (h + 2 * pad) * (s.width / s.height))
  const vh = vw * (s.height / s.width)
  return { box: `${x + w / 2 - vw / 2} ${y + h / 2 - vh / 2} ${vw} ${vh}`, scale: vw / s.width }
})

/** The label's parts: each a piece of text, its color, and bold or not. */
function parts(d: MapDistrict | undefined): { text: string; color: string; bold?: boolean }[] {
  const dash = [{ text: '—', color: '#94a3b8' }]
  if (!d) return dash
  if (measureId.value) {
    if (!d.measure) return dash
    const yesLeads = d.measure.yesPct >= d.measure.noPct
    return [
      { text: `${d.measure.yesPct}%`, color: colors.TEXT, bold: yesLeads },
      { text: ' / ', color: colors.TEXT },
      { text: `${d.measure.noPct}%`, color: colors.TEXT, bold: !yesLeads },
    ]
  }
  if (!d.shown) return dash
  if (mode.value === 'TOP2') {
    // The two leading parties: each party's candidates added up (two Democrats count as one Dem share).
    const [a, b] = topTwoParties(d.candidates)
    if (!a) return dash
    return [
      { text: `${a[1]}%`, color: colors[a[0]], bold: true },
      ...(b ? [{ text: ' / ', color: colors.TEXT }, { text: `${b[1]}%`, color: colors[b[0]] }] : []),
    ]
  }
  const mine = d.candidates.filter(c => c.party === mode.value)
  if (!mine.length) return dash
  return [{ text: `${mine.reduce((s, c) => s + (c.pct ?? 0), 0)}%`, color: colors[mode.value as Party], bold: true }]
}

// Labels grow on narrow screens (a phone shows the map ~350px wide); tap a district to zoom.
const svgEl = ref<SVGSVGElement | null>(null)
const shownWidth = ref(1000)
let resize: ResizeObserver | null = null
watch(svgEl, el => {
  resize?.disconnect()
  if (!el || typeof ResizeObserver === 'undefined') return
  resize = new ResizeObserver(([e]) => { shownWidth.value = e.contentRect.width || 1000 })
  resize.observe(el)
})
onUnmounted(() => resize?.disconnect())
const textScale = computed(() => view.value.scale * Math.min(2.2, Math.max(1, 800 / shownWidth.value)))

function pick(n: number) { district.value = district.value === n ? 'ALL' : n }
const districtNumbers = computed(() => Object.keys(shapes.value?.districts ?? {}).map(Number).sort((a, b) => a - b))
</script>

<template>
  <div class="mx-auto max-w-6xl px-4 py-6">
    <h1 class="mb-1 text-2xl font-semibold text-slate-800">{{ $t('stateMap.title', { state: data?.stateName ?? code }) }}</h1>
    <p class="m-0 mb-4 text-sm text-slate-600">{{ $t('stateMap.disclaimer') }}</p>

    <p v-if="noMap" class="text-slate-600" data-test="no-map">{{ $t('stateMap.noMap') }}</p>
    <p v-if="error" class="text-sm text-red-700" role="alert">{{ error }}</p>

    <template v-if="shapes && !noMap">
      <div class="flex flex-col gap-4 md:flex-row md:items-start">
        <!-- The checkboxes, colors and parties columns -->
        <fieldset
          class="grid w-fit shrink-0 grid-cols-[auto_auto_auto] items-center gap-x-3 gap-y-2 rounded-md border border-slate-200 p-3"
          :class="measureId ? 'opacity-50' : ''"
          :disabled="!!measureId"
          data-test="party-controls"
        >
          <legend class="sr-only">{{ $t('stateMap.show') }}</legend>
          <template v-for="r in rows" :key="r.mode">
            <input :id="`map-mode-${r.mode}`" v-model="mode" type="radio" name="map-mode" :value="r.mode" :data-test="`mode-${r.mode}`" />
            <input
              v-model="colors[r.party]"
              type="color"
              :aria-label="$t('stateMap.colorFor', { party: $t(r.label) })"
              class="h-7 w-10 cursor-pointer rounded border border-slate-300 bg-transparent p-0"
            />
            <label :for="`map-mode-${r.mode}`" class="cursor-pointer text-sm text-slate-800">{{ $t(r.label) }}</label>
          </template>
        </fieldset>

        <!-- The map -->
        <div class="min-w-0 flex-1">
          <svg
            ref="svgEl"
            :viewBox="view.box"
            class="block w-full rounded-md"
            style="background: #0b2a5b; aspect-ratio: auto"
            role="img"
            :aria-label="$t('stateMap.title', { state: data?.stateName ?? code })"
            data-test="state-map"
          >
            <path
              v-for="n in districtNumbers"
              :key="`d${n}`"
              :d="shapes.districts[n].path"
              :fill="district === n ? 'rgba(255,255,255,0.10)' : 'transparent'"
              class="cursor-pointer"
              @click="pick(n)"
            />
            <path :d="shapes.borders" fill="none" stroke="#fff" stroke-opacity="0.7" :stroke-width="1.2 * view.scale" stroke-linejoin="round" pointer-events="none" />
            <path :d="shapes.outline" fill="none" stroke="#fff" :stroke-width="2.2 * view.scale" stroke-linejoin="round" pointer-events="none" />
            <g
              v-for="n in districtNumbers"
              :key="`l${n}`"
              :opacity="district === 'ALL' || district === n ? 1 : 0.35"
              class="cursor-pointer"
              :data-test="`district-label-${n}`"
              @click="pick(n)"
            >
              <text
                :x="shapes.districts[n].label[0]"
                :y="shapes.districts[n].label[1] - (shapes.districts[n].small && district === 'ALL' ? 7 : 11) * textScale"
                text-anchor="middle"
                fill="#fff"
                font-weight="700"
                :font-size="(shapes.districts[n].small && district === 'ALL' ? 11 : 16) * textScale"
              >{{ n }}</text>
              <text
                :x="shapes.districts[n].label[0]"
                :y="shapes.districts[n].label[1] + (shapes.districts[n].small && district === 'ALL' ? 6 : 9) * textScale"
                text-anchor="middle"
                :font-size="(shapes.districts[n].small && district === 'ALL' ? 10 : 15) * textScale"
                data-test="district-value"
              ><tspan v-for="(p, i) in parts(byNumber.get(n))" :key="i" :fill="p.color" :font-weight="p.bold ? 700 : 400">{{ p.text }}</tspan></text>
            </g>
          </svg>
          <p class="m-0 mt-1 text-xs text-slate-500">
            {{ measureId ? $t('stateMap.measureNote') : $t('stateMap.partyNote') }}
            {{ $t('stateMap.hiddenNote') }}
            <template v-if="data?.live"> {{ $t('stateMap.liveNote') }}</template>
          </p>
        </div>
      </div>

      <!-- The horizontal row: district, then ballot measure -->
      <div class="mt-4 flex flex-wrap items-end gap-4">
        <label class="flex flex-col text-sm text-slate-700">
          {{ $t('stateMap.district') }}
          <select v-model="district" class="mt-1 rounded border border-slate-300 p-1.5 text-sm" data-test="district-select">
            <option value="ALL">{{ $t('stateMap.all') }}</option>
            <option v-for="n in districtNumbers" :key="n" :value="n">{{ $t('stateMap.districtN', { n }) }}</option>
          </select>
        </label>
        <label class="flex min-w-0 max-w-full flex-col text-sm text-slate-700">
          {{ $t('stateMap.measure') }}
          <select v-model="measureId" class="mt-1 max-w-full rounded border border-slate-300 p-1.5 text-sm" data-test="measure-select">
            <option :value="null">{{ $t('stateMap.none') }}</option>
            <option v-for="m in data?.measures ?? []" :key="m.id" :value="m.id">{{ m.title }}</option>
          </select>
        </label>
      </div>

      <!-- The chosen district in detail -->
      <section v-if="selected" class="mt-4 rounded-md border border-slate-200 p-4" data-test="district-table">
        <h2 class="mb-1 text-base font-semibold text-slate-800">
          {{ $t('stateMap.districtN', { n: selected.number }) }}<template v-if="selected.officeName">: {{ selected.officeName }}</template>
        </h2>
        <table class="w-full border-collapse text-sm">
          <tbody>
            <tr v-for="c in selected.candidates" :key="c.name" class="border-b border-slate-100 last:border-b-0">
              <td class="py-1 pr-2" style="width: 1.25rem"><span class="inline-block h-3 w-3 rounded-full" :style="{ background: colors[c.party] }" /></td>
              <td class="py-1 pr-3 text-slate-800">{{ c.name }} <span class="text-slate-500">{{ c.affiliation }}</span></td>
              <td class="py-1 text-right font-semibold text-slate-800">{{ c.pct == null ? '—' : `${c.pct}%` }}<template v-if="c.votes != null"> ({{ c.votes }})</template></td>
            </tr>
          </tbody>
        </table>
        <p v-if="measureId" class="m-0 mt-2 text-sm text-slate-700">
          {{ data?.measures.find(m => m.id === measureId)?.title }}:
          <strong v-if="selected.measure">{{ $t('stateMap.yes') }} {{ selected.measure.yesPct }}% / {{ $t('stateMap.no') }} {{ selected.measure.noPct }}%</strong>
          <span v-else>—</span>
        </p>
        <router-link :to="`/polls/election/${selected.electionId}/results`" class="mt-2 inline-block text-sm underline">{{ $t('stateMap.fullResults') }}</router-link>
      </section>
    </template>
  </div>
</template>
