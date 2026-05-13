import { createI18n } from 'vue-i18n'
import en from './en.json'
import fr from './fr.json'
import es from './es.json'
import de from './de.json'
import it from './it.json'
import nb from './nb.json'
import ptBR from './pt-BR.json'
import zhCN from './zh-CN.json'

// Locales that real humans will see. Add a new entry here and a matching JSON
// file (same key set as en.json) to roll out a new language.
export const SUPPORTED_LOCALES = [
  { code: 'en',    name: 'English'    },
  { code: 'fr',    name: 'Français'   },
  { code: 'es',    name: 'Español'    },
  { code: 'de',    name: 'Deutsch'    },
  { code: 'it',    name: 'Italiano'   },
  { code: 'nb',    name: 'Norsk'      },
  { code: 'pt-BR', name: 'Português'  },
  { code: 'zh-CN', name: '中文'       }
] as const

export type LocaleCode = typeof SUPPORTED_LOCALES[number]['code']

const STORAGE_KEY = 'pollsystem.locale'

function resolveInitialLocale(): LocaleCode {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved && SUPPORTED_LOCALES.some(l => l.code === saved)) {
    return saved as LocaleCode
  }
  // Try the browser preference, falling back to English. Match on the prefix
  // so "en-US" and "en-GB" both resolve to "en".
  for (const pref of navigator.languages ?? [navigator.language]) {
    const exact = SUPPORTED_LOCALES.find(l => l.code === pref)
    if (exact) return exact.code
    const prefix = pref.split('-')[0]
    const partial = SUPPORTED_LOCALES.find(l => l.code === prefix)
    if (partial) return partial.code
  }
  return 'en'
}

export const i18n = createI18n({
  legacy: false,
  locale: resolveInitialLocale(),
  fallbackLocale: 'en',
  messages: { en, fr, es, de, it, nb, 'pt-BR': ptBR, 'zh-CN': zhCN }
})

/** Persists the chosen locale and switches the active one. */
export function setLocale(code: LocaleCode): void {
  localStorage.setItem(STORAGE_KEY, code)
  i18n.global.locale.value = code
}
