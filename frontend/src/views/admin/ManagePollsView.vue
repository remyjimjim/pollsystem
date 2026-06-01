<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

type Kind = 'ELECTION' | 'QUESTIONNAIRE' | 'BALLOT_MEASURE'
type Scope = 'ZIPCODE' | 'COUNTY' | 'STATE'
type SortKey = 'title' | 'type' | 'creatorEmail' | 'stateInitial' | 'countyName' | 'zipcode' | 'closeDate' | 'blocked' | 'note'

interface NoteDto { id: number; body: string; emailed: boolean; createdAt: string; updatedAt: string }
interface PollRow {
  id: number
  type: Kind
  title: string
  status: string
  creatorEmail: string
  closeDate: string | null
  zipcodes: string[]
  stateInitial: string | null
  countyName: string | null
  blocked: boolean
  latestNote: NoteDto | null
}
interface StateOpt { id: number; name: string; initial: string }
interface CountyOpt { id: number; stateId: number; name: string }
interface Purview { states: StateOpt[]; counties: CountyOpt[]; zipcodes: string[]; unrestricted: boolean }
interface BlockDto {
  id: number
  scope: Scope
  zipcode: string | null
  countyId: number | null
  countyName: string | null
  stateId: number | null
  stateInitial: string | null
  createdAt: string
  createdBy: number
}

// ---------- filters ----------
const kindFilter = ref<Record<Kind, boolean>>({ ELECTION: true, QUESTIONNAIRE: true, BALLOT_MEASURE: true })
const showDisabled = ref(false)
const titleFilter = ref('')
const titleSuggestions = ref<string[]>([])
const notesFilter = ref('')

const purview = ref<Purview>({ states: [], counties: [], zipcodes: [], unrestricted: false })
const selectedStateIds = ref<number[]>([])
const lastClickedStateIndex = ref<number | null>(null)
const selectedCountyIds = ref<number[]>([])
const lastClickedCountyIndex = ref<number | null>(null)
const selectedZipcodes = ref<string[]>([])
const lastClickedZipIndex = ref<number | null>(null)
const statePickerOpen = ref(false)
const countyPickerOpen = ref(false)
const zipPickerOpen = ref(false)

const results = ref<PollRow[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)

const displayedCounties = computed<CountyOpt[]>(() => {
  if (selectedStateIds.value.length === 0) return purview.value.counties
  const set = new Set(selectedStateIds.value)
  return purview.value.counties.filter(c => set.has(c.stateId))
})
const displayedZipcodes = computed<string[]>(() => purview.value.zipcodes)

async function loadPurview() {
  try {
    const res = await axios.get<Purview>('/api/admin/polls/purview')
    purview.value = res.data
    if (res.data.unrestricted) {
      // SUPER: load the full state list. Counties and zipcodes load on
      // demand when the admin narrows the geography (otherwise we'd ship
      // 3.3k counties and 30k+ zipcodes that they don't need yet).
      const s = await axios.get<StateOpt[]>('/api/states')
      purview.value = { ...res.data, states: s.data }
    }
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('admin.managePolls.errorPurview')
  }
}

/**
 * For SUPER (unrestricted) admins, populate the county picker from
 * `/api/counties` matching the picked states. For purview-restricted
 * admins, the county list was already loaded by `/api/admin/polls/purview`
 * and is filtered locally by `displayedCounties` — leave it alone.
 */
async function refreshCountiesForState() {
  if (!purview.value.unrestricted) return
  if (selectedStateIds.value.length === 0) {
    purview.value = { ...purview.value, counties: [] }
    return
  }
  try {
    const res = await axios.get<CountyOpt[]>('/api/counties', {
      params: { state_id: selectedStateIds.value.join(',') }
    })
    purview.value = { ...purview.value, counties: res.data }
  } catch { /* keep prior list */ }
}

/** Same pattern as `refreshCountiesForState` but for the zipcode picker. */
async function refreshZipcodesForGeo() {
  if (!purview.value.unrestricted) return
  const params: Record<string, string> = {}
  if (selectedCountyIds.value.length > 0) {
    params.county_ids = selectedCountyIds.value.join(',')
  } else if (selectedStateIds.value.length > 0) {
    params.state_id = selectedStateIds.value.join(',')
  } else {
    purview.value = { ...purview.value, zipcodes: [] }
    return
  }
  try {
    const res = await axios.get<{ zipcode: string }[]>('/api/zipcodes', { params })
    purview.value = { ...purview.value, zipcodes: res.data.map(z => z.zipcode) }
  } catch { /* keep prior list */ }
}

function afterStateChange() {
  selectedCountyIds.value = []
  selectedZipcodes.value = []
  refreshCountiesForState()
  refreshZipcodesForGeo()
  scheduleFetch()
}
function afterCountyChange() {
  selectedZipcodes.value = []
  refreshZipcodesForGeo()
  scheduleFetch()
}

async function loadTitleSuggestions() {
  try {
    const res = await axios.get<{ titles: string[]; candidates: string[] }>('/api/polls/search/suggestions')
    titleSuggestions.value = res.data.titles
  } catch { /* non-fatal */ }
}

