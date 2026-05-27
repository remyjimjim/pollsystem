<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

type PollType = 'questionnaire' | 'election' | 'ballot-measure'
type PollStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'

interface SuperPollRow {
  id: number
  type: PollType
  title: string
  summary: string | null
  status: PollStatus
  creatorEmail: string
  closeDate: string | null
}

interface EditDraft {
  title: string
  summary: string
  closeDate: string
}

const polls = ref<SuperPollRow[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const editingKey = ref<string | null>(null)
const editDraft = reactive<EditDraft>({ title: '', summary: '', closeDate: '' })

function rowKey(p: SuperPollRow): string {
  return `${p.type}-${p.id}`
}

function toLocalInput(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}`
  )
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await axios.get<SuperPollRow[]>('/api/super/polls')
    polls.value = res.data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.managePolls.errorLoad')
  } finally {
    loading.value = false
  }
}

function startEdit(p: SuperPollRow) {
  editingKey.value = rowKey(p)
  editDraft.title = p.title
  editDraft.summary = p.summary ?? ''
  editDraft.closeDate = toLocalInput(p.closeDate)
  message.value = null
  error.value = null
}

function cancelEdit() {
  editingKey.value = null
}

async function saveEdit(p: SuperPollRow) {
  if (!editDraft.title.trim()) {
    error.value = t('super.managePolls.errorTitleBlank')
    return
  }
  saving.value = true
  error.value = null
  message.value = null
  try {
    const body = {
      title: editDraft.title.trim(),
      summary: p.type === 'election' ? null : editDraft.summary.trim(),
      closeDate: editDraft.closeDate ? new Date(editDraft.closeDate).toISOString() : null
    }
    await axios.put(`/api/super/polls/${p.type}/${p.id}`, body)
    message.value = t('super.managePolls.saved', { title: editDraft.title.trim() })
    editingKey.value = null
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.managePolls.errorSave')
  } finally {
    saving.value = false
  }
}

function statusClasses(status: PollStatus): string {
  switch (status) {
    case 'DRAFT': return 'bg-slate-200 text-slate-700'
    case 'PUBLISHED': return 'bg-emerald-200 text-emerald-900'
    case 'CLOSED': return 'bg-orange-200 text-orange-900'
    case 'ARCHIVED': return 'bg-rose-200 text-rose-900'
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-6xl py-8">
    <h1 class="mb-2 text-2xl font-semibold text-slate-800">{{ $t('super.managePolls.heading') }}</h1>
    <p class="mb-4 text-sm text-slate-600">{{ $t('super.managePolls.subtitle') }}</p>

    <div class="mb-4">
      <button
        @click="load"
        :disabled="loading"
        class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ loading ? $t('common.loading') : $t('super.managePolls.refresh') }}
      </button>
    </div>

    <p v-if="error" class="text-sm text-red-700">{{ error }}</p>
    <p v-if="message" class="text-sm text-green-700">{{ message }}</p>

    <p v-if="!loading && polls.length === 0" class="text-sm text-slate-500">
      {{ $t('super.managePolls.none') }}
    </p>

    <table v-if="polls.length > 0" class="mt-2 w-full border-collapse text-sm">
      <thead>
        <tr class="bg-slate-50 text-left">
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.managePolls.colType') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.managePolls.colTitle') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.managePolls.colCreator') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.managePolls.colStatus') }}</th>
          <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.managePolls.colCloseDate') }}</th>
          <th class="border-b border-slate-200 p-2"></th>
        </tr>
      </thead>
      <tbody>
        <template v-for="p in polls" :key="rowKey(p)">
          <tr v-if="editingKey !== rowKey(p)">
            <td class="border-b border-slate-100 p-2 font-mono text-xs uppercase">{{ p.type }}</td>
            <td class="border-b border-slate-100 p-2">{{ p.title }}</td>
            <td class="border-b border-slate-100 p-2 text-slate-600">{{ p.creatorEmail }}</td>
            <td class="border-b border-slate-100 p-2">
              <span :class="['inline-block rounded px-2 py-0.5 text-xs', statusClasses(p.status)]">
                {{ p.status }}
              </span>
            </td>
            <td class="border-b border-slate-100 p-2">
              {{ p.closeDate ? new Date(p.closeDate).toLocaleString() : '—' }}
            </td>
            <td class="border-b border-slate-100 p-2 text-right">
              <button
                @click="startEdit(p)"
                class="text-slate-800 underline hover:text-slate-900"
              >{{ $t('super.managePolls.edit') }}</button>
            </td>
          </tr>
          <tr v-else>
            <td colspan="6" class="border-b border-slate-200 bg-slate-50 p-4">
              <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
                  {{ $t('super.managePolls.fieldTitle') }}
                  <input
                    v-model="editDraft.title"
                    type="text"
                    maxlength="500"
                    class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
                  />
                </label>
                <label class="flex flex-col gap-1 text-xs font-semibold text-slate-700">
                  {{ $t('super.managePolls.fieldCloseDate') }}
                  <input
                    v-model="editDraft.closeDate"
                    type="datetime-local"
                    class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
                  />
                </label>
                <label
                  v-if="p.type !== 'election'"
                  class="flex flex-col gap-1 text-xs font-semibold text-slate-700 sm:col-span-2"
                >
                  {{ $t('super.managePolls.fieldSummary') }}
                  <textarea
                    v-model="editDraft.summary"
                    rows="3"
                    class="rounded border border-slate-300 p-2 text-sm font-normal text-slate-900 focus:border-slate-500 focus:outline-none"
                  />
                </label>
              </div>
              <p class="mt-2 text-xs text-slate-500">
                {{ $t('super.managePolls.safeFieldsNote') }}
              </p>
              <div class="mt-3 flex justify-end gap-2">
                <button
                  @click="cancelEdit"
                  :disabled="saving"
                  class="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {{ $t('super.managePolls.cancel') }}
                </button>
                <button
                  @click="saveEdit(p)"
                  :disabled="saving"
                  class="rounded bg-slate-800 px-3 py-1.5 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {{ saving ? $t('super.managePolls.saving') : $t('super.managePolls.save') }}
                </button>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>
