import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import axios from 'axios'
import type { BadWord } from './useCommentModeration'
import { reviewText, textAsking, answerText, type TextField } from './useTextModeration'

vi.mock('axios')

const DARN: BadWord = { word: 'darn', replace: 'dang' }

function field(text: string, warnOnly = false) {
  const box = { text }
  const f: TextField = { get: () => box.text, set: (t: string) => { box.text = t }, warnOnly }
  return { box, f }
}
/** Answers the next question once it's on screen. */
async function reply(yes: boolean) {
  await vi.waitFor(() => expect(textAsking.value).not.toBeNull())
  const q = textAsking.value!
  answerText(yes)
  await nextTick()
  return q
}

describe('listed words in titles, reasons, notes…', () => {
  beforeEach(() => vi.clearAllMocks())

  it('Yes replaces the word and saving goes on', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { found: [[DARN]], textMode: 'BLOCK' } })
    const { box, f } = field('A darn title')
    const done = reviewText([f])
    expect((await reply(true)).keepOnNo).toBe(false)
    expect(await done).toBeNull()
    expect(box.text).toBe('A dang title')
  })

  it('in BLOCK mode, No stops the save and names the word', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { found: [[DARN]], textMode: 'BLOCK' } })
    const { box, f } = field('A darn title')
    const done = reviewText([f])
    await reply(false)
    expect(await done).toBe('darn')
    expect(box.text).toBe('A darn title')
  })

  it('in WARN mode, or on a warn-only field, No keeps the text and saving goes on', async () => {
    for (const [mode, warnOnly] of [['WARN', false], ['BLOCK', true]] as const) {
      vi.mocked(axios.post).mockResolvedValue({ data: { found: [[DARN]], textMode: mode } })
      const { box, f } = field('Official darn text', warnOnly)
      const done = reviewText([f])
      expect((await reply(false)).keepOnNo).toBe(true)
      expect(await done).toBeNull()
      expect(box.text).toBe('Official darn text')
    }
  })

  it("asks nothing when every field is empty, or when the check fails (the server rechecks)", async () => {
    expect(await reviewText([field('  ').f])).toBeNull()
    expect(axios.post).not.toHaveBeenCalled()
    vi.mocked(axios.post).mockRejectedValue(new Error('offline'))
    expect(await reviewText([field('A darn title').f])).toBeNull()
    expect(textAsking.value).toBeNull()
  })
})
