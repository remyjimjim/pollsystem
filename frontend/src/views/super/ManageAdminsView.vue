<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'

interface AdminRoleAssignmentDto {
  id: number
  zipcode: string
  countyName: string
  stateInitial: string
  enabled: boolean
}
interface AdminUserDto {
  id: number
  email: string
  phone: string
  isEnabled: boolean
  access: 'ADMIN' | 'SUPER'
  roleAssignments: AdminRoleAssignmentDto[]
}

const admins = ref<AdminUserDto[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await axios.get<AdminUserDto[]>('/api/super/admins')
    admins.value = res.data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load admins'
  } finally {
    loading.value = false
  }
}

async function toggle(raId: number) {
  error.value = null
  try {
    await axios.post(`/api/super/admins/role-assignments/${raId}/toggle`)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Toggle failed'
  }
}

async function demote(user: AdminUserDto) {
  if (!confirm(`Demote ${user.email} to CREATOR? All their admin assignments will be disabled.`)) return
  error.value = null
  try {
    await axios.post(`/api/super/admins/${user.id}/demote`)
    message.value = `${user.email} demoted to CREATOR.`
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Demote failed'
  }
}

function accessBadgeClasses(access: 'ADMIN' | 'SUPER'): string {
  return access === 'ADMIN'
    ? 'bg-sky-200 text-sky-900'
    : 'bg-purple-200 text-purple-900'
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-5xl py-8">
    <h1 class="mb-4 text-2xl font-semibold text-slate-800">Manage Admins</h1>

    <div class="mb-4">
      <button
        @click="load"
        :disabled="loading"
        class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ loading ? 'Loading…' : 'Refresh' }}
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-700">{{ message }}</p>

    <p v-if="!loading && admins.length === 0" class="text-sm text-slate-500">No admins yet.</p>

    <div v-else class="flex flex-col gap-4">
      <article
        v-for="a in admins"
        :key="a.id"
        class="rounded-md border border-slate-200 p-4"
      >
        <header class="mb-3 flex items-center gap-3">
          <div class="flex-1">
            <strong class="font-semibold text-slate-800">{{ a.email }}</strong>
            <span class="ml-2 text-sm text-slate-500">{{ a.phone }}</span>
          </div>
          <div class="flex gap-2">
            <span
              :class="['rounded px-2 py-0.5 text-xs font-semibold', accessBadgeClasses(a.access)]"
            >{{ a.access }}</span>
            <span
              v-if="!a.isEnabled"
              class="rounded bg-red-100 px-2 py-0.5 text-xs text-red-900"
            >disabled</span>
          </div>
          <button
            v-if="a.access === 'ADMIN'"
            @click="demote(a)"
            class="rounded border border-red-700 bg-white px-3 py-1.5 text-sm text-red-700 hover:bg-red-50"
          >Demote to Creator</button>
        </header>

        <section>
          <h4 class="mb-2 text-sm font-semibold text-slate-700">Role Assignments</h4>
          <p v-if="a.roleAssignments.length === 0" class="text-sm text-slate-500">
            No zipcode assignments.
          </p>
          <table v-else class="w-full border-collapse text-sm">
            <thead>
              <tr class="bg-slate-50 text-left">
                <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">State</th>
                <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">County</th>
                <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Zipcode</th>
                <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Enabled</th>
                <th class="border-b border-slate-200 p-2"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="ra in a.roleAssignments" :key="ra.id">
                <td class="border-b border-slate-100 p-2">{{ ra.stateInitial }}</td>
                <td class="border-b border-slate-100 p-2">{{ ra.countyName }}</td>
                <td class="border-b border-slate-100 p-2 font-mono">{{ ra.zipcode }}</td>
                <td class="border-b border-slate-100 p-2">
                  <span
                    :class="[
                      'rounded px-2 py-0.5 text-xs',
                      ra.enabled ? 'bg-green-100 text-green-900' : 'bg-slate-200 text-slate-600'
                    ]"
                  >{{ ra.enabled ? 'on' : 'off' }}</span>
                </td>
                <td class="border-b border-slate-100 p-2">
                  <button
                    @click="toggle(ra.id)"
                    class="text-sm text-slate-800 underline"
                  >{{ ra.enabled ? 'Disable' : 'Enable' }}</button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </article>
    </div>
  </div>
</template>
