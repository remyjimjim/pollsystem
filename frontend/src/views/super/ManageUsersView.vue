<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

type Role = 'USER' | 'CREATOR' | 'ADMIN'
interface MessageDto {
  id: number
  body: string
  emailed: boolean
  createdAt: string
  updatedAt: string
}
interface UserRow {
  id: number
  email: string
  phone: string
  access: Role | 'VIEWER' | 'SUPER'
  isEnabled: boolean
  zipcode: string
  stateInitial: string | null
  countyName: string | null
  latestMessage: MessageDto | null
}
interface StateRow { id: number; name: string; initial: string }
interface CountyRow { id: number; stateId: number; name: string }
interface CountyZipRow { id: number; countyId: number; zipcode: string }

// ---------- filter state ----------
const roleFilter = ref<Record<Role, boolean>>({ USER: true, CREATOR: true, ADMIN: true })
const showDisabled = ref(false)
const emailFilter = ref('')
const emailSuggestions = ref<string[]>([])
const messageFilter = ref('')

const states = ref<StateRow[]>([])
const counties = ref<CountyRow[]>([])
const zipcodeOptions = ref<CountyZipRow[]>([])
const selectedStateIds = ref<number[]>([])
const lastClickedStateIndex = ref<number | null>(null)
const selectedCountyIds = ref<number[]>([])
const lastClickedCountyIndex = ref<number | null>(null)
const selectedZipcodes = ref<string[]>([])
const lastClickedZipIndex = ref<number | null>(null)

const statePickerOpen = ref(false)
const countyPickerOpen = ref(false)
const zipPickerOpen = ref(false)

const countyFilter = ref('')
let countyFilterTimer: ReturnType<typeof setTimeout> | null = null
const zipFilter = ref('')
let zipFilterTimer: ReturnType<typeof setTimeout> | null = null

const results = ref<UserRow[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)

// ---------- API helpers ----------
async function loadStates() {
  try { states.value = (await axios.get<StateRow[]>('/api/states')).data } catch { /* non-fatal */ }
}
async function loadCounties(stateIds: number[]) {
  try {
    counties.value = (await axios.get<CountyRow[]>('/api/counties', {
      params: { state_id: stateIds.join(',') }
    })).data
  } catch { counties.value = [] }
}
async function loadCountiesByPrefix(prefix: string) {
  try {
    counties.value = (await axios.get<CountyRow[]>('/api/counties', { params: { prefix } })).data
  } catch { counties.value = [] }
}
async function loadZipcodesByState(stateIds: number[]) {
  try {
    zipcodeOptions.value = (await axios.get<CountyZipRow[]>('/api/zipcodes', {
      params: { state_id: stateIds.join(',') }
    })).data
  } catch { zipcodeOptions.value = [] }
}
async function loadZipcodesByCounty(countyIds: number[]) {
  try {
    zipcodeOptions.value = (await axios.get<CountyZipRow[]>('/api/zipcodes', {
      params: { county_ids: countyIds.join(',') }
    })).data
  } catch { zipcodeOptions.value = [] }
}
async function loadZipcodesByPrefix(prefix: string) {
  try {
    zipcodeOptions.value = (await axios.get<CountyZipRow[]>('/api/zipcodes', { params: { prefix } })).data
  } catch { zipcodeOptions.value = [] }
}

// ---------- cascade reactions ----------
watch(countyFilter, (newVal) => {
  if (selectedStateIds.value.length > 0) return
  if (countyFilterTimer) clearTimeout(countyFilterTimer)
  const trimmed = newVal.trim()
  if (trimmed === '') { counties.value = []; return }
  countyFilterTimer = setTimeout(() => loadCountiesByPrefix(trimmed), 200)
})
const displayedCounties = computed<CountyRow[]>(() => {
  if (selectedStateIds.value.length === 0) return counties.value
  const prefix = countyFilter.value.trim().toLowerCase()
  if (prefix === '') return counties.value
  return counties.value.filter(c => c.name.toLowerCase().startsWith(prefix))
})
watch(zipFilter, (newVal) => {
  if (selectedStateIds.value.length > 0) return
  if (zipFilterTimer) clearTimeout(zipFilterTimer)
  const trimmed = newVal.trim()
  if (trimmed === '') { zipcodeOptions.value = []; return }
  zipFilterTimer = setTimeout(() => loadZipcodesByPrefix(trimmed), 200)
})
const displayedZipcodes = computed<CountyZipRow[]>(() => {
  const prefix = zipFilter.value.trim()
  if (prefix === '') return zipcodeOptions.value
  return zipcodeOptions.value.filter(z => z.zipcode.startsWith(prefix))
})

