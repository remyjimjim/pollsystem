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

onMounted(load)
</script>

<template>
  <div class="view">
    <h1>Manage Admins</h1>

    <div class="bar">
      <button @click="load" :disabled="loading">
        {{ loading ? 'Loading…' : 'Refresh' }}
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="success">{{ message }}</p>

    <p v-if="!loading && admins.length === 0" class="hint">No admins yet.</p>

    <div v-else class="admins">
      <article v-for="a in admins" :key="a.id" class="card">
        <header>
          <div>
            <strong>{{ a.email }}</strong>
            <span class="phone">{{ a.phone }}</span>
          </div>
          <div class="badges">
            <span :class="['access', `access-${a.access}`]">{{ a.access }}</span>
            <span v-if="!a.isEnabled" class="badge disabled">disabled</span>
          </div>
          <button
            v-if="a.access === 'ADMIN'"
            @click="demote(a)"
            class="demote"
          >Demote to Creator</button>
        </header>

        <section>
          <h4>Role Assignments</h4>
          <p v-if="a.roleAssignments.length === 0" class="hint">No zipcode assignments.</p>
          <table v-else class="grid">
            <thead>
              <tr>
                <th>State</th>
                <th>County</th>
                <th>Zipcode</th>
                <th>Enabled</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="ra in a.roleAssignments" :key="ra.id">
                <td>{{ ra.stateInitial }}</td>
                <td>{{ ra.countyName }}</td>
                <td class="zip">{{ ra.zipcode }}</td>
                <td>
                  <span :class="['pill', ra.enabled ? 'on' : 'off']">
                    {{ ra.enabled ? 'on' : 'off' }}
                  </span>
                </td>
                <td>
                  <button @click="toggle(ra.id)" class="link">
                    {{ ra.enabled ? 'Disable' : 'Enable' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </article>
    </div>
  </div>
</template>

<style scoped>
.view { padding: 2rem 0; max-width: 1000px; margin: 0 auto; }
h1 { margin-bottom: 1rem; color: #1a365d; }
.bar { margin-bottom: 1rem; }
button {
  padding: 0.5rem 1rem;
  background: white;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
  cursor: pointer;
}
button:disabled { opacity: 0.6; cursor: not-allowed; }
.admins {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.card {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1rem;
}
.card header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}
.card header > div:first-child { flex: 1; }
.phone { color: #718096; margin-left: 0.5rem; font-size: 0.9rem; }
.badges { display: flex; gap: 0.4rem; }
.access {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
}
.access-ADMIN { background: #bee3f8; color: #2c5282; }
.access-SUPER { background: #d6bcfa; color: #553c9a; }
.badge.disabled {
  font-size: 0.75rem;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  background: #fed7d7;
  color: #742a2a;
}
.demote {
  background: white;
  border-color: #c53030;
  color: #c53030;
}
h4 { margin: 0 0 0.5rem; color: #2d3748; font-size: 0.95rem; }
.grid { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.grid th, .grid td {
  padding: 0.4rem 0.5rem;
  border-bottom: 1px solid #edf2f7;
  text-align: left;
}
.grid th { background: #f7fafc; }
.zip { font-family: monospace; }
.pill {
  font-size: 0.75rem;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}
.pill.on { background: #c6f6d5; color: #22543d; }
.pill.off { background: #edf2f7; color: #4a5568; }
.link {
  background: none;
  border: none;
  color: #1a365d;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}
.error { color: #c53030; }
.success { color: #2f855a; }
.hint { color: #718096; }
</style>