// ---------- main fetch (debounced) ----------
let fetchTimer: ReturnType<typeof setTimeout> | null = null
function scheduleFetch() {
  if (fetchTimer) clearTimeout(fetchTimer)
  fetchTimer = setTimeout(fetchPolls, 150)
}
/** Run the search now — used by the Search button and Enter-in-form. */
function searchNow() {
  if (fetchTimer) { clearTimeout(fetchTimer); fetchTimer = null }
  if (titleTimer) { clearTimeout(titleTimer); titleTimer = null }
  if (notesTimer) { clearTimeout(notesTimer); notesTimer = null }
  fetchPolls()
}
async function fetchPolls() {
  loading.value = true
  error.value = null
  try {
    const kinds = (Object.keys(kindFilter.value) as Kind[]).filter(k => kindFilter.value[k])
    const params: Record<string, string> = {}
    if (kinds.length > 0) params.pollType = kinds.join(',')
    if (titleFilter.value.trim()) params.title = titleFilter.value.trim()
    if (notesFilter.value.trim()) params.notesContain = notesFilter.value.trim()
    if (showDisabled.value) params.includeDisabled = 'true'
    if (selectedZipcodes.value.length > 0) params.zipcode = selectedZipcodes.value.join(',')
    else if (selectedCountyIds.value.length > 0) params.countyId = selectedCountyIds.value.join(',')
    else if (selectedStateIds.value.length > 0) params.stateId = selectedStateIds.value.join(',')
    results.value = (await axios.get<PollRow[]>('/api/admin/polls', { params })).data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('admin.managePolls.errorLoad')
  } finally {
    loading.value = false
  }
}

watch(kindFilter, scheduleFetch, { deep: true })
watch(showDisabled, scheduleFetch)
let titleTimer: ReturnType<typeof setTimeout> | null = null
watch(titleFilter, () => {
  if (titleTimer) clearTimeout(titleTimer)
  titleTimer = setTimeout(scheduleFetch, 200)
})
let notesTimer: ReturnType<typeof setTimeout> | null = null
watch(notesFilter, () => {
  if (notesTimer) clearTimeout(notesTimer)
  notesTimer = setTimeout(scheduleFetch, 200)
})

// ---------- picker click handlers (shift-range + Shift-* / Shift-0) ----------
function onStateClick(e: MouseEvent, idx: number, id: number) {
  const target = e.target as HTMLInputElement
  const willCheck = target.checked
  if (e.shiftKey && lastClickedStateIndex.value !== null) {
    const a = Math.min(lastClickedStateIndex.value, idx)
    const b = Math.max(lastClickedStateIndex.value, idx)
    const ids = purview.value.states.slice(a, b + 1).map(s => s.id)
    selectedStateIds.value = willCheck
      ? Array.from(new Set([...selectedStateIds.value, ...ids]))
      : selectedStateIds.value.filter(x => !ids.includes(x))
  } else {
    selectedStateIds.value = willCheck
      ? Array.from(new Set([...selectedStateIds.value, id]))
      : selectedStateIds.value.filter(x => x !== id)
  }
  lastClickedStateIndex.value = idx
  afterStateChange()
}
function onStateKeydown(e: KeyboardEvent) { handleShortcuts(e, 'state') }
function onCountyClick(e: MouseEvent, idx: number, id: number) {
  const target = e.target as HTMLInputElement
  const willCheck = target.checked
  if (e.shiftKey && lastClickedCountyIndex.value !== null) {
    const a = Math.min(lastClickedCountyIndex.value, idx)
    const b = Math.max(lastClickedCountyIndex.value, idx)
    const ids = displayedCounties.value.slice(a, b + 1).map(c => c.id)
    selectedCountyIds.value = willCheck
      ? Array.from(new Set([...selectedCountyIds.value, ...ids]))
      : selectedCountyIds.value.filter(x => !ids.includes(x))
  } else {
    selectedCountyIds.value = willCheck
      ? Array.from(new Set([...selectedCountyIds.value, id]))
      : selectedCountyIds.value.filter(x => x !== id)
  }
  lastClickedCountyIndex.value = idx
  afterCountyChange()
}
function onCountyKeydown(e: KeyboardEvent) { handleShortcuts(e, 'county') }
function onZipClick(e: MouseEvent, idx: number, code: string) {
  const target = e.target as HTMLInputElement
  const willCheck = target.checked
  if (e.shiftKey && lastClickedZipIndex.value !== null) {
    const a = Math.min(lastClickedZipIndex.value, idx)
    const b = Math.max(lastClickedZipIndex.value, idx)
    const zips = displayedZipcodes.value.slice(a, b + 1)
    selectedZipcodes.value = willCheck
      ? Array.from(new Set([...selectedZipcodes.value, ...zips]))
      : selectedZipcodes.value.filter(z => !zips.includes(z))
  } else {
    selectedZipcodes.value = willCheck
      ? Array.from(new Set([...selectedZipcodes.value, code]))
      : selectedZipcodes.value.filter(z => z !== code)
  }
  lastClickedZipIndex.value = idx
  scheduleFetch()
}
function onZipKeydown(e: KeyboardEvent) { handleShortcuts(e, 'zip') }
function handleShortcuts(e: KeyboardEvent, which: 'state' | 'county' | 'zip') {
  const isAll = e.key === '*' || (e.shiftKey && e.code === 'Digit8')
  const isNone = e.key === ')' || (e.shiftKey && e.code === 'Digit0')
  if (!isAll && !isNone) return
  e.preventDefault()
  if (which === 'state') {
    selectedStateIds.value = isAll ? purview.value.states.map(s => s.id) : []
    afterStateChange()
  } else if (which === 'county') {
    selectedCountyIds.value = isAll ? displayedCounties.value.map(c => c.id) : []
    afterCountyChange()
  } else {
    selectedZipcodes.value = isAll ? displayedZipcodes.value.slice() : []
    scheduleFetch()
  }
}

