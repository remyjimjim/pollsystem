import { reactive, watch } from 'vue'

/**
 * The viewer's appearance settings (header gear → Settings): Dark or Light
 * theme, each with its own text and background colors, and a text size.
 * Remembered on this device. Applied to the page below the header: the slate
 * palette the whole site is built from is re-derived from the two colors
 * (see .app-content in main.css), so text, borders, panels and buttons follow.
 */
export type Theme = 'dark' | 'light'
export type TextSize = 'small' | 'medium' | 'large'

export const THEME_DEFAULTS: Record<Theme, { text: string; background: string }> = {
  dark: { text: '#85c742', background: '#4d3d85' },   // rgb(133 199 66) on #4d3d85 (was #160b2c until 2026-10-25)
  light: { text: '#000000', background: '#ffffff' },
}
// Former defaults: a saved color equal to one of these was never chosen by the
// viewer, so it follows the current default instead.
const FORMER_DEFAULTS: Record<Theme, { text: string[]; background: string[] }> = {
  dark: { text: [], background: ['#160b2c'] },
  light: { text: [], background: [] },
}

export const TEXT_SCALE: Record<TextSize, number> = { small: 0.7, medium: 1, large: 1.4 }

const KEY = 'appearance'

function load() {
  const base = {
    theme: 'dark' as Theme,
    colors: { dark: { ...THEME_DEFAULTS.dark }, light: { ...THEME_DEFAULTS.light } },
    size: 'medium' as TextSize,
  }
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? 'null')
    if (saved && typeof saved === 'object') {
      if (saved.theme === 'dark' || saved.theme === 'light') base.theme = saved.theme
      if (saved.size in TEXT_SCALE) base.size = saved.size
      for (const t of ['dark', 'light'] as Theme[]) {
        for (const which of ['text', 'background'] as const) {
          const v = saved.colors?.[t]?.[which]
          if (typeof v === 'string' && !FORMER_DEFAULTS[t][which].includes(v.toLowerCase())) base.colors[t][which] = v
        }
      }
    }
  } catch { /* storage unavailable: defaults */ }
  return base
}

const state = reactive(load())

function apply() {
  const root = document.documentElement
  const c = state.colors[state.theme]
  root.classList.add('app-themed')
  root.classList.toggle('theme-dark', state.theme === 'dark')
  root.style.setProperty('--app-fg', c.text)
  root.style.setProperty('--app-bg', c.background)
  root.style.colorScheme = state.theme
  root.style.fontSize = `${TEXT_SCALE[state.size] * 100}%`
}

watch(state, () => {
  apply()
  try { localStorage.setItem(KEY, JSON.stringify(state)) } catch { /* not remembered */ }
}, { deep: true })

export function useAppearance() {
  apply()
  return state
}
