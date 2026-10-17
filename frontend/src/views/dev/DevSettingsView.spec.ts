import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import DevSettingsView from './DevSettingsView.vue'

vi.mock('axios')
afterEach(() => vi.resetAllMocks())

describe('DevSettingsView (local only)', () => {
  it('shows the threshold, saves a new one, and offers to go back to the default', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: { resultsThreshold: 10, resultsThresholdDefault: 10, resultsThresholdOverridden: false } })
    vi.mocked(axios.put).mockResolvedValue({ data: { resultsThreshold: 3, resultsThresholdDefault: 10, resultsThresholdOverridden: true, pollsRebuilt: 12 } })
    const w = mount(DevSettingsView)
    await flushPromises()
    expect((w.find('#results-threshold').element as HTMLInputElement).value).toBe('10')
    expect(w.find('[data-test="reset-threshold"]').exists()).toBe(false)

    await w.find('#results-threshold').setValue('3')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(axios.put).toHaveBeenCalledWith('/api/dev/settings', { resultsThreshold: 3 })
    expect(w.find('[data-test="dev-settings-saved"]').text()).toContain('hidden until 3 respondents')
    expect(w.find('[data-test="dev-settings-saved"]').text()).toContain('12 polls')

    vi.mocked(axios.put).mockResolvedValue({ data: { resultsThreshold: 10, resultsThresholdDefault: 10, resultsThresholdOverridden: false, pollsRebuilt: 12 } })
    await w.find('[data-test="reset-threshold"]').trigger('click')
    await flushPromises()
    expect(axios.put).toHaveBeenLastCalledWith('/api/dev/settings', { resultsThreshold: null })
    expect((w.find('#results-threshold').element as HTMLInputElement).value).toBe('10')
  })

  it('without the local backend, says so', async () => {
    vi.mocked(axios.get).mockRejectedValue(new Error('404'))
    const w = mount(DevSettingsView)
    await flushPromises()
    expect(w.find('[data-test="dev-settings-error"]').text()).toContain('local backend')
    expect(w.find('[data-test="results-threshold"]').exists()).toBe(false)
  })
})