// ---------- picker summaries ----------
const statePickerSummary = computed<string>(() => {
  if (selectedStateIds.value.length === 0) return t('admin.managePolls.stateAny')
  if (selectedStateIds.value.length === 1) {
    return purview.value.states.find(s => s.id === selectedStateIds.value[0])?.name ?? t('admin.managePolls.stateAny')
  }
  return t('admin.managePolls.stateNSelected', { n: selectedStateIds.value.length })
})
const countyPickerSummary = computed<string>(() => {
  if (selectedCountyIds.value.length === 0) return t('admin.managePolls.countyAny')
  if (selectedCountyIds.value.length === 1) {
    return purview.value.counties.find(c => c.id === selectedCountyIds.value[0])?.name ?? t('admin.managePolls.countyAny')
  }
  return t('admin.managePolls.countyNSelected', { n: selectedCountyIds.value.length })
})
const zipPickerSummary = computed<string>(() => {
  if (selectedZipcodes.value.length === 1) return selectedZipcodes.value[0]
  if (selectedZipcodes.value.length > 1) return t('admin.managePolls.zipcodeNSelected', { n: selectedZipcodes.value.length })
  return displayedZipcodes.value[0] ?? ''
})

// ---------- sorting ----------
const sortKey = ref<SortKey>('title')
const sortDir = ref<'asc' | 'desc'>('asc')
function toggleSort(k: SortKey) {
  if (sortKey.value === k) sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  else { sortKey.value = k; sortDir.value = 'asc' }
}
function sortValue(r: PollRow, k: SortKey): string {
  switch (k) {
    case 'title': return r.title.toLowerCase()
    case 'type': return r.type
    case 'creatorEmail': return r.creatorEmail.toLowerCase()
    case 'stateInitial': return (r.stateInitial ?? '').toLowerCase()
    case 'countyName': return (r.countyName ?? '').toLowerCase()
    case 'zipcode': return r.zipcodes[0] ?? ''
    // ISO timestamps are lex-sortable. Null close-date = "never closes",
    // sorts after every real date in ascending order.
    case 'closeDate': return r.closeDate ?? '￿'
    case 'blocked': return r.blocked ? 'true' : 'false'
    case 'note': return (r.latestNote?.body ?? '').toLowerCase()
  }
}
const sortedResults = computed<PollRow[]>(() => {
  const dir = sortDir.value === 'asc' ? 1 : -1
  return results.value.slice().sort((a, b) => {
    const av = sortValue(a, sortKey.value), bv = sortValue(b, sortKey.value)
    if (av < bv) return -dir; if (av > bv) return dir; return 0
  })
})
function sortIndicator(k: SortKey): string {
  if (sortKey.value !== k) return ''
  return sortDir.value === 'asc' ? ' ▲' : ' ▼'
}

// ---------- block modal ----------
const blockModalOpen = ref(false)
const blockModalRow = ref<PollRow | null>(null)
const blockModalExisting = ref<BlockDto[]>([])
const blockModalScope = ref<Scope>('ZIPCODE')
const blockModalZip = ref<string>('')
const blockModalCountyId = ref<number | ''>('')
const blockModalStateId = ref<number | ''>('')
const blockModalError = ref<string | null>(null)
const blockModalBusy = ref(false)
const blockModalRowMeta = computed(() => {
  const r = blockModalRow.value
  if (!r) return { stateId: null as number | null, countyId: null as number | null }
  // Resolve the row's state/county ids from its zipcode against the purview.
  const cz = purview.value.counties.find(c => c.name === r.countyName)
  return { stateId: cz?.stateId ?? null, countyId: cz?.id ?? null }
})

async function openBlockModal(row: PollRow) {
  blockModalRow.value = row
  blockModalOpen.value = true
  blockModalError.value = null
  blockModalScope.value = 'ZIPCODE'
  blockModalZip.value = row.zipcodes[0] ?? ''
  blockModalCountyId.value = blockModalRowMeta.value.countyId ?? ''
  blockModalStateId.value = blockModalRowMeta.value.stateId ?? ''
  try {
    blockModalExisting.value = (await axios.get<BlockDto[]>(`/api/admin/polls/${row.type}/${row.id}/blocks`)).data
  } catch (e: any) {
    blockModalError.value = e?.response?.data?.message ?? t('admin.managePolls.errorBlocks')
  }
}

/**
 * Click handler for the Enable/Disable checkbox in the table.
 *
 * The checkbox reads as "is this poll enabled?" — checked = enabled,
 * unchecked = blocked. So the two transitions are:
 * - Checked → unchecked (enabled → disabled): open the modal so the
 *   admin picks the scope (zipcode / county / state). The actual block
 *   isn't created until Apply lands inside the modal; if the modal is
 *   dismissed the row stays checked after the next refetch.
 * - Unchecked → checked (disabled → enabled): silently remove every
 *   block currently affecting this poll that's within the admin's
 *   purview. Out-of-purview blocks (e.g. a state-wide block another
 *   admin set) stay and the row will refresh back to unchecked.
 *
 * `@click.prevent` stops the browser from flipping the checkbox before
 * the backend round-trip completes; the `:checked` bind reflects the
 * authoritative `!row.blocked`.
 */
