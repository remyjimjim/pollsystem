<script setup lang="ts">
// The Super dashboard's Substack members card (decided 2026-10-28): paid
// Substack subscribers become members through the Substack Stripe account's
// webhook; Re-sync compares against its subscriptions (it also runs every 5
// minutes). Errors show here, in the backend log's format.
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'

interface Entry { id: number; at: string; source: string; email: string | null; action: string; ok: boolean; message: string | null }
interface Status { webhookConfigured: boolean; resyncConfigured: boolean; members: number; recent: Entry[] }
interface Result { checked: number; activated: number; renewed: number; ended: number; errors: string[] }

const { t } = useI18n()
const status = ref<Status | null>(null)
const result = ref<Result | null>(null)
const busy = ref(false)
const error = ref<string | null>(null)

async function load() {
  try { status.value = (await axios.get<Status>('/api/super/substack')).data }
  catch (e: any) { error.value = e?.response?.data?.message ?? t('super.dashboard.substack.loadFailed') }
}
async function resync() {
  busy.value = true
  error.value = null
  result.value = null
  try {
    result.value = (await axios.post<Result>('/api/super/substack/resync')).data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.dashboard.substack.resyncFailed')
  } finally {
    busy.value = false
    await load()
  }
}
onMounted(load)
</script>

<template>
  <section class="mb-8 rounded-lg border border-slate-200 bg-white p-5" data-test="substack-card">
    <h2 class="font-semibold text-slate-800">{{ $t('super.dashboard.substack.heading') }}</h2>
    <p class="m-0 mt-1 text-sm text-slate-600">{{ $t('super.dashboard.substack.blurb') }}</p>
    <p v-if="!status && !error" class="mt-2 text-sm text-slate-600">{{ $t('common.loading') }}</p>
    <template v-if="status">
      <ul class="m-0 mt-2 list-none p-0 text-sm text-slate-700" data-test="substack-status">
        <li>{{ $t('super.dashboard.substack.webhook') }}:
          <strong :class="status.webhookConfigured ? 'text-green-700' : 'text-orange-700'">{{ status.webhookConfigured ? $t('super.dashboard.substack.set') : $t('super.dashboard.substack.notSet') }}</strong></li>
        <li>{{ $t('super.dashboard.substack.resyncKey') }}:
          <strong :class="status.resyncConfigured ? 'text-green-700' : 'text-orange-700'">{{ status.resyncConfigured ? $t('super.dashboard.substack.set') : $t('super.dashboard.substack.notSet') }}</strong></li>
        <li>{{ $t('super.dashboard.substack.members') }}: <strong>{{ status.members }}</strong></li>
      </ul>
      <button type="button" :disabled="busy" @click="resync" data-test="substack-resync"
        class="mt-3 rounded bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-900 disabled:opacity-60">
        {{ busy ? $t('super.dashboard.substack.resyncing') : $t('super.dashboard.substack.resync') }}
      </button>
    </template>
    <p v-if="result" class="m-0 mt-2 text-sm text-slate-700" data-test="substack-result">
      {{ $t('super.dashboard.substack.resultLine', { checked: result.checked, activated: result.activated, renewed: result.renewed, ended: result.ended }) }}
    </p>
    <ul v-if="result?.errors.length" class="m-0 mt-1 list-none p-0" data-test="substack-errors">
      <li v-for="(line, i) in result.errors" :key="i" class="font-mono text-xs text-red-700">{{ line }}</li>
    </ul>
    <p v-if="error" class="mt-2 text-sm text-red-700" role="alert">{{ error }}</p>

    <template v-if="status?.recent.length">
      <h3 class="mb-1 mt-4 text-sm font-semibold text-slate-700">{{ $t('super.dashboard.substack.recent') }}</h3>
      <ul class="m-0 max-h-72 list-none overflow-y-auto p-0 text-xs" data-test="substack-recent">
        <li v-for="e in status.recent" :key="e.id" class="border-t border-slate-100 py-1" :class="e.ok ? 'text-slate-700' : 'text-red-700'">
          <span class="text-slate-500">{{ new Date(e.at).toLocaleString() }}</span>
          · {{ e.source }} · <strong>{{ e.action }}</strong>
          <template v-if="e.email"> · {{ e.email }}</template>
          <div v-if="e.message" class="break-words" :class="e.ok ? '' : 'font-mono'">{{ e.message }}</div>
        </li>
      </ul>
    </template>
  </section>
</template>
