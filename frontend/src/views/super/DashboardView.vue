<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface AdminWorkloadRow {
  adminId: number
  email: string
  pending: number
  stale: number
  percentOfTotal: number
  warn: boolean
}
interface AdminWorkloadDto {
  totalPending: number
  warnThreshold: number
  rows: AdminWorkloadRow[]
}

const load = ref<AdminWorkloadDto | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

async function fetchLoad() {
  loading.value = true
  error.value = null
  try {
    const res = await axios.get<AdminWorkloadDto>('/api/super/admin-load')
    load.value = res.data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.dashboard.errorLoad')
  } finally {
    loading.value = false
  }
}

function pct(share: number): string {
  return `${(share * 100).toFixed(1)}%`
}

onMounted(fetchLoad)
</script>

<template>
  <div class="mx-auto max-w-4xl py-8">
    <h1 class="mb-2 text-2xl font-semibold text-slate-800">{{ $t('super.dashboard.heading') }}</h1>
    <p class="mb-6 text-slate-600">{{ $t('super.dashboard.subtitle') }}</p>

    <div class="mb-8 grid gap-4 sm:grid-cols-[repeat(auto-fit,minmax(220px,1fr))]">
      <router-link
        to="/super/admin-requests"
        class="block rounded-lg border border-slate-200 bg-white p-5 no-underline transition-colors hover:border-slate-800"
      >
        <h3 class="mb-1 font-semibold text-slate-800">{{ $t('super.dashboard.linksAdminRequestsTitle') }}</h3>
        <p class="m-0 text-sm text-slate-600">
          {{ $t('super.dashboard.linksAdminRequestsDesc') }}
        </p>
      </router-link>
      <router-link
        to="/super/manage-admins"
        class="block rounded-lg border border-slate-200 bg-white p-5 no-underline transition-colors hover:border-slate-800"
      >
        <h3 class="mb-1 font-semibold text-slate-800">{{ $t('super.dashboard.linksManageAdminsTitle') }}</h3>
        <p class="m-0 text-sm text-slate-600">
          {{ $t('super.dashboard.linksManageAdminsDesc') }}
        </p>
      </router-link>
      <router-link
        to="/super/poll-templates"
        class="block rounded-lg border border-slate-200 bg-white p-5 no-underline transition-colors hover:border-slate-800"
      >
        <h3 class="mb-1 font-semibold text-slate-800">{{ $t('super.dashboard.linksPollTemplatesTitle') }}</h3>
        <p class="m-0 text-sm text-slate-600">
          {{ $t('super.dashboard.linksPollTemplatesDesc') }}
        </p>
      </router-link>
      <router-link
        to="/super/ip-management"
        class="block rounded-lg border border-slate-200 bg-white p-5 no-underline transition-colors hover:border-slate-800"
      >
        <h3 class="mb-1 font-semibold text-slate-800">{{ $t('super.dashboard.linksIpManagementTitle') }}</h3>
        <p class="m-0 text-sm text-slate-600">{{ $t('super.dashboard.linksIpManagementDesc') }}</p>
      </router-link>
    </div>

    <section>
      <div class="mb-2 flex items-center justify-between">
        <h2 class="text-lg font-semibold text-slate-700">{{ $t('super.dashboard.workloadHeading') }}</h2>
        <button
          @click="fetchLoad"
          :disabled="loading"
          class="rounded border border-slate-300 bg-white px-3 py-1 text-xs hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {{ loading ? $t('common.loading') : $t('super.dashboard.refresh') }}
        </button>
      </div>

      <p v-if="error" class="text-sm text-red-700">{{ error }}</p>

      <template v-if="load">
        <p class="mb-2 text-sm text-slate-600">
          {{ $t('super.dashboard.totalOutstanding') }}
          <strong class="font-semibold text-slate-800">{{ load.totalPending }}</strong>
        </p>

        <p v-if="load.rows.length === 0" class="text-sm text-slate-500">
          {{ $t('super.dashboard.noAssigned') }}
        </p>
        <table v-else class="w-full border-collapse text-sm">
          <thead>
            <tr class="bg-slate-50 text-left">
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.dashboard.colAdmin') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('super.dashboard.colPending') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">
                {{ $t('super.dashboard.colPctTotal') }}
              </th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">
                {{ $t('super.dashboard.colStale') }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in load.rows" :key="row.adminId">
              <td class="border-b border-slate-100 p-2">{{ row.email }}</td>
              <td class="border-b border-slate-100 p-2 font-mono">{{ row.pending }}</td>
              <td class="border-b border-slate-100 p-2">
                <span :class="row.warn ? 'font-semibold text-orange-700' : 'text-slate-700'">
                  {{ pct(row.percentOfTotal) }}
                </span>
                <span
                  v-if="row.warn"
                  class="ml-1 rounded bg-orange-500 px-1.5 py-0.5 text-xs text-white"
                  :title="t('super.dashboard.warnTitle', { threshold: pct(load.warnThreshold) })"
                >⚠</span>
              </td>
              <td class="border-b border-slate-100 p-2 font-mono">{{ row.stale }}</td>
            </tr>
          </tbody>
        </table>
        <p class="mt-2 text-xs text-slate-500">
          {{ $t('super.dashboard.footnote', { threshold: pct(load.warnThreshold), key: 'app.super.workload-warn-threshold' }) }}
        </p>
      </template>
    </section>
  </div>
</template>
