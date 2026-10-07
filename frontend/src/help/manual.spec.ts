// @vitest-environment jsdom
// (DOMPurify mangles HTML under happy-dom; real browsers and jsdom are fine.)
import { describe, expect, it } from 'vitest'
import { AccessLevel } from '@/types'
import { PAGES, canSee, findPage, helpHref, parsePage, renderPage } from './manual'

const RANK = { VIEWER: 0, USER: 1, CREATOR: 2, ADMIN: 3, SUPER: 4 }
const as = (level: keyof typeof RANK | null) => (needed: AccessLevel) =>
  level !== null && RANK[level] >= RANK[needed]

describe('manual: parsing', () => {
  it('reads title, summary and order from front matter and strips it', () => {
    const p = parsePage('viewer', 'x', '---\ntitle: Finding polls\nsummary: Search.\norder: 2\n---\n\n# Heading\n\nBody')
    expect(p).toMatchObject({ title: 'Finding polls', summary: 'Search.', order: 2 })
    expect(p.body).not.toContain('title:')
    expect(p.body).toContain('# Heading')
  })

  it('falls back to the first heading, then the slug', () => {
    expect(parsePage('user', 'a', '# From heading\n\ntext').title).toBe('From heading')
    expect(parsePage('user', 'b-slug', 'no heading').title).toBe('b-slug')
    expect(parsePage('user', 'a', 'x').order).toBe(999)
  })

  it('bundles the real docs/manual pages (README excluded)', () => {
    const p = findPage('viewer', 'finding-polls')
    expect(p?.title).toBe('Finding polls')
    expect(PAGES.some(x => x.slug === 'README')).toBe(false)
  })
})

describe('manual: who sees what', () => {
  it('anonymous visitors see only the viewer section', () => {
    expect(canSee('viewer', as(null))).toBe(true)
    expect(canSee('user', as(null))).toBe(false)
  })

  it('each level sees its own section and those below', () => {
    expect(['viewer', 'user', 'creator', 'admin', 'super'].map(s => canSee(s as never, as('CREATOR'))))
      .toEqual([true, true, true, false, false])
    expect(canSee('super', as('SUPER'))).toBe(true)
    expect(canSee('user', as('VIEWER'))).toBe(false)
  })
})

describe('manual: rendering', () => {
  it('rewrites links between manual pages to /help routes', () => {
    expect(helpHref('viewer', '../user/becoming-a-creator.md')).toBe('/help/user/becoming-a-creator')
    expect(helpHref('viewer', 'viewing-results.md#privacy')).toBe('/help/viewer/viewing-results#privacy')
    expect(helpHref('viewer', './viewing-results.md')).toBe('/help/viewer/viewing-results')
    expect(helpHref('viewer', 'https://example.com/a.md')).toBe('https://example.com/a.md')
    expect(helpHref('viewer', '/polls/search')).toBe('/polls/search')
  })

  it('renders Markdown and strips anything unsafe', () => {
    const html = renderPage({
      section: 'viewer',
      body: '## Hi\n\nSee [results](viewing-results.md).\n\n<script>alert(1)</script><img src=x onerror="alert(2)">',
    })
    expect(html).toContain('<h2>Hi</h2>')
    expect(html).toContain('href="/help/viewer/viewing-results"')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('onerror')
  })
})
