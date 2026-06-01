<script setup lang="ts">
import { reactive, ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import ZipSetter from '@/components/ZipSetter.vue'

const { t } = useI18n()

interface QuestionnaireInitial {
  id: number
  pollTypeId: number
  title: string
  summary: string
  closeDate: string | null
  questions: { text: string }[]
  zipcodes: string[]
}

const props = defineProps<{
  pollTypeId: number
  initial?: QuestionnaireInitial
}>()

const router = useRouter()

function toLocalInput(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}`
  )
}

const form = reactive({
  title: props.initial?.title ?? '',
  summary: props.initial?.summary ?? '',
  closeDate: toLocalInput(props.initial?.closeDate ?? null),
  zipcodes: [...(props.initial?.zipcodes ?? [])] as string[],
  questions:
    props.initial && props.initial.questions.length > 0
      ? props.initial.questions.map(q => ({ text: q.text }))
      : ([{ text: '' }] as { text: string }[])
})

const draftId = ref<number | null>(props.initial?.id ?? null)
const submitting = ref(false)
const error = ref<string | null>(null)
const message = ref<string | null>(null)
const closeWarning = ref<string | null>(null)

function addQuestion() {
  form.questions.push({ text: '' })
}
function removeQuestion(idx: number) {
  if (form.questions.length > 1) form.questions.splice(idx, 1)
}

function payload() {
  return {
    pollTypeId: props.pollTypeId,
    title: form.title.trim(),
    summary: form.summary.trim(),
    closeDate: form.closeDate ? new Date(form.closeDate).toISOString() : null,
    zipcodes: form.zipcodes,
    questions: form.questions
      .map(q => ({ text: q.text.trim() }))
      .filter(q => q.text.length > 0)
  }
}

function validate(): string | null {
  if (!form.title.trim()) return t('form.validation.titleRequired')
  if (!form.summary.trim()) return t('form.validation.summaryRequired')
  if (form.zipcodes.length === 0) return t('form.validation.zipcodeRequired')
  if (form.questions.every(q => !q.text.trim())) return t('form.validation.atLeastOneQuestion')
  return null
}

async function saveDraft() {
  const v = validate()
  if (v) { error.value = v; return }
  error.value = null; message.value = null; closeWarning.value = null
  submitting.value = true
  try {
    if (draftId.value == null) {
      const res = await axios.post('/api/polls/questionnaires', payload())
      draftId.value = res.data.id
    } else {
      await axios.put(`/api/polls/questionnaires/${draftId.value}`, payload())
    }
    message.value = t('form.draftSaved')
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('form.saveFailed')
  } finally {
    submitting.value = false
  }
}

async function publish(confirmed = false) {
  // Always save first so in-memory edits (e.g. closeDate) are persisted
  // before the publish endpoint validates them.
  await saveDraft()
  if (draftId.value == null || error.value) return
  message.value = null
  if (!confirmed) closeWarning.value = null
  submitting.value = true
  try {
    await axios.post(
      `/api/polls/questionnaires/${draftId.value}/publish`,
      null,
      { params: { confirmed } }
    )
    message.value = t('form.published')
    setTimeout(() => router.push('/creator/dashboard'), 600)
  } catch (e: any) {
    const msg: string = e?.response?.data?.message ?? ''
    if (msg.startsWith('close_date_short:')) {
      const iso = msg.substring('close_date_short:'.length)
      closeWarning.value = t('form.closeDateShort', { date: new Date(iso).toLocaleString() })
    } else {
      error.value = msg || t('form.publishFailed')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
      {{ $t('form.title') }}
      <input
        v-model="form.title"
        maxlength="500"
        required
        class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
      />
    </label>

    <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
      {{ $t('form.summary') }}
      <textarea
        v-model="form.summary"
        rows="3"
        required
        class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
      />
    </label>

    <label class="flex flex-col gap-1 text-sm font-semibold text-slate-700">
      {{ $t('form.closeDateOptional') }}
      <input
        v-model="form.closeDate"
        type="datetime-local"
        class="rounded border border-slate-300 p-2 text-base font-normal focus:border-slate-500 focus:outline-none"
      />
    </label>

    <fieldset class="rounded-md border border-slate-200 p-4">
      <legend class="px-2 text-sm font-semibold text-slate-700">{{ $t('form.geoScope') }}</legend>
      <ZipSetter v-model="form.zipcodes" />
    </fieldset>

    <fieldset class="rounded-md border border-slate-200 p-4">
      <legend class="px-2 text-sm font-semibold text-slate-700">{{ $t('form.questions') }}</legend>
      <div
        v-for="(q, i) in form.questions"
        :key="i"
        class="mb-2 flex gap-1"
      >
        <input
          v-model="q.text"
          :placeholder="t('form.questionPlaceholder', { n: i + 1 })"
          maxlength="1000"
          class="flex-1 rounded border border-slate-300 p-2 text-base focus:border-slate-500 focus:outline-none"
        />
        <button
          type="button"
          @click="removeQuestion(i)"
          :disabled="form.questions.length === 1"
          class="w-8 rounded border border-slate-300 bg-white hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
          :aria-label="$t('form.removeQuestion')"
        >×</button>
      </div>
      <button
        type="button"
        @click="addQuestion"
        class="self-start rounded border border-dashed border-slate-300 bg-white px-3 py-2 text-sm hover:bg-slate-50"
      >{{ $t('form.addQuestion') }}</button>
    </fieldset>

    <p v-if="error" class="m-0 text-sm text-red-700">{{ error }}</p>
    <p v-if="message" class="m-0 text-sm text-green-700">{{ message }}</p>

    <div v-if="closeWarning" class="rounded-md border border-orange-400 bg-orange-50 p-4">
      <p class="mb-2 text-sm text-orange-900">{{ closeWarning }}</p>
      <div class="flex gap-2">
        <button
          type="button"
          @click="publish(true)"
          :disabled="submitting"
          class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {{ $t('form.confirmPublish') }}
        </button>
        <button
          type="button"
          @click="closeWarning = null"
          :disabled="submitting"
          class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {{ $t('form.cancel') }}
        </button>
      </div>
    </div>

    <div v-else class="flex gap-2">
      <button
        type="button"
        @click="saveDraft"
        :disabled="submitting"
        class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ submitting ? $t('form.saving') : (draftId ? $t('form.saveChanges') : $t('form.saveDraft')) }}
      </button>
      <button
        type="button"
        @click="router.push('/creator/dashboard')"
        :disabled="submitting"
        class="rounded border border-slate-300 bg-white px-4 py-2 text-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ $t('form.cancel') }}
      </button>
      <button
        type="button"
        @click="publish(false)"
        :disabled="submitting"
        class="rounded bg-slate-800 px-4 py-2 text-sm text-white hover:bg-slate-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {{ $t('form.publish') }}
      </button>
    </div>
  </div>
</template>
