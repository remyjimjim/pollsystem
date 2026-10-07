import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import PollTemplatesView from './PollTemplatesView.vue'

vi.mock('axios')

const ELECTION = { id: 1, name: 'Election', pollType: 1, template: { type: 'Election', fields: { candidates: { widget: 'selectOneRadio' } } } }

afterEach(() => vi.resetAllMocks())

async function mountEditingElection() {
  let template: unknown = ELECTION.template
  vi.mocked(axios.get).mockImplementation(async () => ({ data: [{ ...ELECTION, template }] }))
  vi.mocked(axios.put).mockImplementation(async (_url: string, body: unknown) => {
    template = body
    return { data: { ...ELECTION, template } }
  })
  const w = mount(PollTemplatesView)
  await flushPromises()
  await w.findAll('button').find(b => b.text() === 'Edit')!.trigger('click')
  return w
}

describe('PollTemplatesView', () => {
  it('keeps "Saved." on screen after the post-save reload', async () => {
    const w = await mountEditingElection()
    const changed = { ...ELECTION.template, fields: { candidates: { widget: 'selectManyCheckbox' } } }
    await w.find('textarea').setValue(JSON.stringify(changed))
    await w.findAll('button').find(b => b.text() === 'Save')!.trigger('click')
    await flushPromises() // PUT + the reload it triggers

    expect(axios.get).toHaveBeenCalledTimes(2) // initial load + reload after save
    expect(w.text()).toContain('Saved.')
    expect(w.find('pre').text()).toContain('selectManyCheckbox')
    w.unmount()
  })

  it('rejects invalid JSON without saving', async () => {
    const w = await mountEditingElection()
    await w.find('textarea').setValue('{ "type": "Election", oops }')
    await w.findAll('button').find(b => b.text() === 'Save')!.trigger('click')
    await flushPromises()

    expect(axios.put).not.toHaveBeenCalled()
    expect(w.text()).toContain('Invalid JSON')
    expect(w.text()).not.toContain('Saved.')
    w.unmount()
  })
})
