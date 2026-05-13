<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface PollTypeAdminDto {
  id: number
  name: string
  pollType: number
  template: unknown
}

const types = ref<PollTypeAdminDto[]>([])
const editing = ref<number | null>(null)
const drafts = ref<Record<number, string>>({})
const errors = ref<Record<number, string>>({})
const messages = ref<Record<number, string>>({})

async function load() {
  try {
    const res = await axios.get<PollTypeAdminDto[]>('/api/super/poll-types')
    types.value = res.data
    drafts.value = {}
    errors.value = {}
    messages.value = {}
  } catch (e: any) {
    alert(e?.response?.data?.message ?? t('super.pollTemplates.loadFailed'))
  }
}

function startEdit(pt: PollTypeAdminDto) {
  drafts.value = {
    ...drafts.value,
    [pt.id]: JSON.stringify(pt.template, null, 2)
  }
  errors.value = { ...errors.value, [pt.id]: '' }
  messages.value = { ...messages.value, [pt.id]: '' }
  editing.value = pt.id
}

function cancel(pt: PollTypeAdminDto) {
  const next = { ...drafts.value }; delete next[pt.id]; drafts.value = next
  editing.value = null
}

async function save(pt: PollTypeAdminDto) {
  const draft = drafts.value[pt.id]
  if (draft == null) return
  let parsed: unknown
  try {
    parsed = JSON.parse(draft)
  } catch {
    errors.value = { ...errors.value, [pt.id]: t('super.pollTemplates.invalidJson') }
    return
  }
  try {
    await axios.put(`/api/super/poll-types/${pt.id}/template`, parsed)
    messages.value = { ...messages.value, [pt.id]: t('super.pollTemplates.saved') }
    errors.value = { ...errors.value, [pt.id]: '' }
    editing.value = null
    await load()
  } catch (e: any) {
    errors.value = {
      ...errors.value,
      [pt.id]: e?.response?.data?.message ?? t('super.pollTemplates.saveFailed')
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-4xl py-8">
    <h1 class="mb-2 text-2xl font-semibold text-slate-800">{{ $t('super.pollTemplates.heading') }}</h1>
    <p class="mb-6 text-sm text-slate-600">
      {{ $t('super.pollTemplates.intro') }}
    </p>

    <article
      v-for="pt in types"
      :key="pt.id"
      class="mb-4 rounded-md border border-slate-200 p-4"
    >
      <header class="mb-2 flex items-center gap-2">
        <strong class="font-semibold text-slate-800">{{ pt.name }}</strong>
        <span class="text-sm text-slate-500">{{ $t('super.pollTemplates.idCodeLabel', { id: pt.id, code: pt.pollType }) }}</span>
        <span class="flex-1" />
        <button
          v-if="editing !== pt.id"
          @click="startEdit(pt)"
          class="rounded bg-slate-800 px-3 py-1.5 text-sm text-white hover:bg-slate-900"
        >{{ $t('super.pollTemplates.edit') }}</button>
      </header>

      <pre
        v-if="editing !== pt.id"
        class="m-0 overflow-auto rounded bg-slate-900 p-3 text-xs text-slate-50"
      >{{ JSON.stringify(pt.template, null, 2) }}</pre>

      <div v-else class="flex flex-col gap-2">
        <textarea
          v-model="drafts[pt.id]"
          rows="20"
          spellcheck="false"
          class="resize-y rounded border border-slate-300 p-2 font-mono text-xs focus:border-slate-500 focus:outline-none"
        />
        <p v-if="errors[pt.id]" class="m-0 text-sm text-red-700">{{ errors[pt.id] }}</p>
        <div class="flex gap-2">
          <button
            @click="save(pt)"
            class="rounded bg-slate-800 px-3 py-1.5 text-sm text-white hover:bg-slate-900"
          >{{ $t('super.pollTemplates.save') }}</button>
          <button
            @click="cancel(pt)"
            class="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm hover:bg-slate-50"
          >{{ $t('super.pollTemplates.cancel') }}</button>
        </div>
      </div>

      <p v-if="messages[pt.id]" class="mt-2 text-sm text-green-700">{{ messages[pt.id] }}</p>
    </article>
  </div>
</template>
