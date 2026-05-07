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
  <div class="view">
    <h1>Poll Type Templates</h1>
    <p class="lead">
      Edit the JSON template that defines what fields each poll type expects.
      Validation in the backend services is currently imperative — these templates
      are advisory until a schema-driven validator is wired up.
    </p>

    <article v-for="t in types" :key="t.id" class="card">
      <header>
        <strong>{{ t.name }}</strong>
        <span class="muted">id={{ t.id }}, code={{ t.pollType }}</span>
        <span class="spacer" />
        <button v-if="editing !== t.id" @click="startEdit(t)" class="primary">Edit</button>
      </header>

      <pre v-if="editing !== t.id" class="json">{{ JSON.stringify(t.template, null, 2) }}</pre>

      <div v-else class="editor">
        <textarea v-model="drafts[t.id]" rows="20" spellcheck="false" />
        <p v-if="errors[t.id]" class="error">{{ errors[t.id] }}</p>
        <div class="row">
          <button @click="save(t)" class="primary">Save</button>
          <button @click="cancel(t)">Cancel</button>
        </div>
      </div>

      <p v-if="messages[t.id]" class="success">{{ messages[t.id] }}</p>
    </article>
  </div>
</template>

<style scoped>
.view { padding: 2rem 0; max-width: 900px; margin: 0 auto; }
h1 { color: #1a365d; margin-bottom: 0.5rem; }
.lead { color: #4a5568; margin-bottom: 1.5rem; font-size: 0.9rem; }
.card {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1rem;
  margin-bottom: 1rem;
}
.card header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}
.muted { color: #718096; font-size: 0.85rem; }
.spacer { flex: 1; }
.json {
  background: #1a202c;
  color: #f7fafc;
  padding: 0.75rem;
  border-radius: 4px;
  font-size: 0.85rem;
  overflow: auto;
  margin: 0;
}
.editor {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
textarea {
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  resize: vertical;
}
.row { display: flex; gap: 0.5rem; }
button {
  padding: 0.4rem 0.9rem;
  background: white;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  cursor: pointer;
}
button.primary { background: #1a365d; color: white; border-color: #1a365d; }
.error { color: #c53030; margin: 0; font-size: 0.9rem; }
.success { color: #2f855a; margin-top: 0.5rem; font-size: 0.9rem; }
</style>
