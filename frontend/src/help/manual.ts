import { marked, type Tokens } from 'marked'
import DOMPurify from 'dompurify'
import { AccessLevel } from '@/types'

// The help library. Its pages are the Markdown files in docs/manual/ (one
// folder per audience, one file per process), bundled at build time, so the
// same files read on GitHub and render at /help. Hiding a section from lower
// access levels is a convenience, not secrecy: every page ships in the bundle.

/** Audience folders, in display order, with the access each one needs. */
export const SECTIONS = [
  { key: 'viewer', minAccess: null },
  { key: 'user', minAccess: AccessLevel.USER },
  { key: 'creator', minAccess: AccessLevel.CREATOR },
  { key: 'admin', minAccess: AccessLevel.ADMIN },
  { key: 'super', minAccess: AccessLevel.SUPER },
] as const

export type SectionKey = (typeof SECTIONS)[number]['key']

export interface HelpPage {
  section: SectionKey
  slug: string
  title: string
  summary: string
  order: number
  body: string
}

/**
 * Optional front matter between `---` lines: `title`, `summary`, `order`.
 * Without a title, the first `# Heading` is used.
 */
export function parsePage(section: SectionKey, slug: string, raw: string): HelpPage {
  let body = raw.replace(/^﻿/, '')
  const meta: Record<string, string> = {}
  const fm = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/.exec(body)
  if (fm) {
    for (const line of fm[1].split(/\r?\n/)) {
      const m = /^(\w+):\s*(.*)$/.exec(line)
      if (m) meta[m[1]] = m[2].trim()
    }
    body = body.slice(fm[0].length)
  }
  const heading = /^#\s+(.+)$/m.exec(body)
  const order = Number(meta.order)
  return {
    section,
    slug,
    title: meta.title || heading?.[1].trim() || slug,
    summary: meta.summary ?? '',
    order: Number.isFinite(order) ? order : 999,
    body,
  }
}

const files = import.meta.glob<string>('@manual/*/*.md', { query: '?raw', import: 'default', eager: true })

/** Every page, by section order then page order. */
export const PAGES: HelpPage[] = Object.entries(files)
  .map(([path, raw]) => {
    const m = /\/([^/]+)\/([^/]+)\.md$/.exec(path)
    const section = SECTIONS.find(s => s.key === m?.[1])
    return section && m ? parsePage(section.key, m[2], raw) : null
  })
  .filter((p): p is HelpPage => p !== null)
  .sort((a, b) =>
    sectionIndex(a.section) - sectionIndex(b.section) || a.order - b.order || a.title.localeCompare(b.title))

function sectionIndex(key: SectionKey): number {
  return SECTIONS.findIndex(s => s.key === key)
}

/** Whether someone with [hasAccess] may see [section]; anonymous visitors see `viewer` only. */
export function canSee(section: SectionKey, hasAccess: (level: AccessLevel) => boolean): boolean {
  const min = SECTIONS.find(s => s.key === section)?.minAccess
  return min === null || (min !== undefined && hasAccess(min))
}

export function findPage(section: string, slug: string, pages: HelpPage[] = PAGES): HelpPage | undefined {
  return pages.find(p => p.section === section && p.slug === slug)
}

/**
 * Markdown → sanitised HTML. Links to other manual pages (`../user/x.md`,
 * `x.md#anchor`) become /help routes, so the same link works on GitHub and in
 * the app; other links pass through.
 */
export function renderPage(page: Pick<HelpPage, 'section' | 'body'>): string {
  const html = marked.parse(page.body, {
    async: false,
    gfm: true,
    walkTokens(token) {
      if (token.type === 'link') {
        const link = token as Tokens.Link
        link.href = helpHref(page.section, link.href)
      }
    },
  })
  return DOMPurify.sanitize(html)
}

export function helpHref(fromSection: string, href: string): string {
  const m = /^(?:\.\/|\.\.\/(\w+)\/)?([\w-]+)\.md(#[\w-]*)?$/.exec(href)
  if (!m || /^[a-z]+:/i.test(href)) return href
  return `/help/${m[1] ?? fromSection}/${m[2]}${m[3] ?? ''}`
}