async function onStateChange() {
  selectedCountyIds.value = []
  selectedZipcodes.value = []
  zipFilter.value = ''
  countyFilter.value = ''
  counties.value = []
  zipcodeOptions.value = []
  if (selectedStateIds.value.length > 0) {
    await loadCounties(selectedStateIds.value)
    await loadZipcodesByState(selectedStateIds.value)
  }
  scheduleFetch()
}
async function onCountyChange() {
  selectedZipcodes.value = []
  if (selectedCountyIds.value.length > 0) {
    await loadZipcodesByCounty(selectedCountyIds.value)
  } else if (selectedStateIds.value.length > 0) {
    await loadZipcodesByState(selectedStateIds.value)
  } else {
    zipcodeOptions.value = []
  }
  scheduleFetch()
}

// ---------- picker click/keyboard handlers (shift-range + Shift-* / Shift-0) ----------
function rangeUpdate<T>(arr: T[], current: T[], a: number, b: number, willCheck: boolean): T[] {
  const [start, end] = a < b ? [a, b] : [b, a]
  const slice = arr.slice(start, end + 1)
  if (willCheck) return Array.from(new Set([...current, ...slice]))
  const remove = new Set(slice as unknown[])
  return current.filter(x => !remove.has(x as unknown))
}

function onStateClick(e: MouseEvent, idx: number, id: number) {
  const target = e.target as HTMLInputElement
  const willBeChecked = target.checked
  if (e.shiftKey && lastClickedStateIndex.value !== null) {
    const rangeIds = states.value.slice(
      Math.min(lastClickedStateIndex.value, idx),
      Math.max(lastClickedStateIndex.value, idx) + 1
    ).map(s => s.id)
    selectedStateIds.value = rangeUpdate(rangeIds, selectedStateIds.value, 0, rangeIds.length - 1, willBeChecked)
  } else {
    selectedStateIds.value = willBeChecked
      ? Array.from(new Set([...selectedStateIds.value, id]))
      : selectedStateIds.value.filter(x => x !== id)
  }
  lastClickedStateIndex.value = idx
  onStateChange()
}
function onStateKeydown(e: KeyboardEvent) {
  const isSelectAll = e.key === '*' || (e.shiftKey && e.code === 'Digit8')
  const isDeselectAll = e.key === ')' || (e.shiftKey && e.code === 'Digit0')
  if (isSelectAll) {
    e.preventDefault(); selectedStateIds.value = states.value.map(s => s.id); onStateChange()
  } else if (isDeselectAll) {
    e.preventDefault(); selectedStateIds.value = []; onStateChange()
  }
}
function onCountyClick(e: MouseEvent, idx: number, id: number) {
  const target = e.target as HTMLInputElement
  const willBeChecked = target.checked
  if (e.shiftKey && lastClickedCountyIndex.value !== null) {
    const rangeIds = displayedCounties.value.slice(
      Math.min(lastClickedCountyIndex.value, idx),
      Math.max(lastClickedCountyIndex.value, idx) + 1
    ).map(c => c.id)
    selectedCountyIds.value = rangeUpdate(rangeIds, selectedCountyIds.value, 0, rangeIds.length - 1, willBeChecked)
  } else {
    selectedCountyIds.value = willBeChecked
      ? Array.from(new Set([...selectedCountyIds.value, id]))
      : selectedCountyIds.value.filter(x => x !== id)
  }
  lastClickedCountyIndex.value = idx
  onCountyChange()
}
function onCountyKeydown(e: KeyboardEvent) {
  const isSelectAll = e.key === '*' || (e.shiftKey && e.code === 'Digit8')
  const isDeselectAll = e.key === ')' || (e.shiftKey && e.code === 'Digit0')
  if (isSelectAll) {
    e.preventDefault(); selectedCountyIds.value = displayedCounties.value.map(c => c.id); onCountyChange()
  } else if (isDeselectAll) {
    e.preventDefault(); selectedCountyIds.value = []; onCountyChange()
  }
}
function onZipClick(e: MouseEvent, idx: number, code: string) {
  const target = e.target as HTMLInputElement
  const willBeChecked = target.checked
  if (e.shiftKey && lastClickedZipIndex.value !== null) {
    const rangeCodes = displayedZipcodes.value.slice(
      Math.min(lastClickedZipIndex.value, idx),
      Math.max(lastClickedZipIndex.value, idx) + 1
    ).map(z => z.zipcode)
    selectedZipcodes.value = rangeUpdate(rangeCodes, selectedZipcodes.value, 0, rangeCodes.length - 1, willBeChecked)
  } else {
    selectedZipcodes.value = willBeChecked
      ? Array.from(new Set([...selectedZipcodes.value, code]))
      : selectedZipcodes.value.filter(z => z !== code)
  }
  lastClickedZipIndex.value = idx
  scheduleFetch()
}
function onZipKeydown(e: KeyboardEvent) {
  const isSelectAll = e.key === '*' || (e.shiftKey && e.code === 'Digit8')
  const isDeselectAll = e.key === ')' || (e.shiftKey && e.code === 'Digit0')
  if (isSelectAll) {
    e.preventDefault(); selectedZipcodes.value = displayedZipcodes.value.map(z => z.zipcode); scheduleFetch()
  } else if (isDeselectAll) {
    e.preventDefault(); selectedZipcodes.value = []; scheduleFetch()
  }
}

