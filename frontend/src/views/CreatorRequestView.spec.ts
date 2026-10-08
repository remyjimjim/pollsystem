import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import CreatorRequestView from './CreatorRequestView.vue'
import AdminRequestView from './AdminRequestView.vue'

vi.mock('axios')
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), RouterLink: { template: '<a><slot/></a>' } }))

async function scopeOptions(view: unknown) {
  vi.mocked(axios.get).mockResolvedValue({ data: [] })
  const w = mount(view as never)
  await flushPromises()
  const radios = w.findAll('input[type="radio"][name="scopeLevel"]')
  return {
    values: radios.map(r => (r.element as HTMLInputElement).value),
    checked: radios.find(r => (r.element as HTMLInputElement).checked)?.attributes('value'),
  }
}

// Creator and admin access is granted statewide or nationwide only.
describe('access requests offer statewide or nationwide only', () => {
  it('creator request: Nationwide and States, starting on States', async () => {
    expect(await scopeOptions(CreatorRequestView)).toEqual({ values: ['NATIONAL', 'STATE'], checked: 'STATE' })
  })

  it('admin request: the same', async () => {
    expect(await scopeOptions(AdminRequestView)).toEqual({ values: ['NATIONAL', 'STATE'], checked: 'STATE' })
  })
})
