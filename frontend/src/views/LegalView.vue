<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { legalPage, renderLegal, placeholdersIn, POLICY_PAGES } from '@/legal/legal'

const route = useRoute()
// The policies are in English only (decided 2026-10-25): in any other language
// the page says so, and that the English version is the one that applies.
const { locale } = useI18n()
const englishOnly = computed(() =>
  !String(locale.value).startsWith('en') && !!page.value && POLICY_PAGES.includes(page.value.slug))
const page = computed(() => legalPage(String(route.params.page)))
const html = computed(() => (page.value ? renderLegal(page.value) : ''))
const todo = computed(() => (page.value ? placeholdersIn(page.value).length : 0))
</script>

<template>
  <div class="mx-auto max-w-3xl py-8">
    <template v-if="page">
      <p v-if="todo" class="mb-4 rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" data-test="legal-draft">
        Draft: {{ todo }} highlighted item{{ todo === 1 ? '' : 's' }} still to fill in before launch.
      </p>
      <p v-if="englishOnly" class="mb-4 rounded border border-sky-300 bg-sky-50 p-3 text-sm text-sky-900" data-test="legal-english-only">
        {{ $t('legal.englishOnly') }}
      </p>
      <article class="legal-page" lang="en" data-test="legal-page" v-html="html" />
    </template>
    <p v-else class="text-sm text-slate-600">{{ $t('notFound.message') }}</p>
  </div>
</template>

<style scoped>
.legal-page :deep(h1) { font-size: 1.5rem; font-weight: 600; margin: 0 0 0.75rem; color: var(--color-slate-800); }
.legal-page :deep(h2) { font-size: 1.125rem; font-weight: 600; margin: 1.5rem 0 0.5rem; color: var(--color-slate-800); }
.legal-page :deep(p), .legal-page :deep(li) { color: var(--color-slate-700); line-height: 1.6; }
.legal-page :deep(p) { margin: 0 0 0.75rem; }
.legal-page :deep(ul) { list-style: disc; padding-left: 1.25rem; margin: 0 0 0.75rem; }
.legal-page :deep(a) { text-decoration: underline; }
.legal-page :deep(mark.legal-todo) { background: rgb(254 240 138); color: #000; padding: 0 0.2rem; border-radius: 0.2rem; }
</style>
