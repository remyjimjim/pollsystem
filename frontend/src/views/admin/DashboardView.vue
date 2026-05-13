<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()

interface AdminZipcodeScope {
  stateInitial: string
  countyName: string
  zipcode: string
}

interface CreatorRequestRow {
  id: number
  userEmail: string
  zipcodes: string[]
  pollTypeIds: number[]
  status: string
  submittedAt: string
  assignedAdminId: number | null
}

interface RecentDecisionRow {
  requestId: number
  userEmail: string
  zipcodes: string[]
  status: 'APPROVED' | 'REJECTED'
  processedAt: string | null
}

interface AdminDashboardDto {
  scope: AdminZipcodeScope[]
  pendingAssignedToMe: CreatorRequestRow[]
  unassignedInScope: CreatorRequestRow[]
  staleCount: number
  creatorsInScopeCount: number
  recentDecisions: RecentDecisionRow[]
}

const auth = useAuthStore()
const data = ref<AdminDashboardDto | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const acting = ref<number | null>(null)

const STALE_THRESHOLD_MS = 48 * 60 * 60 * 1000

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await axios.get<AdminDashboardDto>('/api/admin/dashboard')
    data.value = res.data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('admin.dashboard.errorLoad')
  } finally {
    loading.value = false
  }
}

async function act(id: number, path: 'batch-approve' | 'batch-reject') {
  acting.value = id
  error.value = null
  try {
    await axios.post(`/api/admin/creator-requests/${path}`, { requestIds: [id] })
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('admin.dashboard.errorAction')
  } finally {
    acting.value = null
  }
}

function relative(iso: string | null): string {
  if (!iso) return '—'
  const diffMs = Date.now() - new Date(iso).getTime()
  const minutes = Math.round(diffMs / 60_000)
  if (minutes < 60) return t('admin.dashboard.timeAgoMinutes', { n: minutes })
  const hours = Math.round(diffMs / 3_600_000)
  if (hours < 24) return t('admin.dashboard.timeAgoHours', { n: hours })
  const days = Math.round(diffMs / 86_400_000)
  return t('admin.dashboard.timeAgoDays', { n: days })
}

function isStale(iso: string): boolean {
  return Date.now() - new Date(iso).getTime() > STALE_THRESHOLD_MS
}

const scopeSummary = computed(() => {
  if (!data.value) return ''
  const zips = data.value.scope.map(s => s.zipcode)
  if (zips.length === 0) return t('admin.dashboard.noZipcodes')
  if (zips.length <= 4) return zips.join(', ')
  return `${zips.slice(0, 3).join(', ')} ${t('admin.dashboard.moreZips', { n: zips.length - 3 })}`
})

// Refetch when the page is restored from the browser's bfcache (e.g. user
// hits Back after flipping a decision in the detail view). Plain onMounted
// doesn't fire on bfcache restore, so the stale snapshot would otherwise
// stick around.
function onPageShow(e: PageTransitionEvent) {
  if (e.persisted) load()
}
onMounted(() => {
  load()
  window.addEventListener('pageshow', onPageShow)
})
onBeforeUnmount(() => {
  window.removeEventListener('pageshow', onPageShow)
})
</script>

