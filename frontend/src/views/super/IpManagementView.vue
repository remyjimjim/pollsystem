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
  <div class="view">
    <h1>IP Allow / Deny Rules</h1>
    <p class="lead warn">
      <strong>Note:</strong> these rules are stored but not enforced.
      Wiring up the request filter is intentionally deferred to avoid
      locking yourself out in dev.
    </p>

    <form @submit.prevent="add" class="add-form">
      <label>
        Value (IP or CIDR)
        <input v-model="form.value" type="text" required maxlength="64" placeholder="e.g. 10.0.0.0/8" />
      </label>
      <label>
        Type
        <select v-model="form.type">
          <option value="ALLOW">ALLOW</option>
          <option value="DENY">DENY</option>
        </select>
      </label>
      <label>
        Note
        <input v-model="form.note" type="text" placeholder="optional" />
      </label>
      <label class="checkbox">
        <input v-model="form.enabled" type="checkbox" />
        Enabled
      </label>
      <button type="submit" class="primary">Add rule</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="success">{{ message }}</p>

    <p v-if="!loading && rules.length === 0" class="hint">No rules yet.</p>

    <table v-else-if="rules.length > 0" class="grid">
      <thead>
        <tr>
          <th>Value</th>
          <th>Type</th>
          <th>Note</th>
          <th>Enabled</th>
          <th>Created</th>
          <th>By</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rules" :key="r.id">
          <td class="ip">{{ r.value }}</td>
          <td>
            <span :class="['pill', `pill-${r.type}`]">{{ r.type }}</span>
          </td>
          <td>{{ r.note ?? '—' }}</td>
          <td>
            <span :class="['pill', r.enabled ? 'on' : 'off']">
              {{ r.enabled ? 'on' : 'off' }}
            </span>
          </td>
          <td>{{ new Date(r.createdAt).toLocaleString() }}</td>
          <td>{{ r.createdByEmail ?? '—' }}</td>
          <td class="actions">
            <button @click="toggle(r.id)" class="link">
              {{ r.enabled ? 'Disable' : 'Enable' }}
            </button>
            <button @click="remove(r.id)" class="link danger">Delete</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.view { padding: 2rem 0; max-width: 1100px; margin: 0 auto; }
h1 { color: #1a365d; margin-bottom: 0.5rem; }
.lead { color: #4a5568; margin-bottom: 1.5rem; }
.warn {
  background: #fffaf0;
  border: 1px solid #ed8936;
  padding: 0.75rem;
  border-radius: 6px;
}
.add-form {
  display: grid;
  grid-template-columns: 2fr 1fr 2fr auto auto;
  gap: 0.5rem;
  align-items: end;
  background: #f7fafc;
  padding: 1rem;
  border-radius: 6px;
  margin-bottom: 1rem;
}
label {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: #2d3748;
}
label.checkbox {
  flex-direction: row;
  align-items: center;
  gap: 0.4rem;
}
input,
select {
  padding: 0.4rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  font: inherit;
  font-weight: 400;
}
button {
  padding: 0.5rem 1rem;
  background: white;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  cursor: pointer;
}
button.primary {
  background: #1a365d;
  color: white;
  border-color: #1a365d;
}
.grid { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.grid th, .grid td {
  padding: 0.5rem;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
}
.grid th { background: #f7fafc; }
.ip { font-family: monospace; }
.pill {
  font-size: 0.75rem;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
}
.pill-ALLOW { background: #c6f6d5; color: #22543d; }
.pill-DENY { background: #fed7d7; color: #742a2a; }
.pill.on { background: #c6f6d5; color: #22543d; }
.pill.off { background: #edf2f7; color: #4a5568; }
.actions { display: flex; gap: 0.5rem; }
.link {
  background: none;
  border: none;
  color: #1a365d;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}
.link.danger { color: #c53030; }
.error { color: #c53030; }
.success { color: #2f855a; }
.hint { color: #718096; }
</style>
