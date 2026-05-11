<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import axios from 'axios'

interface IpRuleDto {
  id: number
  value: string
  type: 'ALLOW' | 'DENY'
  note: string | null
  enabled: boolean
  createdAt: string
  createdByEmail: string | null
}

const rules = ref<IpRuleDto[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)

const form = reactive({
  value: '',
  type: 'ALLOW' as 'ALLOW' | 'DENY',
  note: '',
  enabled: true
})

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await axios.get<IpRuleDto[]>('/api/super/ip-rules')
    rules.value = res.data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Failed to load rules'
  } finally {
    loading.value = false
  }
}

async function add() {
  error.value = null
  message.value = null
  try {
    await axios.post('/api/super/ip-rules', {
      value: form.value.trim(),
      type: form.type,
      note: form.note.trim() || null,
      enabled: form.enabled
    })
    form.value = ''
    form.note = ''
    message.value = 'Rule added.'
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Add failed'
  }
}

async function toggle(id: number) {
  error.value = null
  try {
    await axios.post(`/api/super/ip-rules/${id}/toggle`)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Toggle failed'
  }
}

async function remove(id: number) {
  if (!confirm('Delete this rule?')) return
  error.value = null
  try {
    await axios.delete(`/api/super/ip-rules/${id}`)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? 'Delete failed'
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-6xl py-8">
    <h1 class="mb-2 text-2xl font-semibold text-slate-800">IP Allow / Deny Rules</h1>
    <p class="mb-6 rounded-md border border-orange-400 bg-orange-50 p-3 text-sm text-orange-900">
      <strong class="font-semibold">Note:</strong>
      these rules are stored but not enforced. Wiring up the request filter is
      intentionally deferred to avoid locking yourself out in dev.
    </p>

    <form
      @submit.prevent="add"
      class="mb-4 grid grid-cols-[2fr_1fr_2fr_auto_auto] items-end gap-2 rounded-md bg-slate-50 p-4"
    >
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        Value (IP or CIDR)
        <input
          v-model="form.value"
          type="text"
          required
          maxlength="64"
          placeholder="e.g. 10.0.0.0/8"
          class="rounded border border-slate-300 p-2 text-sm font-normal focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        Type
        <select
          v-model="form.type"
          class="rounded border border-slate-300 p-2 text-sm font-normal focus:border-slate-500 focus:outline-none"
        >
          <option value="ALLOW">ALLOW</option>
          <option value="DENY">DENY</option>
        </select>
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        Note
        <input
          v-model="form.note"
          type="text"
          placeholder="optional"
          class="rounded border border-slate-300 p-2 text-sm font-normal focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex items-center gap-2 text-xs font-semibold text-slate-700">
        <input v-model="form.enabled" type="checkbox" />
        Enabled
      </label>
      <button
        type="submit"
        class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900"
      >
        Add rule
      </button>
    </form>

    <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-700">{{ message }}</p>

    <p v-if="!loading && rules.length === 0" class="text-sm text-slate-500">No rules yet.</p>

    <table v-else-if="rules.length > 0" class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Value</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Type</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Note</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Enabled</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">Created</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">By</th>
          <th class="border-b border-slate-200 p-2"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rules" :key="r.id">
          <td class="border-b border-slate-100 p-2 font-mono">{{ r.value }}</td>
          <td class="border-b border-slate-100 p-2">
            <span
              :class="[
                'rounded px-2 py-0.5 text-xs',
                r.type === 'ALLOW' ? 'bg-green-100 text-green-900' : 'bg-red-100 text-red-900'
              ]"
            >{{ r.type }}</span>
          </td>
          <td class="border-b border-slate-100 p-2">{{ r.note ?? '—' }}</td>
          <td class="border-b border-slate-100 p-2">
            <span
              :class="[
                'rounded px-2 py-0.5 text-xs',
                r.enabled ? 'bg-green-100 text-green-900' : 'bg-slate-200 text-slate-600'
              ]"
            >{{ r.enabled ? 'on' : 'off' }}</span>
          </td>
          <td class="border-b border-slate-100 p-2">
            {{ new Date(r.createdAt).toLocaleString() }}
          </td>
          <td class="border-b border-slate-100 p-2">{{ r.createdByEmail ?? '—' }}</td>
          <td class="border-b border-slate-100 p-2">
            <div class="flex gap-3">
              <button
                @click="toggle(r.id)"
                class="text-slate-800 underline"
              >{{ r.enabled ? 'Disable' : 'Enable' }}</button>
              <button
                @click="remove(r.id)"
                class="text-red-700 underline"
              >Delete</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
