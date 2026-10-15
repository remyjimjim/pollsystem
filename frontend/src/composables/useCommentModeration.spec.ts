import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { replaceBadWord, useCommentModeration, type BadWord } from './useCommentModeration'

vi.mock('axios')

const DARN: BadWord = { word: 'darn', replace: 'dang' }
const HECK: BadWord = { word: 'heck', replace: 'h*ck' }

describe('replacing a word', () => {
  it('replaces every whole-word occurrence, keeping a leading capital', () => {
    expect(replaceBadWord('Darn, darn, darned', DARN)).toBe('Dang, dang, darned')
  })
})

describe('reviewing public comments before submit', () => {
  beforeEach(() => vi.clearAllMocks())

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

  it('asks the server, then per word found: Yes replaces it, No keeps the comment private and stops asking', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { found: [[DARN, HECK], [HECK]] } })
    const m = useCommentModeration()
    const a = entry('darn and heck', true)
    const b = entry('heck no', true)
    const done = m.review([a.entry, b.entry])

    await vi.waitFor(() => expect(m.asking.value?.word).toBe('darn'))
    expect(axios.post).toHaveBeenCalledWith('/api/moderation/check', { texts: ['darn and heck', 'heck no'] })
    m.answer(true)
    await vi.waitFor(() => expect(m.asking.value?.word).toBe('heck'))
    m.answer(true)
    await vi.waitFor(() => expect(m.asking.value?.word).toBe('heck'))
    m.answer(false)
    await done

    expect(a.e).toEqual({ text: 'dang and h*ck', isPublic: true })
    expect(b.e).toEqual({ text: 'heck no', isPublic: false })
    expect(m.asking.value).toBeNull()
  })

  it("sends nothing when no comment is public", async () => {
    const m = useCommentModeration()
    await m.review([entry('darn', false).entry, entry('  ', true).entry])
    expect(axios.post).not.toHaveBeenCalled()
  })

  it("asks nothing if the check fails (saving still keeps such a comment private)", async () => {
    vi.mocked(axios.post).mockRejectedValue(new Error('offline'))
    const m = useCommentModeration()
    const a = entry('darn', true)
    await m.review([a.entry])
    expect(m.asking.value).toBeNull()
    expect(a.e).toEqual({ text: 'darn', isPublic: true })
  })
})
