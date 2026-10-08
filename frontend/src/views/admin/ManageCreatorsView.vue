<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import InfoPopover from '@/components/InfoPopover.vue'
import PurviewSetter from '@/components/PurviewSetter.vue'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel, ScopeLevel, type PollType, type Purview } from '@/types'

const { t } = useI18n()
const auth = useAuthStore()

type EnabledState = 'ENABLED' | 'DISABLED' | 'PARTIAL'
interface Grant {
  id: number
  scopeLevel: ScopeLevel
  stateId: number | null
  stateName: string | null
  stateInitial: string | null
  countyId: number | null
  countyName: string | null
  zipcode: string | null
  pollTypeId: number | null
  pollTypeName: string | null
  enabled: boolean
  manageable: boolean
  fromRequest: boolean
}
interface CreatorRow {
  userId: number
  email: string
  /** The Enabled column: false while YOU have disabled this creator. */
  enabled: boolean
  /** You can flip Enabled: they have access in your purview (or you disabled them). */
  canToggle: boolean
  /** This row is you: your own creator grants are switchable, but not the row's Enabled. */
  isYou: boolean
  /** Their ENABLED polls inside your purview. */
  pollCount: number
  /** All their polls inside your purview, disabled included: what the Polls link opens. */
  pollTotal: number
  /** Whether their access inside your purview is on; edited per entry in the Edit dialog. */
  accessState: EnabledState
  manageable: boolean
  grants: Grant[]
  lastEditedAt: string | null
}

const rows = ref<CreatorRow[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const emailFilter = ref('')
const busyIds = ref(new Set<number>())

async function load() {
  loading.value = true
  error.value = null
  try {
    rows.value = (await axios.get<CreatorRow[]>('/api/admin/creators')).data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('admin.manageCreators.errorLoad')
  } finally {
    loading.value = false
  }
}
function replaceRow(updated: CreatorRow) {
  rows.value = rows.value.map(r => (r.userId === updated.userId ? updated : r))
  if (editRow.value?.userId === updated.userId) editRow.value = updated
}

const visibleRows = computed(() => {
  const f = emailFilter.value.trim().toLowerCase()
  return f ? rows.value.filter(r => r.email.toLowerCase().includes(f)) : rows.value
})

// ---------- purview display ----------
function regionLabel(g: Grant): string {
  switch (g.scopeLevel) {
    case ScopeLevel.NATIONAL: return t('purview.national')
    case ScopeLevel.STATE: return g.stateName ?? '—'
    case ScopeLevel.COUNTY: return `${g.countyName} (${g.stateInitial})`
    case ScopeLevel.ZIP: return g.zipcode ?? '—'
  }
}
function typeLabel(g: Grant): string { return g.pollTypeName ?? t('admin.manageCreators.allTypes') }
/** One-line summary, e.g. "CA · 3 counties · 2 zips". */
function purviewSummary(r: CreatorRow): string {
  const gs = r.grants
  if (gs.some(g => g.scopeLevel === ScopeLevel.NATIONAL)) return t('purview.national')
  const states = new Set(gs.filter(g => g.scopeLevel === ScopeLevel.STATE).map(g => g.stateInitial))
  const counties = new Set(gs.filter(g => g.scopeLevel === ScopeLevel.COUNTY).map(g => g.countyId))
  const zips = new Set(gs.filter(g => g.scopeLevel === ScopeLevel.ZIP).map(g => g.zipcode))
  const parts: string[] = []
  if (states.size) parts.push([...states].sort().join(', '))
  if (counties.size) parts.push(t('admin.manageCreators.nCounties', { n: counties.size }, counties.size))
  if (zips.size) parts.push(t('admin.manageCreators.nZips', { n: zips.size }, zips.size))
  return parts.join(' · ') || '—'
}
/** Grants grouped by level for the popover: [[heading, ["Los Angeles (CA) — All types", …]], …]. */
function purviewGroups(r: CreatorRow): [string, { text: string; enabled: boolean }[]][] {
  const order: [ScopeLevel, string][] = [
    [ScopeLevel.NATIONAL, t('admin.manageCreators.groupNational')],
    [ScopeLevel.STATE, t('admin.manageCreators.groupStates')],
    [ScopeLevel.COUNTY, t('admin.manageCreators.groupCounties')],
    [ScopeLevel.ZIP, t('admin.manageCreators.groupZips')],
  ]
  return order
    .map(([lvl, heading]) => [
      heading,
      r.grants.filter(g => g.scopeLevel === lvl).map(g => ({ text: `${regionLabel(g)} — ${typeLabel(g)}`, enabled: g.enabled })),
    ] as [string, { text: string; enabled: boolean }[]])
    .filter(([, items]) => items.length > 0)
}

// ---------- enabled toggle (stored per admin) ----------
async function toggleEnabled(r: CreatorRow) {
  if (!r.canToggle || busyIds.value.has(r.userId)) return
  // Unchecking stops them creating polls in your purview and disables their
  // polls here; checking lifts both. Single polls are re-enabled on Manage Polls.
  const enabled = !r.enabled
  busyIds.value = new Set(busyIds.value).add(r.userId)
  error.value = null
  try {
    replaceRow((await axios.put<CreatorRow>(`/api/admin/creators/${r.userId}/enabled`, { enabled })).data)
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('admin.manageCreators.errorSave')
  } finally {
    const next = new Set(busyIds.value); next.delete(r.userId); busyIds.value = next
  }
}

// ---------- polls link ----------
function pollsLink(r: CreatorRow) {
  return { path: '/admin/manage-polls', query: { creator: r.email, sort: 'closeDate', dir: 'desc', showDisabled: '1' } }
}
function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleDateString() : '—'
}

