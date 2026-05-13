import { computed, type ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * Reads a localized label out of a `labels` block embedded in a poll-type
 * template JSON. Falls back to English, then to the supplied default string.
 *
 * Example template fragment:
 *   "candidates": {
 *     "labels": { "en": "Candidates", "fr": "Candidats", ... },
 *     ...
 *   }
 *
 * Usage:
 *   const candidatesHeading = useTemplateLabel(candidatesBlock?.labels, 'Candidates')
 */
export function useTemplateLabel(
  labels: Record<string, string> | undefined | null,
  fallback: string
): ComputedRef<string> {
  const { locale } = useI18n()
  return computed(() => {
    if (!labels) return fallback
    return labels[locale.value] ?? labels.en ?? fallback
  })
}
