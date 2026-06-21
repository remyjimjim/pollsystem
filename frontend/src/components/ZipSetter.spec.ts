import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import ZipSetter from './ZipSetter.vue'

vi.mock('axios')
const mockedAxios = vi.mocked(axios, true)

describe('ZipSetter', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('loads states on mount and renders them as checkboxes', async () => {
    mockedAxios.get.mockResolvedValueOnce({
      data: [{ id: 5, name: 'California', initial: 'CA' }]
    })

    const wrapper = mount(ZipSetter, { props: { modelValue: [] } })
    await flushPromises()

    expect(mockedAxios.get).toHaveBeenCalledWith('/api/states')
    expect(wrapper.text()).toContain('California')
    // State is now multi-select via checkboxes (was <select>).
    expect(wrapper.find('input[type="checkbox"][value="5"]').exists()).toBe(true)
  })

  it('cascades: ticking a state fetches counties using state_id list', async () => {
    mockedAxios.get
      .mockResolvedValueOnce({  // /api/states
        data: [{ id: 5, name: 'California', initial: 'CA' }]
      })
      .mockResolvedValueOnce({  // /api/counties?state_id=5
        data: [{ id: 10, stateId: 5, name: 'Los Angeles' }]
      })

    const wrapper = mount(ZipSetter, { props: { modelValue: [] } })
    await flushPromises()

    // Tick the State checkbox
    await wrapper.find('input[type="checkbox"][value="5"]').setValue(true)
    await flushPromises()

    // state_id arrives joined by comma even for a single state
    expect(mockedAxios.get).toHaveBeenNthCalledWith(2, '/api/counties', {
      params: { state_id: '5' }
    })
    expect(wrapper.text()).toContain('Los Angeles')
  })

  it('emits update:modelValue when a zipcode is checked', async () => {
    mockedAxios.get
      .mockResolvedValueOnce({  // states
        data: [{ id: 5, name: 'California', initial: 'CA' }]
      })
      .mockResolvedValueOnce({  // counties (triggered by state tick)
        data: [{ id: 10, stateId: 5, name: 'Los Angeles' }]
      })
      .mockResolvedValueOnce({  // zipcodes (triggered by county tick)
        data: [{ id: 100, countyId: 10, zipcode: '90001' }]
      })

    const wrapper = mount(ZipSetter, { props: { modelValue: [] } })
    await flushPromises()

    // Tick state, then county, then zip.
    await wrapper.find('input[type="checkbox"][value="5"]').setValue(true)
    await flushPromises()

    await wrapper.find('input[type="checkbox"][value="10"]').setValue(true)
    await flushPromises()

    expect(mockedAxios.get).toHaveBeenNthCalledWith(3, '/api/zipcodes', {
      params: { county_ids: '10' }
    })

    await wrapper.find('input[type="checkbox"][value="90001"]').setValue(true)
    await flushPromises()

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted![emitted!.length - 1][0]).toEqual(['90001'])
  })

  it('multi-state: ticking two states fetches counties for both', async () => {
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

    const wrapper = mount(ZipSetter, { props: { modelValue: [] } })
    await flushPromises()

    await wrapper.find('input[type="checkbox"][value="5"]').setValue(true)
    await flushPromises()
    await wrapper.find('input[type="checkbox"][value="6"]').setValue(true)
    await flushPromises()

    // The most recent counties call carries both state IDs comma-separated.
    expect(mockedAxios.get).toHaveBeenLastCalledWith('/api/counties', {
      params: { state_id: '5,6' }
    })
    expect(wrapper.text()).toContain('Los Angeles')
    expect(wrapper.text()).toContain('Denver')
  })

  it('shows an error message when the states fetch fails', async () => {
    mockedAxios.get.mockRejectedValueOnce(new Error('network down'))

    const wrapper = mount(ZipSetter, { props: { modelValue: [] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Failed to load states')
  })
})