// ---------- picker summaries ----------
const statePickerSummary = computed<string>(() => {
  if (selectedStateIds.value.length === 0) return t('super.manageUsers.stateAny')
  if (selectedStateIds.value.length === 1) {
    return states.value.find(s => s.id === selectedStateIds.value[0])?.name ?? t('super.manageUsers.stateAny')
  }
  return t('super.manageUsers.stateNSelected', { n: selectedStateIds.value.length })
})
const countyPickerSummary = computed<string>(() => {
  if (selectedCountyIds.value.length === 0) return t('super.manageUsers.countyAny')
  if (selectedCountyIds.value.length === 1) {
    return counties.value.find(c => c.id === selectedCountyIds.value[0])?.name ?? t('super.manageUsers.countyAny')
  }
  return t('super.manageUsers.countyNSelected', { n: selectedCountyIds.value.length })
})
const zipPickerSummary = computed<string>(() => {
  if (selectedZipcodes.value.length === 1) return selectedZipcodes.value[0]
  if (selectedZipcodes.value.length > 1) return t('super.manageUsers.zipcodeNSelected', { n: selectedZipcodes.value.length })
  return displayedZipcodes.value[0]?.zipcode ?? ''
})

// ---------- email autocomplete ----------
let emailSugTimer: ReturnType<typeof setTimeout> | null = null
watch(emailFilter, (newVal) => {
  if (emailSugTimer) clearTimeout(emailSugTimer)
  const trimmed = newVal.trim()
  if (trimmed === '') { emailSuggestions.value = []; scheduleFetch(); return }
  emailSugTimer = setTimeout(async () => {
    try {
      emailSuggestions.value = (await axios.get<string[]>('/api/super/users/emails', {
        params: { prefix: trimmed }
      })).data
    } catch { emailSuggestions.value = [] }
    scheduleFetch()
  }, 200)
})

watch(roleFilter, scheduleFetch, { deep: true })
watch(showDisabled, scheduleFetch)
// Free-text search of the message history. Debounced so each keystroke
// doesn't fire its own request.
let messageFilterTimer: ReturnType<typeof setTimeout> | null = null
watch(messageFilter, () => {
  if (messageFilterTimer) clearTimeout(messageFilterTimer)
  messageFilterTimer = setTimeout(scheduleFetch, 200)
})

// ---------- main fetch (debounced) ----------
let fetchTimer: ReturnType<typeof setTimeout> | null = null
function scheduleFetch() {
  if (fetchTimer) clearTimeout(fetchTimer)
  fetchTimer = setTimeout(fetchUsers, 150)
}
async function fetchUsers() {
  loading.value = true
  error.value = null
  try {
    const roles = (Object.keys(roleFilter.value) as Role[]).filter(r => roleFilter.value[r])
    const params: Record<string, string> = {}
    if (roles.length > 0) params.role = roles.join(',')
    if (showDisabled.value) params.includeDisabled = 'true'
    if (emailFilter.value.trim()) params.email = emailFilter.value.trim()
    if (messageFilter.value.trim()) params.message = messageFilter.value.trim()
    // Same cascade as /polls/search: zipcodes win, then counties, then states.
    if (selectedStateIds.value.length === 0 && zipFilter.value.trim()) {
      params.zipcode = zipFilter.value.trim()
    } else if (selectedZipcodes.value.length > 0) {
      params.zipcode = selectedZipcodes.value.join(',')
    } else if (selectedCountyIds.value.length > 0) {
      params.countyId = selectedCountyIds.value.join(',')
    } else if (selectedStateIds.value.length > 0) {
      params.stateId = selectedStateIds.value.join(',')
    }
    results.value = (await axios.get<UserRow[]>('/api/super/users', { params })).data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.manageUsers.errorLoad')
  } finally {
    loading.value = false
  }
}

