import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import DevQuickSignIn from './DevQuickSignIn.vue'

vi.mock('axios')

const USERS = [
  { email: 'demo-respondent2-wash@test.local', access: 'USER' },
  { email: 'demo-admin-wash@test.local', access: 'ADMIN' },
  { email: 'demo-respondent10-wash@test.local', access: 'USER' },
  { email: 'demo-creator-wash@test.local', access: 'CREATOR' },
  { email: 'demo-super@test.local', access: 'SUPER' },
  { email: 'demo-creator-cali@test.local', access: 'CREATOR' },
]

async function mountList(users: unknown[] | Error = USERS) {
  if (users instanceof Error) vi.mocked(axios.get).mockRejectedValue(users)
  else vi.mocked(axios.get).mockResolvedValue({ data: users })
  const w = mount(DevQuickSignIn, { global: { plugins: [createTestingPinia({ createSpy: vi.fn })] } })
  await flushPromises()
  return w
}

afterEach(() => vi.resetAllMocks())

describe('DevQuickSignIn (local only)', () => {
  it('lists the demo users: super first, then each state creator, admin, user, respondents in order', async () => {
    const w = await mountList()
    const buttons = w.findAll('button').map(b => b.text())
    expect(buttons).toEqual(['super', 'creator-cali', 'creator-wash', 'admin-wash', 'respondent2-wash', 'respondent10-wash'])
    expect(w.findAll('button')[0].attributes('title')).toBe('demo-super@test.local (SUPER)')
  })

  it('one click signs in as that user and says so', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: { token: 'jwt-creator-wash' } })
    const w = await mountList()
    const auth = useAuthStore()
    await w.findAll('button').find(b => b.text() === 'creator-wash')!.trigger('click')
    await flushPromises()
    expect(axios.post).toHaveBeenCalledWith('/api/dev/token', null, { params: { email: 'demo-creator-wash@test.local' } })
    expect(auth.signInWithToken).toHaveBeenCalledWith('jwt-creator-wash')
    expect(w.emitted('signed-in')).toHaveLength(1)
  })

  it('shows nothing when there are no demo users or no dev endpoints', async () => {
    expect((await mountList([])).find('[data-test="dev-quick-sign-in"]').exists()).toBe(false)
    expect((await mountList(new Error('404'))).find('[data-test="dev-quick-sign-in"]').exists()).toBe(false)
  })

  it('a failed sign-in shows the reason and signs nobody in', async () => {
    vi.mocked(axios.post).mockRejectedValue({ response: { data: { message: 'No local user with email x' } } })
    const w = await mountList()
    const auth = useAuthStore()
    await w.findAll('button')[0].trigger('click')
    await flushPromises()
    expect(w.text()).toContain('No local user with email x')
    expect(auth.signInWithToken).not.toHaveBeenCalled()
    expect(w.emitted('signed-in')).toBeUndefined()
  })
})
