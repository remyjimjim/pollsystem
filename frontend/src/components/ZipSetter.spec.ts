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

  it('loads states on mount', async () => {
    mockedAxios.get.mockResolvedValueOnce({
      data: [{ id: 5, name: 'California', initial: 'CA' }]
    })

    const wrapper = mount(ZipSetter, { props: { modelValue: [] } })
    await flushPromises()

    expect(mockedAxios.get).toHaveBeenCalledWith('/api/states')
    const options = wrapper.findAll('select option')
    // First option is the placeholder + 1 seeded state
    expect(options.length).toBeGreaterThanOrEqual(2)
    expect(options.at(-1)?.text()).toContain('California')
  })

  it('cascades: selecting a state fetches counties for that state', async () => {
    mockedAxios.get
      .mockResolvedValueOnce({  // /api/states
        data: [{ id: 5, name: 'California', initial: 'CA' }]
      })
      .mockResolvedValueOnce({  // /api/counties?state_id=5
        data: [{ id: 10, stateId: 5, name: 'Los Angeles' }]
      })

    const wrapper = mount(ZipSetter, { props: { modelValue: [] } })
    await flushPromises()

    await wrapper.find('select').setValue(5)
    await flushPromises()

    expect(mockedAxios.get).toHaveBeenNthCalledWith(2, '/api/counties', {
      params: { state_id: 5 }
    })
    expect(wrapper.text()).toContain('Los Angeles')
  })

  it('emits update:modelValue when a zipcode is checked', async () => {
    mockedAxios.get
      .mockResolvedValueOnce({  // states
        data: [{ id: 5, name: 'California', initial: 'CA' }]
      })
      .mockResolvedValueOnce({  // counties
        data: [{ id: 10, stateId: 5, name: 'Los Angeles' }]
      })
      .mockResolvedValueOnce({  // zipcodes
        data: [{ id: 100, countyId: 10, zipcode: '90001' }]
      })

    const wrapper = mount(ZipSetter, { props: { modelValue: [] } })
    await flushPromises()

    await wrapper.find('select').setValue(5)
    await flushPromises()

    // Tick the county checkbox
    const countyCheckbox = wrapper.find('input[type="checkbox"][value="10"]')
    await countyCheckbox.setValue(true)
    await flushPromises()

    expect(mockedAxios.get).toHaveBeenNthCalledWith(3, '/api/zipcodes', {
      params: { county_ids: '10' }
    })

    // Now tick the zipcode checkbox
    const zipCheckbox = wrapper.find('input[type="checkbox"][value="90001"]')
    await zipCheckbox.setValue(true)
    await flushPromises()

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted![emitted!.length - 1][0]).toEqual(['90001'])
  })

  it('shows an error message when the states fetch fails', async () => {
    mockedAxios.get.mockRejectedValueOnce(new Error('network down'))

    const wrapper = mount(ZipSetter, { props: { modelValue: [] } })
    await flushPromises()

    expect(wrapper.text()).toContain('Failed to load states')
  })
})
