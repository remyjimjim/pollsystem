import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { useAuthStore } from '@/stores/auth'
import CompleteProfileView from './CompleteProfileView.vue'

const replace = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ replace }),
  useRoute: () => ({ query: {} })
}))

function mountView() {
  return mount(CompleteProfileView, {
    global: {
      plugins: [createTestingPinia({ createSpy: vi.fn })],
      stubs: { 'router-link': true }
    }
  })
}

async function fillAndSubmit(wrapper: ReturnType<typeof mountView>) {
  await wrapper.find('input[type="tel"]').setValue('+15551234567')
  await wrapper.find('input[autocomplete="postal-code"]').setValue('90001')
  await wrapper.find('form').trigger('submit.prevent')
  await flushPromises()
}

describe('CompleteProfileView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('submits phone + zipcode and redirects home on success', async () => {
    const wrapper = mountView()
    const auth = useAuthStore()

    await fillAndSubmit(wrapper)

    expect(auth.completeProfile).toHaveBeenCalledWith({
      phone: '+15551234567',
      zipcode: '90001'
    })
    expect(replace).toHaveBeenCalledWith('/')
  })

  it('shows the backend error and does not redirect on failure', async () => {
    const wrapper = mountView()
    const auth = useAuthStore()
    vi.mocked(auth.completeProfile).mockRejectedValueOnce({
      response: { data: { message: 'Phone already registered to another account' } }
    })

    await fillAndSubmit(wrapper)

    expect(wrapper.text()).toContain('Phone already registered')
    expect(replace).not.toHaveBeenCalled()
  })
})

describe('CompleteProfileView: tidying what was typed', () => {
  it('a pasted phone with a non-breaking hyphen and a zipcode with a space are cleaned and accepted', async () => {
    const wrapper = mountView()
    const auth = useAuthStore()
    await wrapper.find('input[type="tel"]').setValue('(401) 992‑4938')
    await wrapper.find('input[autocomplete="postal-code"]').setValue('98264 ')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(auth.completeProfile).toHaveBeenCalledWith({ phone: '(401) 992-4938', zipcode: '98264' })
  })

  it('a real format mistake is reported before anything is sent', async () => {
    const wrapper = mountView()
    const auth = useAuthStore()
    await wrapper.find('input[type="tel"]').setValue('(401) 992-4938')
    await wrapper.find('input[autocomplete="postal-code"]').setValue('9826')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(auth.completeProfile).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Please enter a 5-digit zipcode')
  })
})
