import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import BallotMeasureForm from './BallotMeasureForm.vue'

vi.mock('axios')
const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

const measure = (electionId: number) => ({
  id: 31, pollTypeId: 3, electionId, title: 'Free Money Mondays', summary: 's', effectiveDate: '2026-12-01', closeDate: null,
})

async function mountFor(electionStatus: string) {
  vi.mocked(axios.get).mockResolvedValue({ data: [
    { id: 7, type: 'Election', title: 'King for a week', status: electionStatus, closeDate: null, createdAt: '2026-10-01' },
  ] })
  const w = mount(BallotMeasureForm, { props: { pollTypeId: 3, initial: measure(7) }, attachTo: document.body })
  await flushPromises()
  return w
}

afterEach(() => {
  vi.resetAllMocks()
  document.body.innerHTML = ''
})

describe('BallotMeasureForm: publishing goes through a draft election', () => {
  it('under a draft election: no Publish; it says the election publishes it, and links back', async () => {
    const w = await mountFor('DRAFT')
    expect(w.findAll('button').some(b => b.text() === 'Publish')).toBe(false)
    expect(w.find('[data-test="goes-live-with-election"]').text()).toContain('King for a week')
    await w.find('[data-test="back-to-election"]').trigger('click')
    expect(push).toHaveBeenCalledWith('/creator/polls/election/7/edit')
    w.unmount()
  })

  it('under a published election: Publish as usual', async () => {
    const w = await mountFor('PUBLISHED')
    expect(w.findAll('button').some(b => b.text() === 'Publish')).toBe(true)
    expect(w.find('[data-test="goes-live-with-election"]').exists()).toBe(false)
    w.unmount()
  })
})
