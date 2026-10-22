import { describe, expect, it } from 'vitest'
import { cleanEmail, cleanPhone, cleanZipcode, PHONE_OK, ZIPCODE_OK } from './cleanInput'

describe('cleanInput', () => {
  it('a phone copied with a non-breaking hyphen, odd spaces or invisible marks becomes plain', () => {
    expect(cleanPhone('(401) 992‑4938')).toBe('(401) 992-4938')
    expect(cleanPhone(' ‪(401) 992–4938‬ ')).toBe('(401) 992-4938')
    expect(cleanPhone('４０１ ９９２ ４９３８')).toBe('401 992 4938')          // full-width digits
    expect(PHONE_OK.test(cleanPhone('(401) 992‑4938'))).toBe(true)
  })

  it('a zipcode or email with stray spaces is trimmed', () => {
    expect(cleanZipcode('98264 ')).toBe('98264')
    expect(cleanZipcode(' 98264')).toBe('98264')
    expect(ZIPCODE_OK.test(cleanZipcode(' 98264 '))).toBe(true)
    expect(cleanEmail(' Pat@Example.com​ ')).toBe('Pat@Example.com')
  })

  it('leaves real mistakes for the form to report', () => {
    expect(ZIPCODE_OK.test(cleanZipcode('9826'))).toBe(false)
    expect(PHONE_OK.test(cleanPhone('call me'))).toBe(false)
  })
})
