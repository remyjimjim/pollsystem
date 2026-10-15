import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { findBadWords, replaceBadWord, resetBadWordsCache, useCommentModeration, type BadWord } from './useCommentModeration'

vi.mock('axios')

const LIST: BadWord[] = [{ word: 'darn', replace: 'dang' }, { word: 'heck', replace: 'h*ck' }]

describe('bad-word matching', () => {
  it('matches whole words, ignoring case', () => {
    expect(findBadWords('Well DARN it', LIST).map(b => b.word)).toEqual(['darn'])
    expect(findBadWords('darned heckle', LIST)).toEqual([])
    expect(findBadWords('darn, heck!', LIST).map(b => b.word)).toEqual(['darn', 'heck'])
    expect(findBadWords('   ', LIST)).toEqual([])
  })

  it('replaces every occurrence of just that word', () => {
    expect(replaceBadWord('Darn, darn, darned', LIST[0])).toBe('Dang, dang, darned')
  })
})

describe('reviewing public comments before submit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetBadWordsCache()
    vi.mocked(axios.get).mockResolvedValue({ data: LIST })
  })

  function entry(text: string, isPublic: boolean) {
    const e = { text, isPublic }
    return {
      e,
      entry: {
        inputId: 'x',
        get: () => e.text,
        set: (t: string) => { e.text = t },
        isPublic: () => e.isPublic,
        makePrivate: () => { e.isPublic = false }
      }
    }
  }

  it('asks per word: Yes replaces it, No keeps the comment private and stops asking', async () => {
    const m = useCommentModeration()
    const a = entry('darn and heck', true)
    const b = entry('heck no', true)
    const done = m.review([a.entry, b.entry])

    await vi.waitFor(() => expect(m.asking.value?.word).toBe('darn'))
    m.answer(true)                                  // a: replace darn
    await vi.waitFor(() => expect(m.asking.value?.word).toBe('heck'))
    m.answer(true)                                  // a: replace heck
    await vi.waitFor(() => expect(m.asking.value?.word).toBe('heck'))
    m.answer(false)                                 // b: keep private
    await done

    expect(a.e).toEqual({ text: 'dang and h*ck', isPublic: true })
    expect(b.e).toEqual({ text: 'heck no', isPublic: false })
    expect(m.asking.value).toBeNull()
  })

  it("doesn't check private comments, or load the list when nothing is public", async () => {
    const m = useCommentModeration()
    const a = entry('darn', false)
    await m.review([a.entry])
    expect(m.asking.value).toBeNull()
    expect(axios.get).not.toHaveBeenCalled()
    expect(a.e.text).toBe('darn')
  })
})
