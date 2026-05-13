<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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
    error.value = e?.response?.data?.message ?? t('super.ipManagement.errorLoad')
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
    message.value = t('super.ipManagement.ruleAdded')
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.ipManagement.errorAdd')
  }
}

async function toggle(id: number) {
  error.value = null
  try {
    await axios.post(`/api/super/ip-rules/${id}/toggle`)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.ipManagement.errorToggle')
  }
}

async function remove(id: number) {
  if (!confirm(t('super.ipManagement.deleteConfirm'))) return
  error.value = null
  try {
    await axios.delete(`/api/super/ip-rules/${id}`)
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.ipManagement.errorDelete')
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-6xl py-8">
    <h1 class="mb-2 text-2xl font-semibold text-slate-800">{{ $t('super.ipManagement.heading') }}</h1>
    <p class="mb-6 rounded-md border border-orange-400 bg-orange-50 p-3 text-sm text-orange-900">
      <strong class="font-semibold">{{ $t('super.ipManagement.note') }}</strong>
      {{ $t('super.ipManagement.noteBody') }}
    </p>

    <form
      @submit.prevent="add"
      class="mb-4 grid grid-cols-[2fr_1fr_2fr_auto_auto] items-end gap-2 rounded-md bg-slate-50 p-4"
    >
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        {{ $t('super.ipManagement.valueLabel') }}
        <input
          v-model="form.value"
          type="text"
          required
          maxlength="64"
          :placeholder="$t('super.ipManagement.valuePlaceholder')"
          class="rounded border border-slate-300 p-2 text-sm font-normal focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        {{ $t('super.ipManagement.typeLabel') }}
        <select
          v-model="form.type"
          class="rounded border border-slate-300 p-2 text-sm font-normal focus:border-slate-500 focus:outline-none"
        >
          <option value="ALLOW">ALLOW</option>
          <option value="DENY">DENY</option>
        </select>
      </label>
      <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
        {{ $t('super.ipManagement.noteLabel') }}
        <input
          v-model="form.note"
          type="text"
          :placeholder="$t('super.ipManagement.noteOptional')"
          class="rounded border border-slate-300 p-2 text-sm font-normal focus:border-slate-500 focus:outline-none"
        />
      </label>
      <label class="flex items-center gap-2 text-xs font-semibold text-slate-700">
        <input v-model="form.enabled" type="checkbox" />
        {{ $t('super.ipManagement.enabledLabel') }}
      </label>
      <button
        type="submit"
        class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900"
      >
        {{ $t('super.ipManagement.addRule') }}
      </button>
    </form>

    <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-700">{{ message }}</p>

    <p v-if="!loading && rules.length === 0" class="text-sm text-slate-500">{{ $t('super.ipManagement.none') }}</p>

    <table v-else-if="rules.length > 0" class="w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.ipManagement.colValue') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.ipManagement.colType') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.ipManagement.colNote') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.ipManagement.colEnabled') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.ipManagement.colCreated') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.ipManagement.colBy') }}</th>
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
          <td class="border-b border-slate-100 p-2">{{ r.note ?? $t('super.ipManagement.dashLabel') }}</td>
          <td class="border-b border-slate-100 p-2">
            <span
              :class="[
                'rounded px-2 py-0.5 text-xs',
                r.enabled ? 'bg-green-100 text-green-900' : 'bg-slate-200 text-slate-600'
              ]"
            >{{ r.enabled ? $t('super.ipManagement.on') : $t('super.ipManagement.off') }}</span>
          </td>
          <td class="border-b border-slate-100 p-2">
            {{ new Date(r.createdAt).toLocaleString() }}
          </td>
          <td class="border-b border-slate-100 p-2">{{ r.createdByEmail ?? $t('super.ipManagement.dashLabel') }}</td>
          <td class="border-b border-slate-100 p-2">
            <div class="flex gap-3">
              <button
                @click="toggle(r.id)"
                class="text-slate-800 underline"
              >{{ r.enabled ? $t('super.ipManagement.disable') : $t('super.ipManagement.enable') }}</button>
              <button
                @click="remove(r.id)"
                class="text-red-700 underline"
              >{{ $t('super.ipManagement.deleteAction') }}</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
