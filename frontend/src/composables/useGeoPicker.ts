// Headless logic for the State / County / Zipcode cascade picker shared
// between ZipSetter (form-style, inline <details> shells) and
// PollSearchView (filter-bar style, popup shells). Both presentations bind
// the same reactive state, fetches, and select-all + keystroke helpers
// exposed here.
//
// Three picker sections (state, county, zip) and each one has the same
// shape: a list of items, a current selection, a filter string, a
// displayed (filtered) view, all-selected / some-selected computeds for
// driving Select-all checkboxes (including the native `indeterminate`
// property via a watchEffect on the consumer's template ref), a
// toggle-all action that respects the filter window, and a section-level
// keydown handler covering Enter/Escape (clear filter), Ctrl/Cmd-A
// (select visible), Ctrl/Cmd-Shift-A (deselect visible), plus the legacy
// Shift-* / Shift-0 bindings for parity with the original PollSearchView
// shortcuts.

import { computed, ref, watch, watchEffect, type Ref, type ComputedRef } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import type { State, County, CountyZip } from '@/types'

export interface UseGeoPickerOptions {
  /**
   * When `true`, prefix-search the counties and zipcodes endpoints if no
   * state is currently selected. Used by PollSearchView's filter bar so
   * "type a partial zipcode to find a poll" still works without a state
   * context. ZipSetter leaves this off — its forms always require a state
   * to be picked first.
   */
  enablePrefixSearch?: boolean
  /**
   * Debounce window for the prefix-search fetches. Only consulted when
   * `enablePrefixSearch` is `true`.
   */
  prefixSearchDelayMs?: number
}

export interface PickerSection<TItem, TValue> {
  items: Ref<TItem[]>
  displayed: ComputedRef<TItem[]>
  selected: Ref<TValue[]>
  filter: Ref<string>
  loading: Ref<boolean>
  allSelected: ComputedRef<boolean>
  someSelected: ComputedRef<boolean>
  selectAllRef: Ref<HTMLInputElement | null>
  toggleAll: () => void
  onFilterKeydown: (e: KeyboardEvent) => void
}

export interface UseGeoPickerReturn {
  states: PickerSection<State, number>
  counties: PickerSection<County, number>
  zips: PickerSection<CountyZip, string>
  error: Ref<string | null>
  reload: {
    states: () => Promise<void>
    counties: (stateIds: number[]) => Promise<void>
    zips: (countyIds: number[]) => Promise<void>
  }
}