// ---------- edit modal ----------
const editRow = ref<CreatorRow | null>(null)
const editError = ref<string | null>(null)
const editBusy = ref(false)
const pollTypes = ref<PollType[]>([])
const addPurview = ref<Purview>({ scopeLevel: ScopeLevel.STATE, regionIds: [], zipcodes: [] })
const addTypeIds = ref<number[]>([])
const setterKey = ref(0) // remount PurviewSetter to clear it after an add
const isSuper = computed(() => auth.hasAccess(AccessLevel.SUPER))
const addLevels = computed(() =>
  isSuper.value ? undefined : [ScopeLevel.STATE, ScopeLevel.COUNTY, ScopeLevel.ZIP],
)
const canAdd = computed(() => {
  const p = addPurview.value
  return p.scopeLevel === ScopeLevel.NATIONAL
    || (p.scopeLevel === ScopeLevel.ZIP ? p.zipcodes.length > 0 : p.regionIds.length > 0)
})

async function openEdit(r: CreatorRow) {
  editRow.value = r
  editError.value = null
  addTypeIds.value = []
  setterKey.value++
  if (pollTypes.value.length === 0) {
    try { pollTypes.value = (await axios.get<PollType[]>('/api/poll-types')).data } catch { /* types optional */ }
  }
}
function closeEdit() { editRow.value = null }

async function editCall(fn: () => Promise<{ data: CreatorRow }>) {
  editBusy.value = true
  editError.value = null
  try {
    replaceRow((await fn()).data)
    return true
  } catch (e: any) {
    editError.value = e?.response?.data?.message ?? t('admin.manageCreators.errorSave')
    return false
  } finally {
    editBusy.value = false
  }
}
function setGrantEnabled(g: Grant, enabled: boolean) {
  const r = editRow.value!
  return editCall(() => axios.put<CreatorRow>(`/api/admin/creators/${r.userId}/grants/${g.id}`, { enabled }))
}
function removeGrant(g: Grant) {
  const r = editRow.value!
  return editCall(() => axios.delete<CreatorRow>(`/api/admin/creators/${r.userId}/grants/${g.id}`))
}
async function addGrants() {
  const r = editRow.value!
  const ok = await editCall(() =>
    axios.post<CreatorRow>(`/api/admin/creators/${r.userId}/grants`, { ...addPurview.value, pollTypeIds: addTypeIds.value }),
  )
  if (ok) { addTypeIds.value = []; setterKey.value++ }
}

function onEsc(e: KeyboardEvent) { if (e.key === 'Escape' && editRow.value) closeEdit() }
onMounted(() => {
  document.addEventListener('keydown', onEsc)
  load()
})
</script>

