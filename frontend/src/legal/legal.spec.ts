// @vitest-environment jsdom
// (DOMPurify mangles HTML under happy-dom; real browsers and jsdom are fine.)
import { describe, expect, it } from 'vitest'
import { LEGAL_PAGES, legalPage, placeholdersIn, renderLegal } from './legal'

describe('legal pages', () => {
  it('each page loads with its title; unknown pages are refused', () => {
    expect(LEGAL_PAGES.map(s => legalPage(s)?.title)).toEqual(['Terms of Service', 'Privacy Policy', 'Cancellation and Refund Policy'])
    expect(legalPage('nope')).toBeNull()
  })

  it('[[TO FILL: …]] items are listed and shown highlighted, never left raw', () => {
    for (const slug of LEGAL_PAGES) {
      const page = legalPage(slug)!
      const todo = placeholdersIn(page)
      const html = renderLegal(page)
      expect(html).not.toContain('[[')
      expect((html.match(/<mark class="legal-todo">/g) ?? []).length).toBe(todo.length)
    }
    // All three were completed on 2026-10-22.
    for (const slug of LEGAL_PAGES) expect(placeholdersIn(legalPage(slug)!)).toEqual([])
    // The highlighting itself, on a made-up page.
    const draft = { slug: 'terms' as const, title: 'T', body: 'Effective [[TO FILL: a\n date]].' }
    expect(placeholdersIn(draft)).toEqual(['a date'])
    expect(renderLegal(draft)).toContain('<mark class="legal-todo">TO FILL: a date</mark>')
  })

  it('the privacy policy states the facts the app depends on', () => {
    const text = legalPage('privacy')!.body
    for (const fact of ['email address, phone number and zipcode', 'never see or store your card details', 'Super administrators',
      'Stripe', 'Resend', 'Fly.io', 'Neon', 'Cloudflare']) expect(text).toContain(fact)
  })
})
