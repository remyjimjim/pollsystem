<script setup lang="ts">
// The Super dashboard's Demo account card (decided 2026-10-28): one shared
// address per site, switched on for 12 hours at a time. While on, typing it on
// Sign in signs straight in as a member and creator. Ending it (or its time
// running out) archives its polls for the super who started it and deletes the
// account with its votes and comments; Start makes a fresh one.
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'

interface DemoStatus { email: string; on: boolean; until: string | null; zipcode: string | null; startedBy: string | null }

const { t } = useI18n()
const demo = ref<DemoStatus | null>(null)
const zipcode = ref('98225')
const busy = ref(false)
const error = ref<string | null>(null)

async function call(action: () => Promise<{ data: DemoStatus }>) {
  busy.value = true
  error.value = null
  try {
    demo.value = (await action()).data
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('super.dashboard.demo.error')
  } finally {
    busy.value = false
  }
}
const load = () => call(() => axios.get('/api/super/demo'))
const start = () => call(() => axios.post('/api/super/demo/start', { zipcode: zipcode.value.trim() }))
const extend = () => call(() => axios.post('/api/super/demo/extend'))
function end() {
  if (!window.confirm(t('super.dashboard.demo.confirmEnd'))) return
  call(() => axios.post('/api/super/demo/end'))
}
onMounted(load)
</script>

<template>
  <section
    class="mb-8 rounded-lg border p-5"
    :class="demo?.on ? 'border-amber-400 bg-amber-50' : 'border-slate-200 bg-white'"
    data-test="demo-card"
  >
    <h2 class="font-semibold text-slate-800">{{ $t('super.dashboard.demo.heading') }}</h2>
    <p class="m-0 mt-1 text-sm text-slate-600">{{ $t('super.dashboard.demo.blurb') }}</p>
    <p v-if="!demo" class="mt-2 text-sm text-slate-600">{{ $t('common.loading') }}</p>
    <template v-else>
      <p class="m-0 mt-2 text-sm text-slate-700">
        {{ $t('super.dashboard.demo.address') }} <strong class="font-mono">{{ demo.email }}</strong>
      </p>
      <p class="m-0 mt-1 text-sm" data-test="demo-status">
        <strong v-if="demo.on" class="text-amber-800">
          {{ $t('super.dashboard.demo.onUntil', { time: new Date(demo.until!).toLocaleString(), zipcode: demo.zipcode }) }}
        </strong>
        <strong v-else class="text-slate-700">{{ $t('super.dashboard.demo.off') }}</strong>
        <span v-if="demo.on && demo.startedBy" class="text-slate-600"> · {{ $t('super.dashboard.demo.startedBy', { email: demo.startedBy }) }}</span>
      </p>
      <div class="mt-3 flex flex-wrap items-end gap-2">
        <label v-if="!demo.on" class="flex flex-col gap-1 text-xs text-slate-600">
          {{ $t('super.dashboard.demo.zipcode') }}
          <input
            v-model="zipcode"
            inputmode="numeric"
            maxlength="5"
            data-test="demo-zipcode"
            class="w-24 rounded border border-slate-300 bg-white p-2 text-sm text-slate-900"
          />
        </label>
        <button v-if="!demo.on" type="button" :disabled="busy" @click="start" data-test="demo-start"
          class="rounded bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-900 disabled:opacity-60">
          {{ $t('super.dashboard.demo.start') }}
        </button>
        <template v-else>
          <button type="button" :disabled="busy" @click="extend" data-test="demo-extend"
            class="rounded border border-slate-400 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60">
            {{ $t('super.dashboard.demo.extend') }}
          </button>
          <button type="button" :disabled="busy" @click="end" data-test="demo-end"
            class="rounded bg-red-700 px-4 py-2 text-sm font-semibold text-white hover:bg-red-800 disabled:opacity-60">
            {{ $t('super.dashboard.demo.end') }}
          </button>
        </template>
      </div>
      <p v-if="error" class="mt-2 text-xs text-red-700" role="alert">{{ error }}</p>
    </template>
  </section>
</template>
