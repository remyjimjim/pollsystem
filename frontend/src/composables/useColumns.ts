import { ref, watch, type Ref } from 'vue'

/**
 * Which of a table's columns to show, ticked in a "Show:" row (ColumnChooser).
 * Remembered on this device under [storageKey]. The first time on a narrow
 * screen, [hiddenWhenNarrow] start unticked.
 */
export function useColumns<K extends string>(
  storageKey: string,
  keys: readonly K[],
  hiddenWhenNarrow: readonly K[] = [],
): Ref<Record<K, boolean>> {
  const all = Object.fromEntries(keys.map(k => [k, true])) as Record<K, boolean>
  function initial(): Record<K, boolean> {
    try {
      const saved = JSON.parse(localStorage.getItem(storageKey) ?? 'null')
      if (saved && typeof saved === 'object') {
        // Only known keys: a column added later starts shown.
        return { ...all, ...Object.fromEntries(keys.filter(k => typeof saved[k] === 'boolean').map(k => [k, saved[k]])) }
      }
    } catch { /* storage unavailable: use the defaults */ }
    const narrow = typeof window !== 'undefined' && window.innerWidth < 640
    return narrow ? { ...all, ...Object.fromEntries(hiddenWhenNarrow.map(k => [k, false])) } : { ...all }
  }
  const columns = ref(initial()) as Ref<Record<K, boolean>>
  watch(columns, v => {
    try { localStorage.setItem(storageKey, JSON.stringify(v)) } catch { /* not remembered */ }
  }, { deep: true })
  return columns
}