export function useGeoPicker(opts: UseGeoPickerOptions = {}): UseGeoPickerReturn {
  const { t } = useI18n()
  const enablePrefixSearch = opts.enablePrefixSearch ?? false
  const prefixSearchDelayMs = opts.prefixSearchDelayMs ?? 200

  // ── data ────────────────────────────────────────────────────────────
  const stateItems = ref<State[]>([])
  const countyItems = ref<County[]>([])
  const zipItems = ref<CountyZip[]>([])

  const selectedStateIds = ref<number[]>([])
  const selectedCountyIds = ref<number[]>([])
  const selectedZipcodes = ref<string[]>([])

  const stateFilter = ref('')
  const countyFilter = ref('')
  const zipFilter = ref('')

  const loadingStates = ref(false)
  const loadingCounties = ref(false)
  const loadingZips = ref(false)
  const error = ref<string | null>(null)

  // ── fetches ─────────────────────────────────────────────────────────
  async function loadStates() {
    loadingStates.value = true
    error.value = null
    try {
      const res = await axios.get<State[]>('/api/states')
      stateItems.value = res.data
    } catch {
      error.value = t('zipSetter.loadStatesFailed')
    } finally {
      loadingStates.value = false
    }
  }

  async function loadCounties(stateIds: number[]) {
    loadingCounties.value = true
    error.value = null
    countyItems.value = []
    zipItems.value = []
    selectedCountyIds.value = []
    countyFilter.value = ''
    zipFilter.value = ''
    if (stateIds.length === 0) {
      loadingCounties.value = false
      return
    }
    try {
      // GeographyController binds state_id as List<Long>, so a
      // comma-separated value covers multi-state in one round-trip.
      const res = await axios.get<County[]>('/api/counties', {
        params: { state_id: stateIds.join(',') }
      })
      countyItems.value = res.data
    } catch {
      error.value = t('zipSetter.loadCountiesFailed')
    } finally {
      loadingCounties.value = false
    }
  }

  async function loadZips(countyIds: number[]) {
    if (countyIds.length === 0) {
      zipItems.value = []
      zipFilter.value = ''
      return
    }
    loadingZips.value = true
    error.value = null
    zipFilter.value = ''
    try {
      const res = await axios.get<CountyZip[]>('/api/zipcodes', {
        params: { county_ids: countyIds.join(',') }
      })
      zipItems.value = res.data
      selectedZipcodes.value = selectedZipcodes.value.filter(z =>
        res.data.some(cz => cz.zipcode === z)
      )
    } catch {
      error.value = t('zipSetter.loadZipcodesFailed')
    } finally {
      loadingZips.value = false
    }
  }

  // ── prefix-search fallbacks (filter-bar style) ─────────────────────
  // Only attached when the caller opts in. Counties/zips can then be
  // discovered without a state selection — useful for filter bars where
  // the user types a partial zip and expects results to surface.
  let countyPrefixTimer: ReturnType<typeof setTimeout> | null = null
  let zipPrefixTimer: ReturnType<typeof setTimeout> | null = null

  async function loadCountiesByPrefix(prefix: string) {
    try {
      const res = await axios.get<County[]>('/api/counties', { params: { prefix } })
      countyItems.value = res.data
    } catch {
      countyItems.value = []
    }
  }

  async function loadZipsByPrefix(prefix: string) {
    try {
      const res = await axios.get<CountyZip[]>('/api/zipcodes', { params: { prefix } })
      zipItems.value = res.data
    } catch {
      zipItems.value = []
    }
  }

  if (enablePrefixSearch) {
    watch(countyFilter, (next) => {
      if (selectedStateIds.value.length > 0) return // state context: filter locally
      if (countyPrefixTimer) clearTimeout(countyPrefixTimer)
      const trimmed = next.trim()
      if (trimmed === '') { countyItems.value = []; return }
      countyPrefixTimer = setTimeout(() => loadCountiesByPrefix(trimmed), prefixSearchDelayMs)
    })
    watch(zipFilter, (next) => {
      if (selectedStateIds.value.length > 0) return
      if (zipPrefixTimer) clearTimeout(zipPrefixTimer)
      const trimmed = next.trim()
      if (trimmed === '') { zipItems.value = []; return }
      zipPrefixTimer = setTimeout(() => loadZipsByPrefix(trimmed), prefixSearchDelayMs)
    })
  }

  // ── displayed (filtered) views ──────────────────────────────────────
  const displayedStates = computed(() => {
    const prefix = stateFilter.value.trim().toLowerCase()
    if (prefix === '') return stateItems.value
    return stateItems.value.filter(s =>
      s.name.toLowerCase().startsWith(prefix) ||
      s.initial.toLowerCase().startsWith(prefix)
    )
  })

  const displayedCounties = computed(() => {
    // With a state context (or prefix-search disabled) we filter locally.
    // With prefix-search and no state context, items already came back
    // narrowed from the server — render as-is.
    if (enablePrefixSearch && selectedStateIds.value.length === 0) {
      return countyItems.value
    }
    const prefix = countyFilter.value.trim().toLowerCase()
    if (prefix === '') return countyItems.value
    return countyItems.value.filter(c => c.name.toLowerCase().startsWith(prefix))
  })

  const displayedZips = computed(() => {
    const prefix = zipFilter.value.trim()
    if (prefix === '') return zipItems.value
    return zipItems.value.filter(z => z.zipcode.startsWith(prefix))
  })

  // ── select-all wiring (per section) ─────────────────────────────────
  function buildSection<TItem, TValue>(
    items: Ref<TItem[]>,
    displayed: ComputedRef<TItem[]>,
    selected: Ref<TValue[]>,
    filter: Ref<string>,
    loading: Ref<boolean>,
    keyOf: (item: TItem) => TValue,
  ): PickerSection<TItem, TValue> {
    const selectAllRef = ref<HTMLInputElement | null>(null)
    const allSelected = computed(() =>
      displayed.value.length > 0 &&
      displayed.value.every(i => selected.value.includes(keyOf(i)))
    )
    const someSelected = computed(() => {
      const visible = displayed.value
      if (visible.length === 0) return false
      const n = visible.filter(i => selected.value.includes(keyOf(i))).length
      return n > 0 && n < visible.length
    })
    watchEffect(() => {
      if (selectAllRef.value) {
        selectAllRef.value.indeterminate = someSelected.value
      }
    })

    function toggleAll() {
      const visibleKeys = displayed.value.map(keyOf)
      if (allSelected.value) {
        const drop = new Set<TValue>(visibleKeys)
        selected.value = selected.value.filter(v => !drop.has(v))
      } else {
        selected.value = Array.from(new Set([...selected.value, ...visibleKeys]))
      }
    }

    function onFilterKeydown(e: KeyboardEvent) {
      if (e.key === 'Enter' || e.key === 'Escape') {
        e.preventDefault()
        e.stopPropagation()
        filter.value = ''
        return
      }
      const ctrlOrCmd = e.ctrlKey || e.metaKey
      const isA = e.key.toLowerCase() === 'a'
      const isSelectAll = e.key === '*'
        || (e.shiftKey && e.code === 'Digit8')
        || (ctrlOrCmd && !e.shiftKey && isA)
      const isDeselectAll = e.key === ')'
        || (e.shiftKey && e.code === 'Digit0')
        || (ctrlOrCmd && e.shiftKey && isA)
      if (isSelectAll) {
        e.preventDefault()
        const visibleKeys = displayed.value.map(keyOf)
        selected.value = Array.from(new Set([...selected.value, ...visibleKeys]))
      } else if (isDeselectAll) {
        e.preventDefault()
        const drop = new Set<TValue>(displayed.value.map(keyOf))
        selected.value = selected.value.filter(v => !drop.has(v))
      }
    }

    return {
      items,
      displayed,
      selected,
      filter,
      loading,
      allSelected,
      someSelected,
      selectAllRef,
      toggleAll,
      onFilterKeydown,
    }
  }

  const states = buildSection(stateItems, displayedStates, selectedStateIds, stateFilter, loadingStates, (s) => s.id)
  const counties = buildSection(countyItems, displayedCounties, selectedCountyIds, countyFilter, loadingCounties, (c) => c.id)
  const zips = buildSection(zipItems, displayedZips, selectedZipcodes, zipFilter, loadingZips, (z) => z.zipcode)

  // ── cascade: state → counties → zips ────────────────────────────────
  watch(selectedStateIds, (ids) => {
    loadCounties(ids)
  }, { deep: true })

  watch(selectedCountyIds, (ids) => {
    loadZips(ids)
  }, { deep: true })

  // Kick off the states fetch the moment this composable wires up.
  loadStates()

  return {
    states,
    counties,
    zips,
    error,
    reload: {
      states: loadStates,
      counties: loadCounties,
      zips: loadZips,
    },
  }
}
