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
    expect(placeholdersIn(legalPage('privacy')!)).toContain('contact email')
  })

  it('the privacy policy states the facts the app depends on', () => {
    const text = legalPage('privacy')!.body
    for (const fact of ['email address, phone number and zipcode', 'never see or store your card details', 'Super administrators',
      'Stripe', 'Resend', 'Fly.io', 'Neon', 'Cloudflare']) expect(text).toContain(fact)
  })
})
