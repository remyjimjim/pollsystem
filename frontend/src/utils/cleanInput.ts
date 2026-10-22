/**
 * Tidy what people type (or paste, or autofill) before it's sent. Characters
 * they can't see shouldn't fail a form: stray spaces, and look-alikes copied
 * from contacts apps and web pages (a non-breaking hyphen for "-", a
 * non-breaking space for " ", invisible direction marks).
 */

// Invisible: zero-width spaces/joiners, direction marks, word joiner, BOM.
const INVISIBLE = /[​-‏‪-‮⁠-⁤﻿]/g
// Hyphen and dash look-alikes, and the minus sign.
const DASHES = /[‐-―−﹘﹣－]/g
// Space look-alikes (non-breaking, thin, figure, ideographic, ...).
const SPACES = /[   -   　]/g

function plain(s: string): string {
  return s.normalize('NFKC').replace(INVISIBLE, '').replace(DASHES, '-').replace(SPACES, ' ')
}

export function cleanEmail(s: string): string {
  return plain(s).replace(/\s+/g, '')
}

export function cleanPhone(s: string): string {
  return plain(s).replace(/\s+/g, ' ').trim()
}

export function cleanZipcode(s: string): string {
  return plain(s).replace(/\s+/g, '')
}

/** The same rules the server applies. */
export const PHONE_OK = /^[0-9+\-() ]{7,20}$/
export const ZIPCODE_OK = /^[0-9]{5}$/