async function onBlockCheckboxClick(row: PollRow) {
  if (!row.blocked) {
    // Was checked (enabled), user wants to disable → ask for scope via modal.
    await openBlockModal(row)
    return
  }
  // Was unchecked (disabled), user wants to enable → drop all blocks we can reach.
  try {
    const existing = (await axios.get<BlockDto[]>(`/api/admin/polls/${row.type}/${row.id}/blocks`)).data
    await Promise.allSettled(
      existing.map(b => axios.delete(`/api/admin/polls/blocks/${b.id}`))
    )
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('admin.managePolls.errorBlock')
  } finally {
    await fetchPolls()
  }
}
function closeBlockModal() {
  blockModalOpen.value = false
  blockModalRow.value = null
  blockModalExisting.value = []
  blockModalError.value = null
}
async function applyBlock() {
  if (!blockModalRow.value) return
  blockModalBusy.value = true
  blockModalError.value = null
  try {
    const body: Record<string, unknown> = { scope: blockModalScope.value }
    if (blockModalScope.value === 'ZIPCODE') body.zipcode = blockModalZip.value
    if (blockModalScope.value === 'COUNTY') body.countyId = blockModalCountyId.value
    if (blockModalScope.value === 'STATE') body.stateId = blockModalStateId.value
    await axios.post<BlockDto>(`/api/admin/polls/${blockModalRow.value.type}/${blockModalRow.value.id}/block`, body)
    await Promise.all([fetchPolls(), refetchBlocks()])
  } catch (e: any) {
    blockModalError.value = e?.response?.data?.message ?? t('admin.managePolls.errorBlock')
  } finally {
    blockModalBusy.value = false
  }
}
async function removeBlock(b: BlockDto) {
  if (!blockModalRow.value) return
  blockModalBusy.value = true
  try {
    await axios.delete(`/api/admin/polls/blocks/${b.id}`)
    await Promise.all([fetchPolls(), refetchBlocks()])
  } catch (e: any) {
    blockModalError.value = e?.response?.data?.message ?? t('admin.managePolls.errorBlock')
  } finally {
    blockModalBusy.value = false
  }
}
async function refetchBlocks() {
  if (!blockModalRow.value) return
  blockModalExisting.value = (await axios.get<BlockDto[]>(
    `/api/admin/polls/${blockModalRow.value.type}/${blockModalRow.value.id}/blocks`
  )).data
}

// ---------- note modal ----------
const noteModalOpen = ref(false)
const noteModalRow = ref<PollRow | null>(null)
const noteModalNotes = ref<NoteDto[]>([])
const noteModalIdx = ref(0)
const noteModalMode = ref<'edit' | 'new'>('edit')
const noteModalBody = ref('')
const noteModalSendEmail = ref(false)
const noteModalSaving = ref(false)
const noteModalError = ref<string | null>(null)
const noteTextarea = ref<HTMLTextAreaElement | null>(null)

async function openNoteModal(row: PollRow, startMode: 'edit' | 'new' = 'edit') {
  noteModalRow.value = row
  noteModalError.value = null
  noteModalNotes.value = []
  noteModalIdx.value = 0
  noteModalMode.value = startMode
  noteModalBody.value = ''
  noteModalSendEmail.value = false
  noteModalOpen.value = true
  try {
    noteModalNotes.value = (await axios.get<NoteDto[]>(`/api/admin/polls/${row.type}/${row.id}/notes`)).data
    if (noteModalNotes.value.length === 0) noteModalMode.value = 'new'
    else if (startMode === 'edit') noteModalBody.value = noteModalNotes.value[0].body
  } catch (e: any) {
    noteModalError.value = e?.response?.data?.message ?? t('admin.managePolls.errorNotes')
  }
  await nextTick()
  noteTextarea.value?.focus()
}
function closeNoteModal() {
  noteModalOpen.value = false
  noteModalRow.value = null
  noteModalNotes.value = []
  noteModalBody.value = ''
}
function notePrev() {
  if (noteModalMode.value !== 'edit') return
  if (noteModalIdx.value < noteModalNotes.value.length - 1) {
    noteModalIdx.value += 1
    noteModalBody.value = noteModalNotes.value[noteModalIdx.value].body
  }
}
function noteNext() {
  if (noteModalMode.value !== 'edit') return
  if (noteModalIdx.value > 0) {
    noteModalIdx.value -= 1
    noteModalBody.value = noteModalNotes.value[noteModalIdx.value].body
  }
}
function noteStartNew() {
  noteModalMode.value = 'new'
  noteModalBody.value = ''
  noteModalSendEmail.value = false
  nextTick(() => noteTextarea.value?.focus())
}
function noteBackToEdit() {
  if (noteModalNotes.value.length === 0) return
  noteModalMode.value = 'edit'
  noteModalIdx.value = 0
  noteModalBody.value = noteModalNotes.value[0].body
}
async function saveNote() {
  if (!noteModalRow.value) return
  const body = noteModalBody.value.trim()
  if (body.length === 0 || body.length > 2000) {
    noteModalError.value = t('admin.managePolls.errorBodyLength'); return
  }
  noteModalSaving.value = true
  noteModalError.value = null
  try {
    if (noteModalMode.value === 'new') {
      const res = await axios.post<NoteDto>(
        `/api/admin/polls/${noteModalRow.value.type}/${noteModalRow.value.id}/notes`,
        { body, sendEmail: noteModalSendEmail.value }
      )
      noteModalNotes.value = [res.data, ...noteModalNotes.value]
      noteModalIdx.value = 0
      noteModalMode.value = 'edit'
      noteModalSendEmail.value = false
      if (noteModalRow.value) {
        const updated = { ...noteModalRow.value, latestNote: res.data }
        results.value = results.value.map(r => (r.id === updated.id && r.type === updated.type ? updated : r))
        noteModalRow.value = updated
      }
    } else {
      const current = noteModalNotes.value[noteModalIdx.value]
      const res = await axios.put<NoteDto>(`/api/admin/polls/notes/${current.id}`, { body })
      noteModalNotes.value = noteModalNotes.value.map((n, i) => (i === noteModalIdx.value ? res.data : n))
      if (noteModalIdx.value === 0 && noteModalRow.value) {
        const updated = { ...noteModalRow.value, latestNote: res.data }
        results.value = results.value.map(r => (r.id === updated.id && r.type === updated.type ? updated : r))
        noteModalRow.value = updated
      }
    }
  } catch (e: any) {
    noteModalError.value = e?.response?.data?.message ?? t('admin.managePolls.errorSaveNote')
  } finally {
    noteModalSaving.value = false
  }
}