// ---------- column sorting ----------
type SortKey = 'email' | 'access' | 'stateInitial' | 'countyName' | 'zipcode' | 'isEnabled' | 'msg'
const sortKey = ref<SortKey>('email')
const sortDir = ref<'asc' | 'desc'>('asc')
function toggleSort(key: SortKey) {
  if (sortKey.value === key) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortDir.value = 'asc'
  }
  // A new sort order breaks the previous shift-range anchor.
  lastClickedEnableIndex.value = null
}
function sortValue(row: UserRow, key: SortKey): string {
  switch (key) {
    case 'email': return row.email.toLowerCase()
    case 'access': return row.access
    case 'stateInitial': return (row.stateInitial ?? '').toLowerCase()
    case 'countyName': return (row.countyName ?? '').toLowerCase()
    case 'zipcode': return row.zipcode
    // Per the request: 'true' sorts alphabetically after 'false', so asc
    // groups disabled rows first, enabled last.
    case 'isEnabled': return row.isEnabled ? 'true' : 'false'
    case 'msg': return (row.latestMessage?.body ?? '').toLowerCase()
  }
}
const sortedResults = computed<UserRow[]>(() => {
  const dir = sortDir.value === 'asc' ? 1 : -1
  const k = sortKey.value
  return results.value.slice().sort((a, b) => {
    const av = sortValue(a, k)
    const bv = sortValue(b, k)
    if (av < bv) return -dir
    if (av > bv) return dir
    return 0
  })
})
function sortIndicator(key: SortKey): string {
  if (sortKey.value !== key) return ''
  return sortDir.value === 'asc' ? ' ▲' : ' ▼'
}

// ---------- enable/disable column ----------
const lastClickedEnableIndex = ref<number | null>(null)
function onEnableClick(e: MouseEvent, idx: number, row: UserRow) {
  e.preventDefault()
  const targetState = !row.isEnabled
  if (e.shiftKey && lastClickedEnableIndex.value !== null) {
    const a = Math.min(lastClickedEnableIndex.value, idx)
    const b = Math.max(lastClickedEnableIndex.value, idx)
    // Range is taken from the currently-visible (sorted) order so a
    // shift-click across two rows toggles everything the user sees
    // between them, not whatever the unsorted backend order is.
    const ids = sortedResults.value.slice(a, b + 1).map(r => r.id)
    bulkToggle(ids, targetState)
  } else {
    toggleEnabled(row.id)
  }
  lastClickedEnableIndex.value = idx
}
function onEnableHeaderKeydown(e: KeyboardEvent) {
  const isSelectAll = e.key === '*' || (e.shiftKey && e.code === 'Digit8')
  const isDeselectAll = e.key === ')' || (e.shiftKey && e.code === 'Digit0')
  if (isSelectAll) {
    e.preventDefault(); bulkToggle(results.value.map(r => r.id), true)
  } else if (isDeselectAll) {
    e.preventDefault(); bulkToggle(results.value.map(r => r.id), false)
  }
}
async function toggleEnabled(userId: number) {
  try {
    const res = await axios.post<UserRow>(`/api/super/users/${userId}/toggle-enabled`)
    replaceRow(res.data)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.manageUsers.errorToggle')
  }
}
async function bulkToggle(userIds: number[], enable: boolean) {
  try {
    const res = await axios.post<UserRow[]>('/api/super/users/bulk-toggle-enabled', { userIds, enable })
    const byId = new Map(res.data.map(r => [r.id, r]))
    results.value = results.value.map(r => byId.get(r.id) ?? r)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.manageUsers.errorToggle')
  }
}
function replaceRow(row: UserRow) {
  results.value = results.value.map(r => (r.id === row.id ? row : r))
}

// ---------- demote ----------
async function demote(row: UserRow) {
  if (!confirm(t('super.manageUsers.demoteConfirm', { email: row.email }))) return
  try {
    const res = await axios.post<UserRow>(`/api/super/users/${row.id}/demote`)
    // Demoted Admin becomes Creator; might be filtered out if Creator unchecked.
    replaceRow(res.data)
    message.value = t('super.manageUsers.demoted', { email: row.email })
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.manageUsers.errorDemote')
  }
}

// ---------- message modal ----------
const modalOpen = ref(false)
const modalUser = ref<UserRow | null>(null)
const modalMessages = ref<MessageDto[]>([])
const modalIdx = ref(0) // 0 = newest
const modalMode = ref<'edit' | 'new'>('edit')
const modalBody = ref('')
const modalSendEmail = ref(false)
const modalSaving = ref(false)
const modalError = ref<string | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

