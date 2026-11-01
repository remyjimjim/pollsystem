import { ref } from 'vue'
import axios from 'axios'
import { replaceBadWord, type BadWord } from './useCommentModeration'

/**
 * Listed words in text other than comments (decided 2026-11-01): titles,
 * summaries, questions, office names, request reasons, notes and messages.
 * Each listed word found asks "Can I replace X with Y?". Yes replaces it; No
 * depends on the server's moderation.text-mode, sent with every check:
 * - BLOCK: back to the field to reword it (the server refuses the save anyway);
 * - WARN: it's kept as typed.
 * App.vue renders the question (<ReplaceWordDialog> with [textAsking]).
 */
export type TextMode = 'BLOCK' | 'WARN'

/** A text input to check: how to read / change it, and its id to focus. */
export interface TextField {
  inputId?: string
  get(): string | null | undefined
  set(text: string): void
  /** No keeps it as typed even in BLOCK mode (a ballot measure's official summary). */
  warnOnly?: boolean
}

/** The question on screen: the word, and whether No keeps the text. */
export const textAsking = ref<{ word: BadWord; keepOnNo: boolean } | null>(null)
let resolve: ((yes: boolean) => void) | null = null

export function answerText(yes: boolean) {
  textAsking.value = null
  const r = resolve
  resolve = null
  r?.(yes)
}

function ask(word: BadWord, keepOnNo: boolean): Promise<boolean> {
  textAsking.value = { word, keepOnNo }
  return new Promise(r => { resolve = r })
}

/**
 * Before saving: asks about each listed word in [fields]. Returns null to go
 * on saving, or the word to reword first (its field is focused). If the check
 * itself fails, nothing is asked: the server checks again on save.
 */
export async function reviewText(fields: TextField[]): Promise<string | null> {
  const list = fields.filter(f => f.get()?.trim())
  if (list.length === 0) return null
  let found: BadWord[][]
  let mode: TextMode
  try {
    const r = await axios.post<{ found: BadWord[][]; textMode?: TextMode }>('/api/moderation/check', { texts: list.map(f => f.get() ?? '') })
    found = r.data?.found ?? []
    mode = r.data?.textMode ?? 'BLOCK'
  } catch {
    return null
  }
  for (const [i, f] of list.entries()) {
    for (const b of found[i] ?? []) {
      const el = f.inputId ? document.getElementById(f.inputId) : null
      el?.focus()
      const keepOnNo = mode === 'WARN' || !!f.warnOnly
      if (await ask(b, keepOnNo)) {
        f.set(replaceBadWord(f.get() ?? '', b))
      } else if (keepOnNo) {
        break
      } else {
        el?.focus()
        return b.word
      }
    }
  }
  return null
}