// ---------- global UI ----------
function onDocClick(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  if (!target?.closest('[data-state-picker]')) statePickerOpen.value = false
  if (!target?.closest('[data-county-picker]')) countyPickerOpen.value = false
  if (!target?.closest('[data-zip-picker]')) zipPickerOpen.value = false
}
function onEsc(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  statePickerOpen.value = false
  countyPickerOpen.value = false
  zipPickerOpen.value = false
  if (noteModalOpen.value) closeNoteModal()
  if (blockModalOpen.value) closeBlockModal()
}
function previewText(body: string): string {
  const flat = body.replace(/\s+/g, ' ').trim()
  return flat.length > 60 ? `${flat.slice(0, 60)}…` : flat
}
function kindBadgeClasses(k: Kind): string {
  switch (k) {
    case 'ELECTION': return 'bg-emerald-200 text-emerald-900'
    case 'QUESTIONNAIRE': return 'bg-indigo-200 text-indigo-900'
    case 'BALLOT_MEASURE': return 'bg-rose-200 text-rose-900'
  }
}
function kindLabel(k: Kind): string {
  switch (k) {
    case 'ELECTION': return t('admin.managePolls.kindElection')
    case 'QUESTIONNAIRE': return t('admin.managePolls.kindQuestionnaire')
    case 'BALLOT_MEASURE': return t('admin.managePolls.kindBallotMeasure')
  }
}

onMounted(async () => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onEsc)
  await loadPurview()
  await loadTitleSuggestions()
  await fetchPolls()
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onEsc)
})
</script>