<template>
  <div class="py-8">
    <h1 class="mb-1 text-2xl font-semibold text-slate-800">{{ $t('admin.manageCreators.heading') }}</h1>
    <p class="mb-4 text-sm text-slate-600">{{ $t('admin.manageCreators.body') }}</p>

    <div class="mb-3 flex flex-wrap items-center gap-3">
      <input
        v-model="emailFilter"
        type="search"
        :placeholder="$t('admin.manageCreators.filterEmail')"
        class="w-full max-w-xs rounded border border-slate-300 px-2 py-1 text-sm"
      />
      <span class="text-xs text-slate-500">{{ $t('admin.manageCreators.count', { n: visibleRows.length }, visibleRows.length) }}</span>
    </div>

    <p v-if="error" class="mb-3 rounded bg-red-50 p-2 text-sm text-red-700">{{ error }}</p>
    <p v-if="loading" class="text-sm text-slate-500">{{ $t('common.loading') }}</p>
    <p v-else-if="visibleRows.length === 0" class="text-sm text-slate-500">{{ $t('admin.manageCreators.none') }}</p>

    <div v-else class="overflow-x-auto">
      <table class="w-full border-collapse text-sm">
        <thead>
          <tr class="bg-slate-50 text-left">
            <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.manageCreators.colEmail') }}</th>
            <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">
              {{ $t('admin.manageCreators.colPurview') }}
              <InfoPopover :label="$t('admin.manageCreators.colPurview')">{{ $t('help.adminManageCreators.purview') }}</InfoPopover>
            </th>
            <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">
              {{ $t('admin.manageCreators.colPolls') }}
              <InfoPopover :label="$t('admin.manageCreators.colPolls')">{{ $t('help.adminManageCreators.polls') }}</InfoPopover>
            </th>
            <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">
              {{ $t('admin.manageCreators.colLastEdit') }}
              <InfoPopover :label="$t('admin.manageCreators.colLastEdit')">{{ $t('help.adminManageCreators.lastEdit') }}</InfoPopover>
            </th>
            <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">
              {{ $t('admin.manageCreators.colEnabled') }}
              <InfoPopover :label="$t('admin.manageCreators.colEnabled')" align="right">{{ $t('help.adminManageCreators.enabled') }}</InfoPopover>
            </th>
            <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.manageCreators.colEdit') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in visibleRows" :key="r.userId" data-test="creator-row">
            <td class="border-b border-slate-100 p-2">
              {{ r.email }}
              <span v-if="r.isYou" class="ml-1 text-xs text-slate-500">{{ $t('admin.manageCreators.you') }}</span>
            </td>
            <td class="border-b border-slate-100 p-2">
              <InfoPopover :label="$t('admin.manageCreators.purviewOf', { email: r.email })" icon="pin" width-class="w-80">
                <template #trigger>
                  <span class="inline-flex items-center gap-1 rounded bg-slate-100 px-2 py-0.5 text-slate-800">
                    <svg viewBox="0 0 20 20" fill="currentColor" class="h-3.5 w-3.5 text-slate-500" aria-hidden="true">
                      <path fill-rule="evenodd" d="M9.69 18.933l.003.001C9.89 19.02 10 19 10 19s.11.02.308-.066l.002-.001.006-.003.018-.008a5.741 5.741 0 0 0 .281-.14c.186-.096.446-.24.757-.433.62-.384 1.445-.966 2.274-1.765C15.302 14.988 17 12.493 17 9A7 7 0 1 0 3 9c0 3.492 1.698 5.988 3.355 7.584a13.731 13.731 0 0 0 2.273 1.765 11.842 11.842 0 0 0 .976.544l.062.029.018.008.006.003ZM10 11.25a2.25 2.25 0 1 0 0-4.5 2.25 2.25 0 0 0 0 4.5Z" clip-rule="evenodd" />
                    </svg>
                    {{ purviewSummary(r) }}
                  </span>
                </template>
                <div v-for="[heading, items] in purviewGroups(r)" :key="heading" class="mb-2 last:mb-0">
                  <div class="mb-0.5 font-semibold text-slate-800">{{ heading }}</div>
                  <ul class="max-h-40 overflow-y-auto">
                    <li v-for="it in items" :key="it.text" :class="it.enabled ? '' : 'text-slate-400 line-through'">{{ it.text }}</li>
                  </ul>
                </div>
              </InfoPopover>
            </td>
            <td class="border-b border-slate-100 p-2">
              <router-link v-if="r.pollTotal > 0" :to="pollsLink(r)" class="text-blue-700 underline">{{ r.pollCount }}</router-link>
              <span v-else class="text-slate-400">0</span>
            </td>
            <td class="border-b border-slate-100 p-2">{{ formatDate(r.lastEditedAt) }}</td>
            <td class="border-b border-slate-100 p-2">
              <label
                class="inline-flex items-center gap-2"
                :title="r.canToggle ? '' : r.isYou ? $t('admin.manageCreators.youHint') : $t('admin.manageCreators.noAccessHint')"
              >
                <input
                  type="checkbox"
                  class="h-4 w-4"
                  :checked="r.enabled"
                  :disabled="!r.canToggle || busyIds.has(r.userId)"
                  :aria-label="$t('admin.manageCreators.enabledFor', { email: r.email })"
                  @click.prevent="toggleEnabled(r)"
                />
                <span :class="r.canToggle ? 'text-slate-700' : 'text-slate-400'">{{ r.enabled ? $t('common.yes') : $t('common.no') }}</span>
              </label>
            </td>
            <td class="border-b border-slate-100 p-2">
              <button type="button" class="rounded border border-slate-300 px-2 py-0.5 text-xs hover:bg-slate-50" @click="openEdit(r)">
                {{ $t('admin.manageCreators.edit') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Edit modal -->
    <div v-if="editRow" role="dialog" aria-modal="true" class="fixed inset-0 z-40 flex items-start justify-center overflow-y-auto bg-black/40 p-4" @click.self="closeEdit">
      <div class="mt-10 w-full max-w-2xl rounded bg-white p-5 shadow-xl">
        <h2 class="mb-1 text-lg font-semibold text-slate-800">{{ $t('admin.manageCreators.editHeading', { email: editRow.email }) }}</h2>
        <p class="mb-4 text-xs text-slate-500">{{ $t('admin.manageCreators.editNote') }}</p>

        <h3 class="mb-2 text-sm font-semibold text-slate-700">{{ $t('admin.manageCreators.currentGrants') }}</h3>
        <p v-if="editRow.grants.length === 0" class="mb-4 text-sm text-slate-500">{{ $t('admin.manageCreators.noGrants') }}</p>
        <table v-else class="mb-5 w-full text-sm">
          <tbody>
            <tr v-for="g in editRow.grants" :key="g.id" :class="g.manageable ? '' : 'text-slate-400'">
              <td class="py-1 pr-2">{{ regionLabel(g) }}</td>
              <td class="py-1 pr-2 text-slate-500">{{ typeLabel(g) }}</td>
              <td class="py-1 pr-2">
                <label class="inline-flex items-center gap-1">
                  <input
                    type="checkbox" class="h-4 w-4"
                    :checked="g.enabled"
                    :disabled="!g.manageable || editBusy"
                    @click.prevent="setGrantEnabled(g, !g.enabled)"
                  />
                  {{ g.enabled ? $t('admin.manageCreators.enabled') : $t('admin.manageCreators.disabled') }}
                </label>
              </td>
              <td class="py-1 text-right">
                <span v-if="!g.manageable" class="text-xs">{{ $t('admin.manageCreators.outsidePurview') }}</span>
                <button
                  v-else-if="!g.fromRequest"
                  type="button" :disabled="editBusy"
                  class="text-xs text-red-700 underline disabled:opacity-50"
                  @click="removeGrant(g)"
                >{{ $t('admin.manageCreators.remove') }}</button>
                <InfoPopover v-else :label="$t('admin.manageCreators.fromRequest')" align="right">
                  {{ $t('admin.manageCreators.fromRequestInfo') }}
                </InfoPopover>
              </td>
            </tr>
          </tbody>
        </table>

        <h3 class="mb-2 text-sm font-semibold text-slate-700">{{ $t('admin.manageCreators.addGrants') }}</h3>
        <PurviewSetter :key="setterKey" :allowed-levels="addLevels" @update:model-value="addPurview = $event" />
        <fieldset class="mt-3">
          <legend class="mb-1 text-xs font-semibold text-slate-600">
            {{ $t('admin.manageCreators.pollTypes') }}
            <span class="font-normal text-slate-500">{{ $t('admin.manageCreators.pollTypesHint') }}</span>
          </legend>
          <label v-for="pt in pollTypes" :key="pt.id" class="mr-4 inline-flex items-center gap-1 text-sm">
            <input v-model="addTypeIds" type="checkbox" :value="pt.id" class="h-4 w-4" /> {{ pt.name }}
          </label>
        </fieldset>

        <p v-if="editError" class="mt-3 text-sm text-red-700">{{ editError }}</p>
        <div class="mt-4 flex justify-end gap-2">
          <button type="button" class="rounded border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50" @click="closeEdit">{{ $t('common.close') }}</button>
          <button
            type="button" :disabled="!canAdd || editBusy"
            class="rounded bg-slate-800 px-3 py-1 text-sm text-white hover:bg-slate-900 disabled:opacity-50"
            @click="addGrants"
          >{{ $t('admin.manageCreators.addButton') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
