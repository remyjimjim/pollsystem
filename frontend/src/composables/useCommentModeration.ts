import { ref } from 'vue'
import axios from 'axios'

/** One pair from backend moderation/bad-words.json. */
export interface BadWord { word: string; replace: string }

/** A comment box to check: how to read / change its text and its "public" box. */
export interface CommentEntry {
  /** The comment input's id, focused while asking about it. */
  inputId: string
  get(): string
  set(text: string): void
  isPublic(): boolean
  makePrivate(): void
}

let cached: Promise<BadWord[]> | null = null

/** The list, loaded once per page load. If it can't load, the server's own check still keeps a bad comment private. */
export function loadBadWords(): Promise<BadWord[]> {
  if (!cached) {
    cached = axios.get<BadWord[]>('/api/moderation/bad-words')
      .then(r => r.data ?? [])
      .catch(() => { cached = null; return [] })
  }
  return cached
}

/** Test hook: forget the loaded list. */
export function resetBadWordsCache() { cached = null }

const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** Whole word, ignoring case (same rule as the server): "Darn" matches "darn", "darned" doesn't. */
function wordPattern(word: string): RegExp {
  return new RegExp(`(?<![\\p{L}\\p{N}])${escape(word.trim())}(?![\\p{L}\\p{N}])`, 'giu')
}

export function findBadWords(text: string, list: BadWord[]): BadWord[] {
  if (!text.trim()) return []
  return list.filter(b => b.word.trim() && wordPattern(b.word).test(text))
}

/** Replaces every occurrence; a capitalised one ("Darn") gets a capitalised replacement ("Dang"). */
export function replaceBadWord(text: string, b: BadWord): string {
  return text.replace(wordPattern(b.word), found => {
    const first = found.charAt(0)
    return first !== first.toLowerCase() ? b.replace.charAt(0).toUpperCase() + b.replace.slice(1) : b.replace
  })
}

/**
 * Before submitting: for each comment marked public, focus its box and ask,
 * per listed word found, "Can I replace X with Y?". Yes replaces it; No keeps
 * that comment private (its other words aren't asked about). Render
 * <ReplaceWordDialog> with [asking] and call [answer] from it.
 */
export function useCommentModeration() {
  const asking = ref<BadWord | null>(null)
  let resolve: ((yes: boolean) => void) | null = null

  function ask(b: BadWord): Promise<boolean> {
    asking.value = b
    return new Promise(r => { resolve = r })
  }

  function answer(yes: boolean) {
    asking.value = null
    const r = resolve
    resolve = null
    r?.(yes)
  }

  async function review(entries: CommentEntry[]): Promise<void> {
    const toCheck = entries.filter(e => e.isPublic() && e.get().trim())
    if (toCheck.length === 0) return
    const list = await loadBadWords()
    for (const e of toCheck) {
      for (const b of findBadWords(e.get(), list)) {
        document.getElementById(e.inputId)?.focus()
        if (await ask(b)) {
          e.set(replaceBadWord(e.get(), b))
        } else {
          e.makePrivate()
          break
        }
      }
    }
  }

  return { asking, answer, review }
}