async function openMessageModal(row: UserRow, startMode: 'edit' | 'new' = 'edit') {
  modalUser.value = row
  modalError.value = null
  modalSendEmail.value = false
  modalMessages.value = []
  modalIdx.value = 0
  modalMode.value = startMode
  modalBody.value = ''
  modalOpen.value = true
  try {
    modalMessages.value = (await axios.get<MessageDto[]>(`/api/super/users/${row.id}/messages`)).data
    if (modalMessages.value.length === 0) {
      modalMode.value = 'new'
    } else if (startMode === 'edit') {
      modalBody.value = modalMessages.value[0].body
    }
  } catch (e: any) {
    modalError.value = e?.response?.data?.message ?? t('super.manageUsers.errorLoadMessages')
  }
  await nextTick()
  textareaRef.value?.focus()
}
function closeMessageModal() {
  modalOpen.value = false
  modalUser.value = null
  modalMessages.value = []
  modalBody.value = ''
}
function modalPrev() {
  if (modalMode.value !== 'edit') return
  if (modalIdx.value < modalMessages.value.length - 1) {
    modalIdx.value += 1
    modalBody.value = modalMessages.value[modalIdx.value].body
  }
}
function modalNext() {
  if (modalMode.value !== 'edit') return
  if (modalIdx.value > 0) {
    modalIdx.value -= 1
    modalBody.value = modalMessages.value[modalIdx.value].body
  }
}
function modalStartNew() {
  modalMode.value = 'new'
  modalBody.value = ''
  modalSendEmail.value = false
  nextTick(() => textareaRef.value?.focus())
}
function modalBackToEdit() {
  if (modalMessages.value.length === 0) return
  modalMode.value = 'edit'
  modalIdx.value = 0
  modalBody.value = modalMessages.value[0].body
}
async function saveModal() {
  if (!modalUser.value) return
  const body = modalBody.value.trim()
  if (body.length === 0 || body.length > 2000) {
    modalError.value = t('super.manageUsers.errorBodyLength'); return
  }
  modalSaving.value = true
  modalError.value = null
  try {
    if (modalMode.value === 'new') {
      const res = await axios.post<MessageDto>(`/api/super/users/${modalUser.value.id}/messages`, {
        body, sendEmail: modalSendEmail.value
      })
      modalMessages.value = [res.data, ...modalMessages.value]
      modalIdx.value = 0
      modalMode.value = 'edit'
      modalSendEmail.value = false
      // Reflect new latestMessage in the table row.
      if (modalUser.value) {
        const updated: UserRow = { ...modalUser.value, latestMessage: res.data }
        replaceRow(updated)
        modalUser.value = updated
      }
    } else {
      const current = modalMessages.value[modalIdx.value]
      const res = await axios.put<MessageDto>(`/api/super/users/messages/${current.id}`, { body })
      modalMessages.value = modalMessages.value.map((m, i) => (i === modalIdx.value ? res.data : m))
      // The newest message (index 0) is what shows in the table cell.
      if (modalIdx.value === 0 && modalUser.value) {
        const updated: UserRow = { ...modalUser.value, latestMessage: res.data }
        replaceRow(updated)
        modalUser.value = updated
      }
    }
  } catch (e: any) {
    modalError.value = e?.response?.data?.message ?? t('super.manageUsers.errorSaveMessage')
  } finally {
    modalSaving.value = false
  }
}

// ---------- global UI handlers ----------
function onDocClick(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  if (!target?.closest('[data-state-picker]')) statePickerOpen.value = false
  if (!target?.closest('[data-county-picker]')) countyPickerOpen.value = false
  if (!target?.closest('[data-zip-picker]')) zipPickerOpen.value = false
}
function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    statePickerOpen.value = false
    countyPickerOpen.value = false
    zipPickerOpen.value = false
    if (modalOpen.value) closeMessageModal()
  }
}
function previewText(body: string): string {
  const flat = body.replace(/\s+/g, ' ').trim()
  return flat.length > 60 ? `${flat.slice(0, 60)}…` : flat
}
function accessBadgeClasses(access: UserRow['access']): string {
  switch (access) {
    case 'ADMIN': return 'bg-sky-200 text-sky-900'
    case 'CREATOR': return 'bg-amber-200 text-amber-900'
    case 'USER': return 'bg-slate-200 text-slate-800'
    default: return 'bg-slate-100 text-slate-600'
  }
}

onMounted(() => {
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onEsc)
  loadStates()
  fetchUsers()
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onEsc)
})
</script>

