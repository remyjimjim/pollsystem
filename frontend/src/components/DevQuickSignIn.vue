<script setup lang="ts">
// Local development only (LoginView shows it when import.meta.env.DEV, so it
// isn't in staging/prod builds): one click signs in as a seeded demo user via
// the local dev endpoints, skipping the emailed link. If the backend has no dev
// endpoints, the list fails to load and nothing is shown. English only: it's a
// developer tool.
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

interface DemoUser { email: string; access: string }

const emit = defineEmits<{ 'signed-in': [] }>()
const auth = useAuthStore()
const users = ref<DemoUser[]>([])
const busy = ref<string | null>(null)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    users.value = (await axios.get<DemoUser[]>('/api/dev/demo-users')).data ?? []
  } catch {
    users.value = []
  }
})

// demo-super first, then per state: creator, admin, user, respondents 1..10.
const ROLE_ORDER = ['creator', 'admin', 'user', 'respondent']
const label = (email: string) => email.replace(/@test\.local$/, '').replace(/^demo-/, '')
const groups = computed(() => {
  const byState = new Map<string, DemoUser[]>()
  const other: DemoUser[] = []
  for (const u of users.value) {
    const m = label(u.email).match(/^([a-z]+)(\d*)-([a-z]+)$/)
    if (!m) { other.push(u); continue }
    byState.set(m[3], [...(byState.get(m[3]) ?? []), u])
  }
  const rank = (u: DemoUser) => {
    const m = label(u.email).match(/^([a-z]+)(\d*)-/)!
    return ROLE_ORDER.indexOf(m[1]) * 100 + Number(m[2] || 0)
  }
  return [
    ...(other.length ? [['', other] as const] : []),
    ...[...byState.entries()].sort().map(([st, list]) => [st, [...list].sort((a, b) => rank(a) - rank(b))] as const),
  ]
})

async function signInAs(email: string) {
  busy.value = email
  error.value = null
  try {
    const token = (await axios.post<{ token: string }>('/api/dev/token', null, { params: { email } })).data.token
    await auth.signInWithToken(token)
    emit('signed-in')
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? `Couldn't sign in as ${email}`
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <details v-if="users.length" open class="mt-6 rounded border border-dashed border-amber-400 bg-amber-50 p-3 text-sm" data-test="dev-quick-sign-in">
    <summary class="cursor-pointer font-semibold text-amber-900">Quick sign-in (local only)</summary>
    <p class="m-0 mt-1 text-xs text-amber-900">
      Demo users from scripts/seed-demo.bash. Not in staging or prod builds.
      <RouterLink to="/dev/settings" class="text-amber-900 underline">Dev settings</RouterLink>
    </p>
    <p v-if="error" class="m-0 mt-2 text-xs text-red-700">{{ error }}</p>
    <div v-for="[state, list] in groups" :key="state" class="mt-2">
      <div v-if="state" class="mb-1 text-xs font-semibold uppercase text-amber-900">{{ state }}</div>
      <div class="flex flex-wrap gap-1">
        <button
          v-for="u in list"
          :key="u.email"
          type="button"
          :title="`${u.email} (${u.access})`"
          :disabled="busy !== null"
          class="rounded border border-amber-300 bg-white px-2 py-0.5 text-xs text-slate-800 hover:bg-amber-100 disabled:opacity-50"
          @click="signInAs(u.email)"
        >{{ label(u.email) }}</button>
      </div>
    </div>
  </details>
</template>
