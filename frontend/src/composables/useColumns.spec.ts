import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { useColumns } from './useColumns'

const KEYS = ['a', 'b', 'c'] as const

describe('useColumns', () => {
  afterEach(() => localStorage.clear())

  it('starts with every column shown, and remembers changes on this device', async () => {
    const cols = useColumns('t.cols', KEYS)
    expect(cols.value).toEqual({ a: true, b: true, c: true })
    cols.value = { ...cols.value, b: false }
    await nextTick()
    expect(useColumns('t.cols', KEYS).value).toEqual({ a: true, b: false, c: true })
  })

  it('ignores stored junk, and a column added later starts shown', () => {
    localStorage.setItem('t.cols', JSON.stringify({ a: false, gone: false, b: 'x' }))
    expect(useColumns('t.cols', KEYS).value).toEqual({ a: false, b: true, c: true })
  })

  it('the first time on a narrow screen, the named columns start hidden', () => {
    const was = window.innerWidth
    Object.defineProperty(window, 'innerWidth', { value: 400, configurable: true })
    try {
      expect(useColumns('t.narrow', KEYS, ['c']).value).toEqual({ a: true, b: true, c: false })
    } finally {
      Object.defineProperty(window, 'innerWidth', { value: was, configurable: true })
    }
  })
})
