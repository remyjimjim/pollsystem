import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import { useGeoPicker } from './useGeoPicker'

vi.mock('axios')
const mockedAxios = vi.mocked(axios, true)

// Headless composables can't be exercised in isolation because they
// depend on Vue's setup() lifecycle for refs / watchers — so we mount a
// minimal host component that exposes the return value on the wrapper.
// Refs come back as Refs (not unwrapped) when exposed; tests reach into
// `.value` to read / mutate. Keeps the composable contract honest.
function host(opts?: Parameters<typeof useGeoPicker>[0]) {
  const Host = defineComponent({
    setup(_, { expose }) {
      const picker = useGeoPicker(opts)
      expose(picker)
      return () => h('div')
    },
  })
  return mount(Host)
}

describe('useGeoPicker', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(() => vi.restoreAllMocks())

  it('loads states on setup', async () => {
    mockedAxios.get.mockResolvedValueOnce({
      data: [{ id: 5, name: 'California', initial: 'CA' }]
    })

    const wrapper = host()
    await flushPromises()

    expect(mockedAxios.get).toHaveBeenCalledWith('/api/states')
    const items = ((wrapper.vm as any).states).items.value
    expect(items.length).toBe(1)
    expect(items[0].name).toBe('California')
  })

  it('cascades: selecting a state fetches counties for it', async () => {
    mockedAxios.get
      .mockResolvedValueOnce({  // states
        data: [{ id: 5, name: 'California', initial: 'CA' }]
      })
      .mockResolvedValueOnce({  // counties
        data: [{ id: 10, stateId: 5, name: 'Los Angeles' }]
      })

    const wrapper = host()
    await flushPromises()

    ;((wrapper.vm as any).states).selected.value.push(5)
    await flushPromises()

    expect(mockedAxios.get).toHaveBeenNthCalledWith(2, '/api/counties', {
      params: { state_id: '5' }
    })
    expect(((wrapper.vm as any).counties).items.value.length).toBe(1)
  })

  it('multi-state: comma-separates state_id when several are picked', async () => {
    mockedAxios.get
      .mockResolvedValueOnce({  // states
        data: [
          { id: 5, name: 'California', initial: 'CA' },
          { id: 6, name: 'Colorado',   initial: 'CO' },
        ]
      })
      .mockResolvedValueOnce({  // counties for CA
        data: [{ id: 10, stateId: 5, name: 'Los Angeles' }]
      })
      .mockResolvedValueOnce({  // counties for CA + CO
        data: [
          { id: 10, stateId: 5, name: 'Los Angeles' },
          { id: 20, stateId: 6, name: 'Denver' },
        ]
      })

    const wrapper = host()
    await flushPromises()

    ;((wrapper.vm as any).states).selected.value.push(5)
    await flushPromises()
    ;((wrapper.vm as any).states).selected.value.push(6)
    await flushPromises()

    expect(mockedAxios.get).toHaveBeenLastCalledWith('/api/counties', {
      params: { state_id: '5,6' }
    })
  })

  it('toggleAll: zero visible selected → selects all visible (preserves out-of-window)', async () => {
    mockedAxios.get.mockResolvedValueOnce({
      data: [
        { id: 1, name: 'Alabama',  initial: 'AL' },
        { id: 2, name: 'Arkansas', initial: 'AR' },
        { id: 3, name: 'Colorado', initial: 'CO' },
      ]
    })

    const wrapper = host()
    await flushPromises()

    const states = (wrapper.vm as any).states
    // Pre-select Colorado (out-of-window once we filter to "A*")
    states.selected.value.push(3)
    states.filter.value = 'A'
    await nextTick()
    expect(states.displayed.value.map((s: any) => s.id)).toEqual([1, 2])
    // None of the visible items are selected → toggle ADDS them all.
    states.toggleAll()
    expect([...states.selected.value].sort((a: number, b: number) => a - b)).toEqual([1, 2, 3])
  })

  it('toggleAll: partial selection (indeterminate) → drops the partial selection', async () => {
    mockedAxios.get.mockResolvedValueOnce({
      data: [
        { id: 1, name: 'Alabama',  initial: 'AL' },
        { id: 2, name: 'Arkansas', initial: 'AR' },
        { id: 3, name: 'Colorado', initial: 'CO' },
      ]
    })

    const wrapper = host()
    await flushPromises()

    const states = (wrapper.vm as any).states
    // One of the two visible filtered states already selected — Select-all
    // checkbox would render as indeterminate. Clicking it should clear the
    // partial selection, not add the remaining item.
    states.selected.value.push(1, 3)
    states.filter.value = 'A'
    await nextTick()
    expect(states.someSelected.value).toBe(true)
    states.toggleAll()
    // 1 (visible+selected) dropped; 3 (out-of-window) untouched.
    expect(states.selected.value).toEqual([3])
  })

  it('select-all again when all visible are checked deselects only visible', async () => {
    mockedAxios.get.mockResolvedValueOnce({
      data: [
        { id: 1, name: 'Alabama',  initial: 'AL' },
        { id: 2, name: 'Arkansas', initial: 'AR' },
        { id: 3, name: 'Colorado', initial: 'CO' },
      ]
    })

    const wrapper = host()
    await flushPromises()

    const states = (wrapper.vm as any).states
    states.selected.value.push(1, 2, 3)
    states.filter.value = 'A'
    await nextTick()

    states.toggleAll()  // all visible (1, 2) checked → uncheck just those
    expect(states.selected.value).toEqual([3])
  })

  it('state filter matches both name and initial', async () => {
    mockedAxios.get.mockResolvedValueOnce({
      data: [
        { id: 5, name: 'California', initial: 'CA' },
        { id: 6, name: 'Colorado',   initial: 'CO' },
      ]
    })

    const wrapper = host()
    await flushPromises()

    const states = (wrapper.vm as any).states
    states.filter.value = 'ca'  // "California" by name AND "CA" by initial
    await nextTick()
    expect(states.displayed.value.map((s: any) => s.id)).toEqual([5])

    states.filter.value = 'co'  // "Colorado" by name AND "CO" by initial
    await nextTick()
    expect(states.displayed.value.map((s: any) => s.id)).toEqual([6])
  })
})