<template>
  <div class="mx-auto max-w-6xl py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('admin.managePolls.heading') }}</h1>

    <form
      @submit.prevent="searchNow"
      class="mb-4 grid grid-cols-1 items-start gap-3 rounded-md bg-slate-50 p-4 sm:grid-cols-[repeat(auto-fit,minmax(180px,1fr))]"
    >
      <!-- Poll type -->
      <fieldset class="flex flex-col gap-1 text-sm font-semibold text-slate-700 sm:col-span-2">
        <legend>{{ $t('admin.managePolls.pollType') }}</legend>
        <div class="flex flex-wrap items-center gap-x-4 gap-y-1 font-normal">
          <label class="flex items-center gap-1">
            <input v-model="kindFilter.ELECTION" type="checkbox" class="h-4 w-4" />
            {{ $t('admin.managePolls.kindElection') }}
          </label>
          <label class="flex items-center gap-1">
            <input v-model="kindFilter.QUESTIONNAIRE" type="checkbox" class="h-4 w-4" />
            {{ $t('admin.managePolls.kindQuestionnaire') }}
          </label>
          <label class="flex items-center gap-1">
            <input v-model="kindFilter.BALLOT_MEASURE" type="checkbox" class="h-4 w-4" />
            {{ $t('admin.managePolls.kindBallotMeasure') }}
          </label>
        </div>
        <!-- Show disabled on its own line so it sits directly under the
             Election checkbox above. -->
        <label class="flex items-center gap-1 font-normal">
          <input v-model="showDisabled" type="checkbox" class="h-4 w-4" />
          {{ $t('admin.managePolls.showDisabled') }}
        </label>
      </fieldset>

      <!-- Title contains -->
      <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
        {{ $t('admin.managePolls.titleContains') }}
        <input
          v-model="titleFilter"
          type="text"
          list="poll-title-suggestions"
          autocomplete="off"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        />
        <datalist id="poll-title-suggestions">
          <option v-for="s in titleSuggestions" :key="s" :value="s" />
        </datalist>
      </label>

      <!-- State picker -->
      <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
        {{ $t('admin.managePolls.state') }}
        <div data-state-picker class="relative">
          <button type="button" @click.stop="statePickerOpen = !statePickerOpen"
            class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50">
            <span>{{ statePickerSummary }}</span>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="ml-2 h-4 w-4 text-slate-500 transition-transform" :class="{ 'rotate-180': statePickerOpen }" aria-hidden="true"><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
          </button>
          <div v-if="statePickerOpen" tabindex="0" @keydown="onStateKeydown"
            class="absolute left-0 right-0 z-20 mt-1 max-h-48 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg focus:outline-none focus:ring-1 focus:ring-slate-400">
            <p v-if="purview.states.length === 0" class="px-2 py-1 text-xs text-slate-500">{{ $t('admin.managePolls.purviewEmpty') }}</p>
            <label v-for="(s, idx) in purview.states" :key="s.id" class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50">
              <input type="checkbox" :checked="selectedStateIds.includes(s.id)" @click="onStateClick($event, idx, s.id)" class="h-3.5 w-3.5" />
              <span>{{ s.name }}</span>
            </label>
          </div>
        </div>
      </label>

      <!-- County picker -->
      <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
        {{ $t('admin.managePolls.county') }}
        <div data-county-picker class="relative">
          <button type="button" @click.stop="countyPickerOpen = !countyPickerOpen"
            class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50">
            <span>{{ countyPickerSummary }}</span>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="ml-2 h-4 w-4 text-slate-500 transition-transform" :class="{ 'rotate-180': countyPickerOpen }" aria-hidden="true"><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
          </button>
          <div v-if="countyPickerOpen" tabindex="0" @keydown="onCountyKeydown"
            class="absolute left-0 right-0 z-20 mt-1 max-h-48 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg focus:outline-none focus:ring-1 focus:ring-slate-400">
            <p v-if="displayedCounties.length === 0" class="px-2 py-1 text-xs text-slate-500">{{ $t('admin.managePolls.countyEmpty') }}</p>
            <label v-for="(c, idx) in displayedCounties" :key="c.id" class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50">
              <input type="checkbox" :checked="selectedCountyIds.includes(c.id)" @click="onCountyClick($event, idx, c.id)" class="h-3.5 w-3.5" />
              <span>{{ c.name }}</span>
            </label>
          </div>
        </div>
      </label>

      <!-- Zipcode picker -->
      <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
        {{ $t('admin.managePolls.zipcode') }}
        <div v-if="displayedZipcodes.length === 0" class="rounded border border-slate-300 bg-slate-50 p-2 text-xs font-normal text-slate-500">
          {{ $t('admin.managePolls.zipcodeNone') }}
        </div>
        <div v-else data-zip-picker class="relative">
          <button type="button" @click.stop="zipPickerOpen = !zipPickerOpen"
            class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50">
            <span class="font-mono">{{ zipPickerSummary }}</span>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="ml-2 h-4 w-4 text-slate-500 transition-transform" :class="{ 'rotate-180': zipPickerOpen }" aria-hidden="true"><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
          </button>
          <div v-if="zipPickerOpen" tabindex="0" @keydown="onZipKeydown"
            class="absolute left-0 right-0 z-20 mt-1 max-h-32 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg focus:outline-none focus:ring-1 focus:ring-slate-400">
            <label v-for="(z, idx) in displayedZipcodes" :key="z" class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50">
              <input type="checkbox" :checked="selectedZipcodes.includes(z)" @click="onZipClick($event, idx, z)" class="h-3.5 w-3.5" />
              <span class="font-mono">{{ z }}</span>
            </label>
          </div>
        </div>
      </label>

      <!-- Notes filter -->
      <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
        {{ $t('admin.managePolls.notesFilter') }}
        <input v-model="notesFilter" type="text" autocomplete="off"
          :placeholder="$t('admin.managePolls.notesPlaceholder')"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none" />
      </label>

      <!-- Search: live-filter still fires on every change, but the
           button cancels any pending debounce and runs the query now.
           Mirrors the label/input column structure so the button's top
           edge lines up with the top of the Notes input. -->
      <div class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
        <span aria-hidden="true" class="invisible">.</span>
        <button
          type="submit"
          :disabled="loading"
          class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
        >{{ loading ? $t('admin.managePolls.searching') : $t('admin.managePolls.search') }}</button>
      </div>
    </form>

    <p v-if="error" class="mb-2 text-sm text-red-700">{{ error }}</p>
    <p v-if="message" class="mb-2 text-sm text-green-700">{{ message }}</p>
    <p v-if="loading" class="mb-2 text-xs text-slate-500">{{ $t('common.loading') }}</p>
    <p v-if="!loading && results.length === 0" class="text-sm text-slate-500">{{ $t('admin.managePolls.none') }}</p>

    <table v-if="results.length > 0" class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th @click="toggleSort('title')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colTitle') }}{{ sortIndicator('title') }}</th>
          <th @click="toggleSort('type')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colType') }}{{ sortIndicator('type') }}</th>
          <th @click="toggleSort('creatorEmail')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colCreator') }}{{ sortIndicator('creatorEmail') }}</th>
          <th @click="toggleSort('stateInitial')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colState') }}{{ sortIndicator('stateInitial') }}</th>
          <th @click="toggleSort('countyName')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colCounty') }}{{ sortIndicator('countyName') }}</th>
          <th @click="toggleSort('zipcode')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colZipcode') }}{{ sortIndicator('zipcode') }}</th>
          <th @click="toggleSort('closeDate')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colCloses') }}{{ sortIndicator('closeDate') }}</th>
          <th @click="toggleSort('blocked')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colEnable') }}{{ sortIndicator('blocked') }}</th>
          <th @click="toggleSort('note')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colNote') }}{{ sortIndicator('note') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in sortedResults" :key="`${row.type}-${row.id}`">
          <td class="border-b border-slate-100 p-2">{{ row.title }}</td>
          <td class="border-b border-slate-100 p-2">
            <span :class="['rounded px-2 py-0.5 text-xs font-semibold', kindBadgeClasses(row.type)]">{{ kindLabel(row.type) }}</span>
          </td>
          <td class="border-b border-slate-100 p-2">{{ row.creatorEmail }}</td>
          <td class="border-b border-slate-100 p-2">{{ row.stateInitial ?? '—' }}</td>
          <td class="border-b border-slate-100 p-2">{{ row.countyName ?? '—' }}</td>
          <td class="border-b border-slate-100 p-2 font-mono">
            <template v-if="row.zipcodes.length <= 1">{{ row.zipcodes[0] ?? '—' }}</template>
            <template v-else>{{ row.zipcodes[0] }} <span class="text-slate-500">+{{ row.zipcodes.length - 1 }}</span></template>
          </td>
          <td class="border-b border-slate-100 p-2">
            {{ row.closeDate ? new Date(row.closeDate).toLocaleString() : $t('admin.managePolls.closeNever') }}
          </td>
          <td class="border-b border-slate-100 p-2">
            <input
              type="checkbox"
              :checked="!row.blocked"
              @click.prevent="onBlockCheckboxClick(row)"
              :title="row.blocked ? $t('admin.managePolls.statusDisabled') : $t('admin.managePolls.statusEnabled')"
              :aria-label="row.blocked ? $t('admin.managePolls.statusDisabled') : $t('admin.managePolls.statusEnabled')"
              class="h-4 w-4"
            />
          </td>
          <td class="border-b border-slate-100 p-2 text-xs">
            <template v-if="row.latestNote">
              <button type="button" @click="openNoteModal(row, 'edit')" class="text-slate-800 underline">
                {{ previewText(row.latestNote.body) }}
              </button>
            </template>
            <template v-else>
              <span class="text-slate-500">{{ $t('admin.managePolls.noteNone') }}</span>
              <button type="button" @click="openNoteModal(row, 'new')" class="ml-2 text-slate-800 underline">
                {{ $t('admin.managePolls.noteNew') }}
              </button>
            </template>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- Block modal -->
    <div v-if="blockModalOpen" role="dialog" aria-modal="true"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="closeBlockModal">
      <div class="w-full max-w-lg rounded-md bg-white p-4 shadow-lg">
        <header class="mb-2 flex items-center justify-between">
          <strong class="text-sm text-slate-800">{{ $t('admin.managePolls.blockModalTitle', { title: blockModalRow?.title ?? '' }) }}</strong>
          <button @click="closeBlockModal" :aria-label="$t('common.close')" class="rounded p-1 text-slate-500 hover:bg-slate-100">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="h-4 w-4" aria-hidden="true"><path fill="currentColor" d="M5.7 4.3 4.3 5.7 8.6 10l-4.3 4.3 1.4 1.4L10 11.4l4.3 4.3 1.4-1.4L11.4 10l4.3-4.3-1.4-1.4L10 8.6 5.7 4.3z" /></svg>
          </button>
        </header>

        <section v-if="blockModalExisting.length > 0" class="mb-3 rounded border border-slate-200 p-2">
          <h4 class="mb-1 text-xs font-semibold text-slate-700">{{ $t('admin.managePolls.activeBlocks') }}</h4>
          <ul class="m-0 list-none p-0 text-xs">
            <li v-for="b in blockModalExisting" :key="b.id" class="flex items-center justify-between border-b border-slate-100 py-1 last:border-b-0">
              <span>
                <strong>{{ b.scope }}</strong>
                <template v-if="b.scope === 'ZIPCODE'"> · <span class="font-mono">{{ b.zipcode }}</span></template>
                <template v-else-if="b.scope === 'COUNTY'"> · {{ b.countyName }}</template>
                <template v-else> · {{ b.stateInitial }}</template>
              </span>
              <button type="button" @click="removeBlock(b)" :disabled="blockModalBusy"
                class="rounded border border-slate-300 px-2 py-0.5 text-xs hover:bg-slate-50 disabled:opacity-50">
                {{ $t('admin.managePolls.removeBlock') }}
              </button>
            </li>
          </ul>
        </section>

        <h4 class="mb-1 text-xs font-semibold text-slate-700">{{ $t('admin.managePolls.addBlock') }}</h4>
        <fieldset class="mb-2 flex flex-col gap-2 text-sm">
          <label class="flex items-center gap-2">
            <input type="radio" value="ZIPCODE" v-model="blockModalScope" />
            <span>{{ $t('admin.managePolls.scopeZipcode') }}</span>
            <select v-if="blockModalScope === 'ZIPCODE'" v-model="blockModalZip"
              class="ml-2 rounded border border-slate-300 p-1 text-sm">
              <option v-for="z in blockModalRow?.zipcodes ?? []" :key="z" :value="z">{{ z }}</option>
            </select>
          </label>
          <label class="flex items-center gap-2">
            <input type="radio" value="COUNTY" v-model="blockModalScope" />
            <span>{{ $t('admin.managePolls.scopeCounty') }}</span>
            <span v-if="blockModalScope === 'COUNTY'" class="ml-2 text-slate-700">{{ blockModalRow?.countyName ?? '—' }}</span>
          </label>
          <label class="flex items-center gap-2">
            <input type="radio" value="STATE" v-model="blockModalScope" />
            <span>{{ $t('admin.managePolls.scopeState') }}</span>
            <span v-if="blockModalScope === 'STATE'" class="ml-2 text-slate-700">{{ blockModalRow?.stateInitial ?? '—' }}</span>
          </label>
        </fieldset>
        <p v-if="blockModalError" class="mb-2 text-xs text-red-700">{{ blockModalError }}</p>
        <div class="flex justify-end gap-2">
          <button type="button" @click="closeBlockModal"
            class="rounded border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50">{{ $t('common.close') }}</button>
          <button type="button" @click="applyBlock" :disabled="blockModalBusy"
            class="rounded bg-red-700 px-3 py-1 text-sm text-white hover:bg-red-800 disabled:opacity-60">
            {{ $t('admin.managePolls.disableButton') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Note modal -->
    <div v-if="noteModalOpen" role="dialog" aria-modal="true"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="closeNoteModal">
      <div class="w-full max-w-lg rounded-md bg-white p-4 shadow-lg">
        <header class="mb-2 flex items-center justify-between">
          <strong class="text-sm text-slate-800">
            {{ noteModalMode === 'new'
              ? $t('admin.managePolls.noteModalNewTitle', { title: noteModalRow?.title ?? '' })
              : $t('admin.managePolls.noteModalEditTitle', { title: noteModalRow?.title ?? '' }) }}
          </strong>
          <button @click="closeNoteModal" :aria-label="$t('common.close')" class="rounded p-1 text-slate-500 hover:bg-slate-100">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="h-4 w-4" aria-hidden="true"><path fill="currentColor" d="M5.7 4.3 4.3 5.7 8.6 10l-4.3 4.3 1.4 1.4L10 11.4l4.3 4.3 1.4-1.4L11.4 10l4.3-4.3-1.4-1.4L10 8.6 5.7 4.3z" /></svg>
          </button>
        </header>
        <p v-if="noteModalMode === 'edit' && noteModalNotes.length > 0" class="mb-1 text-xs text-slate-500">
          {{ $t('admin.managePolls.noteModalIndex', {
            i: noteModalNotes.length - noteModalIdx,
            n: noteModalNotes.length,
            when: new Date(noteModalNotes[noteModalIdx].createdAt).toLocaleString()
          }) }}
        </p>
        <textarea ref="noteTextarea" v-model="noteModalBody" maxlength="2000" rows="8"
          class="mb-1 w-full rounded border border-slate-300 p-2 text-sm text-slate-900 focus:border-slate-500 focus:outline-none" />
        <p class="mb-2 text-right text-xs text-slate-500">{{ noteModalBody.length }}/2000</p>
        <p v-if="noteModalError" class="mb-2 text-xs text-red-700">{{ noteModalError }}</p>
        <label v-if="noteModalMode === 'new'" class="mb-3 flex items-center gap-2 text-xs text-slate-700">
          <input v-model="noteModalSendEmail" type="checkbox" class="h-4 w-4" />
          {{ $t('admin.managePolls.sendToCreator', { email: noteModalRow?.creatorEmail ?? '' }) }}
        </label>
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex gap-2 text-xs">
            <button v-if="noteModalMode === 'edit'" type="button" :disabled="noteModalIdx >= noteModalNotes.length - 1" @click="notePrev"
              class="rounded border border-slate-300 px-2 py-1 hover:bg-slate-50 disabled:opacity-50">{{ $t('admin.managePolls.noteModalPrev') }}</button>
            <button v-if="noteModalMode === 'edit'" type="button" :disabled="noteModalIdx <= 0" @click="noteNext"
              class="rounded border border-slate-300 px-2 py-1 hover:bg-slate-50 disabled:opacity-50">{{ $t('admin.managePolls.noteModalNext') }}</button>
            <button v-if="noteModalMode === 'edit'" type="button" @click="noteStartNew"
              class="rounded border border-slate-300 px-2 py-1 hover:bg-slate-50">{{ $t('admin.managePolls.noteNew') }}</button>
            <button v-else-if="noteModalNotes.length > 0" type="button" @click="noteBackToEdit"
              class="rounded border border-slate-300 px-2 py-1 hover:bg-slate-50">{{ $t('admin.managePolls.noteModalBack') }}</button>
          </div>
          <button type="button" @click="saveNote" :disabled="noteModalSaving"
            class="rounded bg-slate-800 px-4 py-1.5 text-sm text-white hover:bg-slate-900 disabled:opacity-60">
            {{ noteModalSaving ? $t('common.saving') : $t('common.save') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
