<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'

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
    alert(e?.response?.data?.message ?? 'Failed to load poll types')
  }
}

function startEdit(t: PollTypeAdminDto) {
  drafts.value = {
    ...drafts.value,
    [t.id]: JSON.stringify(t.template, null, 2)
  }
  errors.value = { ...errors.value, [t.id]: '' }
  messages.value = { ...messages.value, [t.id]: '' }
  editing.value = t.id
}

function cancel(t: PollTypeAdminDto) {
  const next = { ...drafts.value }; delete next[t.id]; drafts.value = next
  editing.value = null
}

async function save(t: PollTypeAdminDto) {
  const draft = drafts.value[t.id]
  if (draft == null) return
  let parsed: unknown
  try {
    parsed = JSON.parse(draft)
  } catch {
    errors.value = { ...errors.value, [t.id]: 'Invalid JSON' }
    return
  }
  try {
    await axios.put(`/api/super/poll-types/${t.id}/template`, parsed)
    messages.value = { ...messages.value, [t.id]: 'Saved.' }
    errors.value = { ...errors.value, [t.id]: '' }
    editing.value = null
    await load()
  } catch (e: any) {
    errors.value = {
      ...errors.value,
      [t.id]: e?.response?.data?.message ?? 'Save failed'
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="mx-auto max-w-4xl py-8">
    <h1 class="mb-2 text-2xl font-semibold text-slate-800">Poll Type Templates</h1>
    <p class="mb-6 text-sm text-slate-600">
      Edit the JSON template that defines what fields each poll type expects.
      Validation in the backend services is currently imperative — these templates
      are advisory until a schema-driven validator is wired up.
    </p>

    <article
      v-for="t in types"
      :key="t.id"
      class="mb-4 rounded-md border border-slate-200 p-4"
    >
      <header class="mb-2 flex items-center gap-2">
        <strong class="font-semibold text-slate-800">{{ t.name }}</strong>
        <span class="text-sm text-slate-500">id={{ t.id }}, code={{ t.pollType }}</span>
        <span class="flex-1" />
        <button
          v-if="editing !== t.id"
          @click="startEdit(t)"
          class="rounded bg-slate-800 px-3 py-1.5 text-sm text-white hover:bg-slate-900"
        >Edit</button>
      </header>

      <pre
        v-if="editing !== t.id"
        class="m-0 overflow-auto rounded bg-slate-900 p-3 text-xs text-slate-50"
      >{{ JSON.stringify(t.template, null, 2) }}</pre>

      <div v-else class="flex flex-col gap-2">
        <textarea
          v-model="drafts[t.id]"
          rows="20"
          spellcheck="false"
          class="resize-y rounded border border-slate-300 p-2 font-mono text-xs focus:border-slate-500 focus:outline-none"
        />
        <p v-if="errors[t.id]" class="m-0 text-sm text-red-700">{{ errors[t.id] }}</p>
        <div class="flex gap-2">
          <button
            @click="save(t)"
            class="rounded bg-slate-800 px-3 py-1.5 text-sm text-white hover:bg-slate-900"
          >Save</button>
          <button
            @click="cancel(t)"
            class="rounded border border-slate-300 bg-white px-3 py-1.5 text-sm hover:bg-slate-50"
          >Cancel</button>
        </div>
      </div>

      <p v-if="messages[t.id]" class="mt-2 text-sm text-green-700">{{ messages[t.id] }}</p>
    </article>
  </div>
</template>
