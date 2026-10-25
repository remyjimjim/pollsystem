import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * The legal pages (Terms, Privacy, Refunds) and "Why?": Markdown in this folder, shown at
 * /legal/<slug>. English only for now. Anything still to be supplied by the
 * owner is written [[TO FILL: …]] and shown highlighted, so none can go live
 * unnoticed (see placeholdersIn).
 */
export const LEGAL_PAGES = ['terms', 'privacy', 'refunds', 'why'] as const
export type LegalSlug = (typeof LEGAL_PAGES)[number]
/** The policies: the "English only" banner applies to these, not to "Why?". */
export const POLICY_PAGES: readonly LegalSlug[] = ['terms', 'privacy', 'refunds']

const files = import.meta.glob<string>('./*.md', { query: '?raw', import: 'default', eager: true })

export interface LegalPage { slug: LegalSlug; title: string; body: string }

export function legalPage(slug: string): LegalPage | null {
  if (!(LEGAL_PAGES as readonly string[]).includes(slug)) return null
  const raw = files[`./${slug}.md`]
  if (raw === undefined) return null
  const fm = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/.exec(raw)
  const title = fm ? (/^title:\s*(.+)$/m.exec(fm[1])?.[1].trim() ?? slug) : slug
  return { slug: slug as LegalSlug, title, body: fm ? raw.slice(fm[0].length) : raw }
}

const PLACEHOLDER = /\[\[TO FILL:\s*([\s\S]*?)\]\]/g

/** The [[TO FILL: …]] items still in a page (empty when it's ready to publish). */
export function placeholdersIn(page: LegalPage): string[] {
  return [...page.body.matchAll(PLACEHOLDER)].map(m => m[1].replace(/\s+/g, ' ').trim())
}

/** Markdown → sanitised HTML, with [[TO FILL: …]] highlighted. */
export function renderLegal(page: LegalPage): string {
  const marked_ = page.body.replace(PLACEHOLDER, (_, what: string) =>
    `<mark class="legal-todo">TO FILL: ${what.replace(/\s+/g, ' ').trim()}</mark>`)
  return DOMPurify.sanitize(marked.parse(marked_, { async: false, gfm: true }))
}
