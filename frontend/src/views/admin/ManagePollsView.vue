<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ColumnChooser from '@/components/ColumnChooser.vue'
import ScrollEnds from '@/components/ScrollEnds.vue'
import { useColumns } from '@/composables/useColumns'

const { t } = useI18n()

// ---------- Columns: choose which to show (Note and Actions always show) ----------
// State and County were dropped from the results (2026-10-28); the Purview column shows the area.
const COLUMN_KEYS = ['title', 'type', 'creator', 'purview', 'closes', 'enabled'] as const
type ColumnKey = (typeof COLUMN_KEYS)[number]
const columns = useColumns<ColumnKey>('managePolls.columns', COLUMN_KEYS, ['creator', 'purview'])
const columnOptions = computed(() => ([
  ['title', 'colTitle'], ['type', 'colType'], ['creator', 'colCreator'],
  ['purview', 'colPurview'], ['closes', 'colCloses'], ['enabled', 'colEnable'],
] as const).map(([key, label]) => ({ key, label: t(`admin.managePolls.${label}`) })))
// Deep-link support, e.g. the Polls link on /admin/manage-creators:
// ?creator=<email>&sort=closeDate&dir=desc&showDisabled=1
const route = useRoute()
const router = useRouter()
const q = (k: string): string => {
  const v = route?.query?.[k]
  return typeof v === 'string' ? v : ''
}

type Kind = 'ELECTION' | 'QUESTIONNAIRE' | 'BALLOT_MEASURE'
type Scope = 'ZIPCODE' | 'COUNTY' | 'STATE' | 'EVERYWHERE'
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
  regionLabel: string
  blocked: boolean
  latestNote: NoteDto | null
  nationwide?: boolean
  // Questionnaires and ballot measures; elections have none.
  summary?: string | null
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
const showDisabled = ref(q('showDisabled') === '1')
/** Nationwide polls are hidden unless this is on (2026-10-28). */
const showNationwide = ref(q('showNationwide') === '1')
/** Exact creator email the list is narrowed to (from ?creator=). */
const creatorFilter = ref(q('creator'))
function clearCreatorFilter() {
  creatorFilter.value = ''
  const { creator: _drop, ...rest } = route?.query ?? {}
  router?.replace({ query: rest })
  scheduleFetch()
}
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
/**
 * Rows the admin has toggled Enabled on since the last filter change. They stay
 * visible even after being disabled (with "Show disabled" off) so the row
 * doesn't vanish under the cursor, letting the admin re-enable it in place.
 * Cleared whenever a filter changes, at which point normal hiding resumes.
 */