<template>
  <div class="py-8">
    <header class="mb-6">
      <h1 class="text-2xl font-semibold text-slate-800">{{ $t('admin.dashboard.heading') }}</h1>
      <p class="mt-1 text-sm text-slate-600">
        <template v-if="auth.user">
          {{ $t('admin.dashboard.signedInAs') }} <strong>{{ auth.user.email }}</strong> ·
        </template>
        {{ $t('admin.dashboard.reviewingFor') }} <span class="font-mono">{{ scopeSummary }}</span>
      </p>
    </header>

    <p v-if="error" class="mb-4 text-sm text-red-700">{{ error }}</p>
    <p v-if="loading" class="text-sm text-slate-600">{{ $t('common.loading') }}</p>

    <template v-if="data">
      <!-- KPI cards -->
      <div class="mb-6 grid gap-4 sm:grid-cols-3">
        <article class="rounded-md border border-slate-200 bg-white p-4">
          <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">
            {{ $t('admin.dashboard.kpiPendingReview') }}
          </p>
          <p class="my-1 text-3xl font-semibold text-slate-800">
            {{ data.pendingAssignedToMe.length }}
          </p>
          <p class="text-xs text-slate-500">{{ $t('admin.dashboard.kpiAssignedToMe') }}</p>
        </article>
        <article class="rounded-md border border-slate-200 bg-white p-4">
          <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">
            {{ $t('admin.dashboard.kpiUnassignedInScope') }}
          </p>
          <p
            class="my-1 text-3xl font-semibold"
            :class="data.unassignedInScope.length > 0 ? 'text-orange-700' : 'text-slate-800'"
          >
            {{ data.unassignedInScope.length }}
          </p>
          <p class="text-xs text-slate-500">
            {{ $t('admin.dashboard.kpiClaimable') }}
            <template v-if="data.staleCount > 0">
              · <span class="font-semibold text-orange-700">{{ data.staleCount }} {{ $t('admin.dashboard.kpiStaleSuffix') }}</span>
            </template>
          </p>
        </article>
        <article class="rounded-md border border-slate-200 bg-white p-4">
          <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">
            {{ $t('admin.dashboard.kpiCreatorsInScope') }}
          </p>
          <p class="my-1 text-3xl font-semibold text-slate-800">
            {{ data.creatorsInScopeCount }}
          </p>
          <p class="text-xs text-slate-500">{{ $t('admin.dashboard.kpiInYourZipcodes') }}</p>
        </article>
      </div>

      <!-- Pending requests table -->
      <section class="mb-6">
        <div class="mb-2 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-slate-700">{{ $t('admin.dashboard.pendingHeading') }}</h2>
          <router-link to="/admin/creator-requests" class="text-sm text-slate-800 underline">
            {{ $t('admin.dashboard.viewAll', { n: data.pendingAssignedToMe.length + data.unassignedInScope.length }) }}
          </router-link>
        </div>
        <p
          v-if="data.pendingAssignedToMe.length === 0 && data.unassignedInScope.length === 0"
          class="text-sm text-slate-500"
        >
          {{ $t('admin.dashboard.noPending') }}
        </p>
        <table v-else class="w-full border-collapse text-sm">
          <thead>
            <tr class="bg-slate-50 text-left">
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colId') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colUser') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colZipcodes') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colPollTypes') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colSubmitted') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colActions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="r in [...data.pendingAssignedToMe, ...data.unassignedInScope]"
              :key="r.id"
              :class="isStale(r.submittedAt) ? 'bg-orange-50' : ''"
            >
              <td class="border-b border-slate-100 p-2 align-top font-mono">
                <router-link
                  :to="`/admin/creator-requests/${r.id}`"
                  class="text-slate-800 underline"
                >#{{ r.id }}</router-link>
              </td>
              <td class="border-b border-slate-100 p-2 align-top">{{ r.userEmail }}</td>
              <td class="border-b border-slate-100 p-2 align-top font-mono text-xs">
                {{ r.zipcodes.join(', ') }}
              </td>
              <td class="border-b border-slate-100 p-2 align-top">
                {{ r.pollTypeIds.join(', ') }}
              </td>
              <td class="border-b border-slate-100 p-2 align-top">
                {{ relative(r.submittedAt) }}
                <span
                  v-if="isStale(r.submittedAt)"
                  class="ml-1 rounded bg-orange-500 px-1.5 py-0.5 text-xs text-white"
                >{{ $t('admin.dashboard.badgeStale') }}</span>
                <span
                  v-if="r.assignedAdminId == null"
                  class="ml-1 rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-700"
                >{{ $t('admin.dashboard.badgeUnassigned') }}</span>
              </td>
              <td class="border-b border-slate-100 p-2 align-top">
                <div class="flex gap-2">
                  <button
                    @click="act(r.id, 'batch-approve')"
                    :disabled="acting === r.id"
                    class="rounded bg-green-700 px-2.5 py-1 text-xs text-white hover:bg-green-800 disabled:cursor-not-allowed disabled:opacity-50"
                    :title="$t('admin.dashboard.approveTitle')"
                  >{{ $t('admin.dashboard.approve') }}</button>
                  <button
                    @click="act(r.id, 'batch-reject')"
                    :disabled="acting === r.id"
                    class="rounded bg-red-700 px-2.5 py-1 text-xs text-white hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-50"
                    :title="$t('admin.dashboard.rejectTitle')"
                  >{{ $t('admin.dashboard.reject') }}</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Recent decisions audit log -->
      <section class="mb-6">
        <h2 class="mb-2 text-lg font-semibold text-slate-700">
          {{ $t('admin.dashboard.recentHeading') }}
          <span class="text-sm font-normal text-slate-500">{{ $t('admin.dashboard.recentSubtitle', { n: data.recentDecisions.length }) }}</span>
        </h2>
        <p v-if="data.recentDecisions.length === 0" class="text-sm text-slate-500">
          {{ $t('admin.dashboard.noDecisions') }}
        </p>
        <table v-else class="w-full border-collapse text-sm">
          <thead>
            <tr class="bg-slate-50 text-left">
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colWhen') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colAction') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colId') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colUser') }}</th>
              <th class="border-b border-slate-200 p-2 font-semibold text-slate-700">{{ $t('admin.dashboard.colZipcodes') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in data.recentDecisions" :key="d.requestId">
              <td class="border-b border-slate-100 p-2">{{ relative(d.processedAt) }}</td>
              <td class="border-b border-slate-100 p-2">
                <span
                  :class="[
                    'rounded px-2 py-0.5 text-xs',
                    d.status === 'APPROVED'
                      ? 'bg-green-100 text-green-900'
                      : 'bg-red-100 text-red-900'
                  ]"
                >{{ d.status }}</span>
              </td>
              <td class="border-b border-slate-100 p-2 font-mono">
                <router-link
                  :to="`/admin/creator-requests/${d.requestId}`"
                  class="text-slate-800 underline"
                >#{{ d.requestId }}</router-link>
              </td>
              <td class="border-b border-slate-100 p-2">{{ d.userEmail }}</td>
              <td class="border-b border-slate-100 p-2 font-mono text-xs">
                {{ d.zipcodes.join(', ') }}
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Quick links -->
      <section>
        <h2 class="mb-2 text-lg font-semibold text-slate-700">{{ $t('admin.dashboard.quickLinks') }}</h2>
        <div class="grid gap-3 sm:grid-cols-3">
          <router-link
            to="/admin/manage-creators"
            class="rounded-md border border-slate-200 bg-white p-4 no-underline transition-colors hover:border-slate-800"
          >
            <p class="font-semibold text-slate-800">{{ $t('admin.dashboard.qlManageCreatorsTitle') }}</p>
            <p class="text-xs text-slate-600">{{ $t('admin.dashboard.qlManageCreatorsDesc') }}</p>
          </router-link>
          <router-link
            to="/admin/manage-polls"
            class="rounded-md border border-slate-200 bg-white p-4 no-underline transition-colors hover:border-slate-800"
          >
            <p class="font-semibold text-slate-800">{{ $t('admin.dashboard.qlManagePollsTitle') }}</p>
            <p class="text-xs text-slate-600">{{ $t('admin.dashboard.qlManagePollsDesc') }}</p>
          </router-link>
          <router-link
            to="/admin-request"
            class="rounded-md border border-slate-200 bg-white p-4 no-underline transition-colors hover:border-slate-800"
          >
            <p class="font-semibold text-slate-800">{{ $t('admin.dashboard.qlRequestSuperTitle') }}</p>
            <p class="text-xs text-slate-600">{{ $t('admin.dashboard.qlRequestSuperDesc') }}</p>
          </router-link>
        </div>
      </section>
    </template>
  </div>
</template>