<template>
  <div class="mx-auto max-w-6xl py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">{{ $t('super.manageUsers.heading') }}</h1>

    <form
      @submit.prevent
      class="mb-4 grid grid-cols-1 items-end gap-3 rounded-md bg-slate-50 p-4 sm:grid-cols-[repeat(auto-fit,minmax(180px,1fr))]"
    >
      <!-- Role -->
      <fieldset class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        <legend>{{ $t('super.manageUsers.role') }}</legend>
        <div class="flex flex-wrap gap-3 pt-1 font-normal">
          <label class="flex items-center gap-1">
            <input v-model="roleFilter.USER" type="checkbox" class="h-4 w-4" />
            {{ $t('super.manageUsers.roleUser') }}
          </label>
          <label class="flex items-center gap-1">
            <input v-model="roleFilter.CREATOR" type="checkbox" class="h-4 w-4" />
            {{ $t('super.manageUsers.roleCreator') }}
          </label>
          <label class="flex items-center gap-1">
            <input v-model="roleFilter.ADMIN" type="checkbox" class="h-4 w-4" />
            {{ $t('super.manageUsers.roleAdmin') }}
          </label>
        </div>
      </fieldset>

      <!-- Show disabled -->
      <label class="flex items-center gap-2 text-xs font-semibold text-slate-700">
        <input v-model="showDisabled" type="checkbox" class="h-4 w-4" />
        {{ $t('super.manageUsers.showDisabled') }}
      </label>

      <!-- Email contains -->
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        {{ $t('super.manageUsers.emailContains') }}
        <input
          v-model="emailFilter"
          type="text"
          list="user-email-suggestions"
          autocomplete="off"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        />
        <datalist id="user-email-suggestions">
          <option v-for="s in emailSuggestions" :key="s" :value="s" />
        </datalist>
      </label>

      <!-- State picker -->
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        <span :title="$t('super.manageUsers.stateHelp')" class="cursor-help">{{ $t('super.manageUsers.state') }}</span>
        <div data-state-picker class="relative">
          <button
            type="button"
            @click.stop="statePickerOpen = !statePickerOpen"
            :aria-expanded="statePickerOpen"
            class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50"
          >
            <span>{{ statePickerSummary }}</span>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="ml-2 h-4 w-4 text-slate-500 transition-transform" :class="{ 'rotate-180': statePickerOpen }" aria-hidden="true"><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
          </button>
          <div
            v-if="statePickerOpen"
            tabindex="0"
            @keydown="onStateKeydown"
            class="absolute left-0 right-0 z-20 mt-1 max-h-48 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg focus:outline-none focus:ring-1 focus:ring-slate-400"
          >
            <label v-for="(s, idx) in states" :key="s.id" class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50">
              <input type="checkbox" :checked="selectedStateIds.includes(s.id)" @click="onStateClick($event, idx, s.id)" class="h-3.5 w-3.5" />
              <span>{{ s.name }}</span>
            </label>
          </div>
        </div>
      </label>

      <!-- County picker -->
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        <span :title="$t('super.manageUsers.countyHelp')" class="cursor-help">{{ $t('super.manageUsers.county') }}</span>
        <div data-county-picker class="relative">
          <button
            type="button"
            @click.stop="countyPickerOpen = !countyPickerOpen"
            :aria-expanded="countyPickerOpen"
            class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50"
          >
            <span>{{ countyPickerSummary }}</span>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="ml-2 h-4 w-4 text-slate-500 transition-transform" :class="{ 'rotate-180': countyPickerOpen }" aria-hidden="true"><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
          </button>
          <div
            v-if="countyPickerOpen"
            tabindex="0"
            @keydown="onCountyKeydown"
            class="absolute left-0 right-0 z-20 mt-1 rounded border border-slate-300 bg-white text-sm font-normal text-slate-900 shadow-lg focus:outline-none focus:ring-1 focus:ring-slate-400"
          >
            <input
              v-model="countyFilter"
              type="text"
              autocomplete="off"
              :placeholder="selectedStateIds.length === 0 ? $t('super.manageUsers.countyTypeahead') : $t('super.manageUsers.countyFilter')"
              class="block w-full rounded-t border-b border-slate-300 p-2 text-xs font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
            />
            <div class="max-h-48 overflow-y-auto p-1">
              <div v-if="displayedCounties.length === 0" class="px-2 py-1 text-xs text-slate-500">
                {{ selectedStateIds.length === 0 && countyFilter.trim() === '' ? $t('super.manageUsers.countyStartHint') : $t('super.manageUsers.countyNoMatches') }}
              </div>
              <label v-for="(c, idx) in displayedCounties" :key="c.id" class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50">
                <input type="checkbox" :checked="selectedCountyIds.includes(c.id)" @click="onCountyClick($event, idx, c.id)" class="h-3.5 w-3.5" />
                <span>{{ c.name }}</span>
              </label>
            </div>
          </div>
        </div>
      </label>

      <!-- Zipcode picker (mode A: typeahead, mode B: dropdown) -->
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        <span :title="$t('super.manageUsers.zipcodeHelp')" class="cursor-help">{{ $t('super.manageUsers.zipcode') }}</span>
        <template v-if="selectedStateIds.length === 0">
          <input
            v-model="zipFilter"
            @input="scheduleFetch"
            type="text"
            inputmode="numeric"
            maxlength="5"
            autocomplete="off"
            list="user-zip-typeahead"
            :placeholder="$t('super.manageUsers.zipcodeTypeahead')"
            class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
          />
          <datalist id="user-zip-typeahead">
            <option v-for="z in displayedZipcodes" :key="z.id" :value="z.zipcode" />
          </datalist>
        </template>
        <template v-else>
          <div v-if="displayedZipcodes.length === 0" class="rounded border border-slate-300 bg-slate-50 p-2 text-xs font-normal text-slate-500">
            {{ $t('super.manageUsers.zipcodeNone') }}
          </div>
          <div v-else data-zip-picker class="relative">
            <button
              type="button"
              @click.stop="zipPickerOpen = !zipPickerOpen"
              :aria-expanded="zipPickerOpen"
              class="flex w-full items-center justify-between rounded border border-slate-300 bg-white p-2 text-left text-sm font-normal text-slate-900 hover:bg-slate-50"
            >
              <span class="font-mono">{{ zipPickerSummary }}</span>
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="ml-2 h-4 w-4 text-slate-500 transition-transform" :class="{ 'rotate-180': zipPickerOpen }" aria-hidden="true"><path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" /></svg>
            </button>
            <div
              v-if="zipPickerOpen"
              tabindex="0"
              @keydown="onZipKeydown"
              class="absolute left-0 right-0 z-20 mt-1 max-h-32 overflow-y-auto rounded border border-slate-300 bg-white p-1 text-sm font-normal text-slate-900 shadow-lg focus:outline-none focus:ring-1 focus:ring-slate-400"
            >
              <label v-for="(z, idx) in displayedZipcodes" :key="z.id" class="flex items-center gap-2 rounded px-2 py-0.5 text-xs font-normal hover:bg-slate-50">
                <input type="checkbox" :checked="selectedZipcodes.includes(z.zipcode)" @click="onZipClick($event, idx, z.zipcode)" class="h-3.5 w-3.5" />
                <span class="font-mono">{{ z.zipcode }}</span>
              </label>
            </div>
          </div>
        </template>
      </label>

      <!-- Messages: free-text search of every user's message history. -->
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        <span :title="$t('super.manageUsers.messageHelp')" class="cursor-help">{{ $t('super.manageUsers.messageFilter') }}</span>
        <input
          v-model="messageFilter"
          type="text"
          autocomplete="off"
          :placeholder="$t('super.manageUsers.messagePlaceholder')"
          class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
        />
      </label>
    </form>

    <p v-if="error" class="mb-2 text-sm text-red-700">{{ error }}</p>
    <p v-if="message" class="mb-2 text-sm text-green-700">{{ message }}</p>
    <p v-if="loading" class="mb-2 text-xs text-slate-500">{{ $t('common.loading') }}</p>

    <p v-if="!loading && results.length === 0" class="text-sm text-slate-500">{{ $t('super.manageUsers.none') }}</p>

    <table v-if="results.length > 0" class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th
            @click="toggleSort('email')"
            class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100"
          >{{ $t('super.manageUsers.colEmail') }}{{ sortIndicator('email') }}</th>
          <th
            @click="toggleSort('access')"
            class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100"
          >{{ $t('super.manageUsers.colRole') }}{{ sortIndicator('access') }}</th>
          <th
            @click="toggleSort('stateInitial')"
            class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100"
          >{{ $t('super.manageUsers.colState') }}{{ sortIndicator('stateInitial') }}</th>
          <th
            @click="toggleSort('countyName')"
            class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100"
          >{{ $t('super.manageUsers.colCounty') }}{{ sortIndicator('countyName') }}</th>
          <th
            @click="toggleSort('zipcode')"
            class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100"
          >{{ $t('super.manageUsers.colZipcode') }}{{ sortIndicator('zipcode') }}</th>
          <th
            tabindex="0"
            @click="toggleSort('isEnabled')"
            @keydown="onEnableHeaderKeydown"
            :title="$t('super.manageUsers.enableHelp')"
            class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100 focus:outline-none focus:ring-1 focus:ring-slate-400"
          >{{ $t('super.manageUsers.colEnable') }}{{ sortIndicator('isEnabled') }}</th>
          <th
            @click="toggleSort('msg')"
            class="cursor-pointer select-none border-b border-slate-200 p-2 font-semibold text-slate-700 hover:bg-slate-100"
          >{{ $t('super.manageUsers.colMsg') }}{{ sortIndicator('msg') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, idx) in sortedResults" :key="row.id">
          <td class="border-b border-slate-100 p-2">{{ row.email }}</td>
          <td class="border-b border-slate-100 p-2">
            <span :class="['rounded px-2 py-0.5 text-xs font-semibold', accessBadgeClasses(row.access)]">{{ row.access }}</span>
            <button
              v-if="row.access === 'ADMIN'"
              type="button"
              @click="demote(row)"
              class="ml-2 rounded border border-red-700 bg-white px-2 py-0.5 text-xs text-red-700 hover:bg-red-50"
            >{{ $t('super.manageUsers.demote') }}</button>
          </td>
          <td class="border-b border-slate-100 p-2">{{ row.stateInitial ?? '—' }}</td>
          <td class="border-b border-slate-100 p-2">{{ row.countyName ?? '—' }}</td>
          <td class="border-b border-slate-100 p-2 font-mono">{{ row.zipcode }}</td>
          <td class="border-b border-slate-100 p-2">
            <input
              type="checkbox"
              :checked="row.isEnabled"
              @click="onEnableClick($event, idx, row)"
              class="h-4 w-4"
              :aria-label="row.isEnabled ? $t('super.manageUsers.disableAria', { email: row.email }) : $t('super.manageUsers.enableAria', { email: row.email })"
            />
          </td>
          <td class="border-b border-slate-100 p-2 text-xs">
            <template v-if="row.latestMessage">
              <button type="button" @click="openMessageModal(row, 'edit')" class="text-slate-800 underline">
                {{ previewText(row.latestMessage.body) }}
              </button>
            </template>
            <template v-else>
              <span class="text-slate-500">{{ $t('super.manageUsers.msgNone') }}</span>
              <button type="button" @click="openMessageModal(row, 'new')" class="ml-2 text-slate-800 underline">
                {{ $t('super.manageUsers.msgNew') }}
              </button>
            </template>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-if="results.length > 0" class="mt-2 text-xs text-slate-500">{{ $t('super.manageUsers.enableHelp') }}</p>

    <!-- Message modal -->
    <div
      v-if="modalOpen"
      role="dialog"
      aria-modal="true"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      @click.self="closeMessageModal"
    >
      <div class="w-full max-w-lg rounded-md bg-white p-4 shadow-lg">
        <header class="mb-2 flex items-center justify-between">
          <strong class="text-sm text-slate-800">
            {{ modalMode === 'new'
              ? $t('super.manageUsers.modalNewTitle', { email: modalUser?.email ?? '' })
              : $t('super.manageUsers.modalEditTitle', { email: modalUser?.email ?? '' }) }}
          </strong>
          <button type="button" @click="closeMessageModal" :aria-label="$t('common.close')" class="rounded p-1 text-slate-500 hover:bg-slate-100">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" class="h-4 w-4" aria-hidden="true"><path fill="currentColor" d="M5.7 4.3 4.3 5.7 8.6 10l-4.3 4.3 1.4 1.4L10 11.4l4.3 4.3 1.4-1.4L11.4 10l4.3-4.3-1.4-1.4L10 8.6 5.7 4.3z" /></svg>
          </button>
        </header>
        <p v-if="modalMode === 'edit' && modalMessages.length > 0" class="mb-1 text-xs text-slate-500">
          {{ $t('super.manageUsers.modalIndex', {
              i: modalMessages.length - modalIdx,
              n: modalMessages.length,
              when: new Date(modalMessages[modalIdx].createdAt).toLocaleString()
          }) }}
        </p>
        <textarea
          ref="textareaRef"
          v-model="modalBody"
          maxlength="2000"
          rows="8"
          class="mb-1 w-full rounded border border-slate-300 p-2 text-sm text-slate-900 focus:border-slate-500 focus:outline-none"
        />
        <p class="mb-2 text-right text-xs text-slate-500">{{ modalBody.length }}/2000</p>
        <p v-if="modalError" class="mb-2 text-xs text-red-700">{{ modalError }}</p>
        <label v-if="modalMode === 'new'" class="mb-3 flex items-center gap-2 text-xs text-slate-700">
          <input v-model="modalSendEmail" type="checkbox" class="h-4 w-4" />
          {{ $t('super.manageUsers.sendToUser') }}
        </label>
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex gap-2 text-xs">
            <button v-if="modalMode === 'edit'" type="button" :disabled="modalIdx >= modalMessages.length - 1" @click="modalPrev" class="rounded border border-slate-300 px-2 py-1 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50">
              {{ $t('super.manageUsers.modalPrev') }}
            </button>
            <button v-if="modalMode === 'edit'" type="button" :disabled="modalIdx <= 0" @click="modalNext" class="rounded border border-slate-300 px-2 py-1 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50">
              {{ $t('super.manageUsers.modalNext') }}
            </button>
            <button v-if="modalMode === 'edit'" type="button" @click="modalStartNew" class="rounded border border-slate-300 px-2 py-1 hover:bg-slate-50">
              {{ $t('super.manageUsers.msgNew') }}
            </button>
            <button v-else-if="modalMessages.length > 0" type="button" @click="modalBackToEdit" class="rounded border border-slate-300 px-2 py-1 hover:bg-slate-50">
              {{ $t('super.manageUsers.modalBackToEdit') }}
            </button>
          </div>
          <button
            type="button"
            @click="saveModal"
            :disabled="modalSaving"
            class="rounded bg-slate-800 px-4 py-1.5 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
          >{{ modalSaving ? $t('common.saving') : $t('common.save') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