const stickyKeys = ref(new Set<string>())
function rowKey(r: { type: Kind; id: number }): string { return `${r.type}-${r.id}` }
const visibleResults = computed<PollRow[]>(() =>
  results.value.filter(r =>
    (showDisabled.value || !r.blocked || stickyKeys.value.has(rowKey(r))) &&
    (showNationwide.value || !r.nationwide))
)
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
  let body: { countyIds?: number[]; stateIds?: number[] }
  if (selectedCountyIds.value.length > 0) {
    body = { countyIds: selectedCountyIds.value }
  } else if (selectedStateIds.value.length > 0) {
    body = { stateIds: selectedStateIds.value }
  } else {
    purview.value = { ...purview.value, zipcodes: [] }
    return
  }
  try {
    const res = await axios.post<{ zipcode: string }[]>('/api/zipcodes', body)
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
  stickyKeys.value = new Set()
  if (fetchTimer) clearTimeout(fetchTimer)
  fetchTimer = setTimeout(fetchPolls, 150)
}
/** Run the search now — used by the Search button and Enter-in-form. */
function searchNow() {
  stickyKeys.value = new Set()
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
    // Always fetch disabled rows; visibleResults hides them unless "Show
    // disabled" is on or the row is sticky (just toggled by the admin).
    params.includeDisabled = 'true'
    if (creatorFilter.value) params.creatorEmail = creatorFilter.value
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
// Disabled rows are already fetched; toggling only re-filters (and ends stickiness).
watch(showDisabled, () => { stickyKeys.value = new Set() })
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
const SORT_KEYS: SortKey[] = ['title', 'type', 'creatorEmail', 'stateInitial', 'countyName', 'zipcode', 'closeDate', 'blocked', 'note']
const sortKey = ref<SortKey>(SORT_KEYS.includes(q('sort') as SortKey) ? (q('sort') as SortKey) : 'title')
const sortDir = ref<'asc' | 'desc'>(q('dir') === 'desc' ? 'desc' : 'asc')
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
  return visibleResults.value.slice().sort((a, b) => {
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
  stickyKeys.value = new Set(stickyKeys.value).add(rowKey(row))
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

// ---------- notes modal (2026-10-28) ----------
// The Notes cell opens this: an Add note button (showing a 2000-character box
// with Cancel and Save), then every note on the poll, newest first. A saved
// note goes to the top of the list and becomes the cell's preview.
const noteModalOpen = ref(false)
const noteModalRow = ref<PollRow | null>(null)
const noteModalNotes = ref<NoteDto[]>([])
const noteComposing = ref(false)
const noteModalBody = ref('')
const noteModalSendEmail = ref(false)
const noteModalSaving = ref(false)
const noteModalError = ref<string | null>(null)
const noteTextarea = ref<HTMLTextAreaElement | null>(null)

async function openNoteModal(row: PollRow) {
  noteModalRow.value = row
  noteModalError.value = null
  noteModalNotes.value = []
  noteComposing.value = false
  noteModalBody.value = ''
  noteModalSendEmail.value = false
  noteModalOpen.value = true
  try {
    noteModalNotes.value = (await axios.get<NoteDto[]>(`/api/admin/polls/${row.type}/${row.id}/notes`)).data
    if (noteModalNotes.value.length === 0) await startNote()
  } catch (e: any) {
    noteModalError.value = e?.response?.data?.message ?? t('admin.managePolls.errorNotes')
  }
}
function closeNoteModal() {
  noteModalOpen.value = false
  noteModalRow.value = null
  noteModalNotes.value = []
  noteModalBody.value = ''
}
async function startNote() {
  noteComposing.value = true
  noteModalError.value = null
  await nextTick()
  noteTextarea.value?.focus()
}
function cancelNote() {
  noteComposing.value = false
  noteModalBody.value = ''
  noteModalSendEmail.value = false
  noteModalError.value = null
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
    const res = await axios.post<NoteDto>(
      `/api/admin/polls/${noteModalRow.value.type}/${noteModalRow.value.id}/notes`,
      { body, sendEmail: noteModalSendEmail.value }
    )
    noteModalNotes.value = [res.data, ...noteModalNotes.value]
    const updated = { ...noteModalRow.value, latestNote: res.data }
    results.value = results.value.map(r => (r.id === updated.id && r.type === updated.type ? updated : r))
    noteModalRow.value = updated
    cancelNote()
  } catch (e: any) {
    noteModalError.value = e?.response?.data?.message ?? t('admin.managePolls.errorSaveNote')
  } finally {
    noteModalSaving.value = false
  }
}

// ---------- edit modal (status / close date + reason + notify) ----------
const EDITABLE_STATUSES = ['PUBLISHED', 'CLOSED', 'ARCHIVED'] as const
const editModalOpen = ref(false)
const editModalRow = ref<PollRow | null>(null)
const editTitle = ref('')
const editSummary = ref('')
const editStatus = ref<string>('PUBLISHED')
const editCloseDate = ref('')
const editReason = ref('')
const editNotify = ref(false)
const editSaving = ref(false)
const editError = ref<string | null>(null)

/** ISO instant → value for a `datetime-local` input, in the admin's local zone. */
function toLocalInput(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function openEditModal(row: PollRow) {
  editModalRow.value = row
  editTitle.value = row.title
  editSummary.value = row.summary ?? ''
  editStatus.value = (EDITABLE_STATUSES as readonly string[]).includes(row.status) ? row.status : 'PUBLISHED'
  editCloseDate.value = toLocalInput(row.closeDate)
  editReason.value = ''
  editNotify.value = false
  editError.value = null
  editModalOpen.value = true
}
function closeEditModal() {
  editModalOpen.value = false
  editModalRow.value = null
}
async function saveEdit() {
  if (!editModalRow.value) return
  const reason = editReason.value.trim()
  if (reason.length === 0 || reason.length > 2000) {
    editError.value = t('admin.managePolls.errorReasonLength'); return
  }
  editSaving.value = true
  editError.value = null
  try {
    // A pre-filled close date is sent back unchanged so it isn't cleared;
    // an empty input leaves the (already-null) close date unchanged.
    const closeDate = editCloseDate.value ? new Date(editCloseDate.value).toISOString() : null
    const title = editTitle.value.trim()
    if (!title) { editError.value = t('admin.managePolls.errorTitle'); return }
    const hasSummary = editModalRow.value.type !== 'ELECTION'
    const summary = editSummary.value.trim()
    if (hasSummary && !summary) { editError.value = t('admin.managePolls.errorSummary'); return }
    await axios.put<NoteDto>(
      `/api/admin/polls/${editModalRow.value.type}/${editModalRow.value.id}`,
      { title: title !== editModalRow.value.title ? title : null,
        summary: hasSummary && summary !== (editModalRow.value.summary ?? '').trim() ? summary : null,
        status: editStatus.value, closeDate, reason, notifyCreator: editNotify.value }
    )
    message.value = t('admin.managePolls.editSaved')
    closeEditModal()
    // Reload: an election's new close date also changes its ballot measures' rows.
    await fetchPolls()
  } catch (e: any) {
    editError.value = e?.response?.data?.message ?? t('admin.managePolls.errorSaveEdit')
  } finally {
    editSaving.value = false
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
  if (editModalOpen.value) closeEditModal()
}
/** The Notes cell: the latest note's first 10 characters, then "..." (2026-10-28). */
function previewText(body: string): string {
  const flat = body.replace(/\s+/g, ' ').trim()
  return flat.length > 10 ? `${flat.slice(0, 10)}...` : flat
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

/** The History page's poll type for a row (rows use ELECTION / QUESTIONNAIRE / BALLOT_MEASURE). */
function historySlug(kind: Kind): string {
  return kind === 'BALLOT_MEASURE' ? 'ballot-measure' : kind.toLowerCase()
}
</script>

<template>
  <div class="mx-auto max-w-6xl py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('admin.managePolls.heading') }}</h1>
    <div v-if="creatorFilter" class="mb-3">
      <span data-test="creator-chip" class="inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1 text-sm text-blue-900">
        {{ $t('admin.managePolls.creatorFilter', { email: creatorFilter }) }}
        <button type="button" :aria-label="$t('admin.managePolls.clearCreator')" class="text-blue-700 hover:text-blue-950" @click="clearCreatorFilter">✕</button>
      </span>
    </div>

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
        <label class="flex items-center gap-1 font-normal">
          <input v-model="showNationwide" type="checkbox" class="h-4 w-4" data-test="show-nationwide" />
          {{ $t('admin.managePolls.showNationwide') }}
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
    <p v-if="!loading && visibleResults.length === 0" class="text-sm text-slate-500">{{ $t('admin.managePolls.none') }}</p>

    <ScrollEnds v-if="visibleResults.length > 0">
    <ColumnChooser v-model="columns" :options="columnOptions" />
    <!-- Phones: one card per poll (2026-10-28), in the table's sort order; wider screens: the table. -->
    <ul class="m-0 flex list-none flex-col gap-3 p-0 sm:hidden" data-test="poll-cards">
      <li v-for="row in sortedResults" :key="`card-${row.type}-${row.id}`" class="rounded-md border border-slate-200 p-3 text-sm">
        <div class="mb-1 flex flex-wrap items-start justify-between gap-2">
          <span class="font-semibold text-slate-800">{{ row.title }}</span>
          <span v-if="columns.type" :class="['rounded px-2 py-0.5 text-xs font-semibold', kindBadgeClasses(row.type)]">{{ kindLabel(row.type) }}</span>
        </div>
        <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-xs text-slate-600">
          <template v-if="columns.creator"><dt>{{ $t('admin.managePolls.colCreator') }}</dt><dd class="m-0 break-all">{{ row.creatorEmail }}</dd></template>
          <template v-if="columns.purview">
            <dt>{{ $t('admin.managePolls.colPurview') }}</dt>
            <dd class="m-0">
              <template v-if="row.zipcodes.length === 0">{{ row.regionLabel || '—' }}</template>
              <template v-else>
                <span class="font-mono">{{ row.zipcodes[0] }}</span>
                <span v-if="row.zipcodes.length > 1" class="text-slate-500"> +{{ row.zipcodes.length - 1 }}</span>
              </template>
            </dd>
          </template>
          <template v-if="columns.closes">
            <dt>{{ $t('admin.managePolls.colCloses') }}</dt>
            <dd class="m-0">{{ row.closeDate ? new Date(row.closeDate).toLocaleString() : $t('admin.managePolls.closeNever') }}</dd>
          </template>
          <template v-if="columns.enabled">
            <dt>{{ $t('admin.managePolls.colEnable') }}</dt>
            <dd class="m-0">
              <input
                type="checkbox"
                :checked="!row.blocked"
                @click.prevent="onBlockCheckboxClick(row)"
                :aria-label="row.blocked ? $t('admin.managePolls.statusDisabled') : $t('admin.managePolls.statusEnabled')"
                class="h-4 w-4 align-middle"
                data-test="card-enabled"
              />
            </dd>
          </template>
          <dt>{{ $t('admin.managePolls.colNote') }}</dt>
          <dd class="m-0">
            <button type="button" @click="openNoteModal(row)" class="text-slate-800 underline" data-test="card-notes">
              {{ row.latestNote ? previewText(row.latestNote.body) : $t('admin.managePolls.noteAdd') }}
            </button>
          </dd>
        </dl>
        <div class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1">
          <button type="button" @click="openEditModal(row)" data-test="card-edit"
            class="rounded border border-slate-300 px-3 py-1 text-xs hover:bg-slate-50">
            {{ $t('admin.managePolls.editButton') }}
          </button>
          <router-link
            :to="{ path: '/history', query: { type: historySlug(row.type), id: String(row.id) } }"
            class="text-xs text-slate-800 underline"
          >{{ $t('history.link') }}</router-link>
        </div>
      </li>
    </ul>
    <div class="hidden sm:block">
    <table class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th v-if="columns.title" @click="toggleSort('title')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colTitle') }}{{ sortIndicator('title') }}</th>
          <th v-if="columns.type" @click="toggleSort('type')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colType') }}{{ sortIndicator('type') }}</th>
          <th v-if="columns.creator" @click="toggleSort('creatorEmail')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colCreator') }}{{ sortIndicator('creatorEmail') }}</th>
          <th v-if="columns.purview" @click="toggleSort('zipcode')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colPurview') }}{{ sortIndicator('zipcode') }}</th>
          <th v-if="columns.closes" @click="toggleSort('closeDate')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colCloses') }}{{ sortIndicator('closeDate') }}</th>
          <th v-if="columns.enabled" @click="toggleSort('blocked')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colEnable') }}{{ sortIndicator('blocked') }}</th>
          <th @click="toggleSort('note')" class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100">{{ $t('admin.managePolls.colNote') }}{{ sortIndicator('note') }}</th>
          <th class="select-none border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.managePolls.colActions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in sortedResults" :key="`${row.type}-${row.id}`">
          <td v-if="columns.title" class="border-b border-slate-100 p-2">{{ row.title }}</td>
          <td v-if="columns.type" class="border-b border-slate-100 p-2">
            <span :class="['rounded px-2 py-0.5 text-xs font-semibold', kindBadgeClasses(row.type)]">{{ kindLabel(row.type) }}</span>
          </td>
          <td v-if="columns.creator" class="border-b border-slate-100 p-2">{{ row.creatorEmail }}</td>
          <td v-if="columns.purview" class="border-b border-slate-100 p-2 font-mono">
            <template v-if="row.zipcodes.length === 0"><span class="text-slate-700">{{ row.regionLabel || '—' }}</span></template>
            <template v-else-if="row.zipcodes.length === 1">{{ row.zipcodes[0] }}</template>
            <template v-else>{{ row.zipcodes[0] }} <span class="text-slate-500">+{{ row.zipcodes.length - 1 }}</span></template>
          </td>
          <td v-if="columns.closes" class="border-b border-slate-100 p-2">
            {{ row.closeDate ? new Date(row.closeDate).toLocaleString() : $t('admin.managePolls.closeNever') }}
          </td>
          <td v-if="columns.enabled" class="border-b border-slate-100 p-2">
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
            <button type="button" @click="openNoteModal(row)" class="whitespace-nowrap text-slate-800 underline" data-test="notes-cell"
              :title="row.latestNote?.body">
              {{ row.latestNote ? previewText(row.latestNote.body) : $t('admin.managePolls.noteAdd') }}
            </button>
          </td>
          <td class="border-b border-slate-100 p-2">
            <button type="button" @click="openEditModal(row)"
              class="rounded border border-slate-300 px-2 py-0.5 text-xs hover:bg-slate-50">
              {{ $t('admin.managePolls.editButton') }}
            </button>
            <router-link
              :to="{ path: '/history', query: { type: historySlug(row.type), id: String(row.id) } }"
              class="ml-2 text-xs text-slate-800 underline"
              data-test="poll-history"
            >{{ $t('history.link') }}</router-link>
          </td>
        </tr>
      </tbody>
    </table>
    </div>
    </ScrollEnds>

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
                <template v-else-if="b.scope === 'STATE'"> · {{ b.stateInitial }}</template>
                <template v-else> · {{ $t('admin.managePolls.scopeEverywhereLabel') }}</template>
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
          <label class="flex items-center gap-2">
            <input type="radio" value="EVERYWHERE" v-model="blockModalScope" />
            <span>{{ $t('admin.managePolls.scopeEverywhere') }}</span>
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

    <!-- Notes modal -->
    <div v-if="noteModalOpen" role="dialog" aria-modal="true" data-test="notes-modal"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="closeNoteModal">
      <div class="flex max-h-[90vh] w-full max-w-lg flex-col rounded-md bg-white p-4 shadow-lg">
        <header class="mb-3 flex items-center justify-between">
          <strong class="text-sm text-slate-800">{{ $t('admin.managePolls.notesModalTitle', { title: noteModalRow?.title ?? '' }) }}</strong>
          <button @click="closeNoteModal" :aria-label="$t('common.close')" class="rounded p-1 text-slate-500 hover:bg-slate-100">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="h-4 w-4" aria-hidden="true"><path fill="currentColor" d="M5.7 4.3 4.3 5.7 8.6 10l-4.3 4.3 1.4 1.4L10 11.4l4.3 4.3 1.4-1.4L11.4 10l4.3-4.3-1.4-1.4L10 8.6 5.7 4.3z" /></svg>
          </button>
        </header>

        <button v-if="!noteComposing" type="button" @click="startNote" data-test="add-note"
          class="mb-3 self-start rounded bg-slate-800 px-3 py-1.5 text-sm text-white hover:bg-slate-900">
          {{ $t('admin.managePolls.noteAdd') }}
        </button>
        <div v-else class="mb-3">
          <textarea ref="noteTextarea" v-model="noteModalBody" maxlength="2000" rows="6" data-test="note-body"
            class="mb-1 w-full rounded border border-slate-300 p-2 text-sm text-slate-900 focus:border-slate-500 focus:outline-none" />
          <p class="mb-2 text-right text-xs text-slate-500">{{ noteModalBody.length }}/2000</p>
          <label class="mb-2 flex items-center gap-2 text-xs text-slate-700">
            <input v-model="noteModalSendEmail" type="checkbox" class="h-4 w-4" />
            {{ $t('admin.managePolls.sendToCreator', { email: noteModalRow?.creatorEmail ?? '' }) }}
          </label>
          <div class="flex justify-end gap-2">
            <button type="button" @click="cancelNote" data-test="note-cancel"
              class="rounded border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50">{{ $t('common.cancel') }}</button>
            <button type="button" @click="saveNote" :disabled="noteModalSaving" data-test="note-save"
              class="rounded bg-slate-800 px-4 py-1.5 text-sm text-white hover:bg-slate-900 disabled:opacity-60">
              {{ noteModalSaving ? $t('common.saving') : $t('common.save') }}
            </button>
          </div>
        </div>
        <p v-if="noteModalError" class="mb-2 text-xs text-red-700">{{ noteModalError }}</p>

        <h4 class="mb-1 text-xs font-semibold text-slate-700">{{ $t('admin.managePolls.notesHeading', { n: noteModalNotes.length }) }}</h4>
        <p v-if="noteModalNotes.length === 0" class="text-xs text-slate-500">{{ $t('admin.managePolls.notesEmpty') }}</p>
        <ul v-else class="m-0 list-none overflow-y-auto p-0" data-test="notes-list">
          <li v-for="n in noteModalNotes" :key="n.id" class="border-t border-slate-100 py-2 text-sm">
            <div class="mb-0.5 text-xs text-slate-500">
              {{ new Date(n.createdAt).toLocaleString() }}
              <template v-if="n.emailed"> · {{ $t('admin.managePolls.noteEmailed') }}</template>
            </div>
            <p class="m-0 whitespace-pre-wrap text-slate-800">{{ n.body }}</p>
          </li>
        </ul>
      </div>
    </div>

    <!-- Edit modal -->
    <div v-if="editModalOpen" role="dialog" aria-modal="true"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="closeEditModal">
      <div class="w-full max-w-lg rounded-md bg-white p-4 shadow-lg">
        <header class="mb-2 flex items-center justify-between">
          <strong class="text-sm text-slate-800">{{ $t('admin.managePolls.editModalTitle', { title: editModalRow?.title ?? '' }) }}</strong>
          <button @click="closeEditModal" :aria-label="$t('common.close')" class="rounded p-1 text-slate-500 hover:bg-slate-100">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="h-4 w-4" aria-hidden="true"><path fill="currentColor" d="M5.7 4.3 4.3 5.7 8.6 10l-4.3 4.3 1.4 1.4L10 11.4l4.3 4.3 1.4-1.4L11.4 10l4.3-4.3-1.4-1.4L10 8.6 5.7 4.3z" /></svg>
          </button>
        </header>

        <label class="mb-3 flex flex-col gap-1 text-sm font-semibold text-slate-700">
          {{ $t('admin.managePolls.editTitle') }}
          <input v-model="editTitle" type="text" maxlength="500" data-test="edit-title"
            class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none" />
        </label>

        <label v-if="editModalRow?.type !== 'ELECTION'" class="mb-3 flex flex-col gap-1 text-sm font-semibold text-slate-700">
          {{ $t('admin.managePolls.editSummary') }}
          <textarea v-model="editSummary" rows="4" data-test="edit-summary"
            class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none" />
        </label>

        <label class="mb-3 flex flex-col gap-1 text-sm font-semibold text-slate-700">
          {{ $t('admin.managePolls.editStatus') }}
          <select v-model="editStatus" class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none">
            <option value="PUBLISHED">{{ $t('admin.managePolls.statusPublished') }}</option>
            <option value="CLOSED">{{ $t('admin.managePolls.statusClosed') }}</option>
            <option value="ARCHIVED">{{ $t('admin.managePolls.statusArchived') }}</option>
          </select>
        </label>

        <label class="mb-3 flex flex-col gap-1 text-sm font-semibold text-slate-700">
          {{ $t('admin.managePolls.editCloseDate') }}
          <input v-model="editCloseDate" type="datetime-local"
            class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none" />
          <span v-if="editModalRow?.type === 'ELECTION'" class="text-xs font-normal text-slate-500">{{ $t('admin.managePolls.closeDateCarries') }}</span>
        </label>

        <label class="mb-1 flex flex-col gap-1 text-sm font-semibold text-slate-700">
          {{ $t('admin.managePolls.editReason') }}
          <textarea v-model="editReason" maxlength="2000" rows="5"
            class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none" />
        </label>
        <p class="mb-2 text-right text-xs text-slate-500">{{ editReason.length }}/2000</p>
        <p v-if="editError" class="mb-2 text-xs text-red-700">{{ editError }}</p>

        <label class="mb-3 flex items-center gap-2 text-xs text-slate-700">
          <input v-model="editNotify" type="checkbox" class="h-4 w-4" />
          {{ $t('admin.managePolls.sendToCreator', { email: editModalRow?.creatorEmail ?? '' }) }}
        </label>

        <div class="flex justify-end gap-2">
          <button type="button" @click="closeEditModal"
            class="rounded border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50">{{ $t('common.close') }}</button>
          <button type="button" @click="saveEdit" :disabled="editSaving"
            class="rounded bg-slate-800 px-4 py-1.5 text-sm text-white hover:bg-slate-900 disabled:opacity-60">
            {{ editSaving ? $t('common.saving') : $t('common.save') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
