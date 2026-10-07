<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { PAGES, SECTIONS, canSee, findPage, renderPage } from '@/help/manual'

// /help lists the manual pages this visitor may see, grouped by audience;
// /help/:section/:slug shows one page (see src/help/manual.ts).
const route = useRoute()
const router = useRouter()
const { locale } = useI18n()
const authStore = useAuthStore()

const visible = (section: string) => canSee(section as never, authStore.hasAccess)

const groups = computed(() =>
  SECTIONS
    .filter(s => visible(s.key))
    .map(s => ({ key: s.key, pages: PAGES.filter(p => p.section === s.key) }))
    .filter(g => g.pages.length > 0))

const section = computed(() => route.params.section as string | undefined)
const page = computed(() =>
  section.value ? findPage(section.value, route.params.slug as string) : undefined)
const allowed = computed(() => !!page.value && visible(page.value.section))
const html = computed(() => (page.value && allowed.value ? renderPage(page.value) : ''))
const englishOnly = computed(() => !String(locale.value).startsWith('en'))

// Links between pages are rewritten to /help/...; follow them in the app
// instead of reloading the whole page.
function onContentClick(e: MouseEvent) {
  const a = (e.target as HTMLElement).closest('a')
  const href = a?.getAttribute('href')
  if (href?.startsWith('/help/') && !e.ctrlKey && !e.metaKey && !e.shiftKey) {
    e.preventDefault()
    router.push(href)
  }
}
</script>

<template>
  <div class="mx-auto max-w-3xl py-8">
    <p v-if="englishOnly" class="mb-4 rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
      {{ $t('manual.englishOnly') }}
    </p>

    <!-- Index -->
    <template v-if="!section">
      <h1 class="mb-2 text-2xl font-semibold text-slate-800">{{ $t('manual.heading') }}</h1>
      <p class="mb-6 text-sm text-slate-600">{{ $t('manual.intro') }}</p>
      <section v-for="g in groups" :key="g.key" class="mb-6">
        <h2 class="mb-2 text-lg font-semibold text-slate-700">{{ $t(`manual.sections.${g.key}`) }}</h2>
        <ul class="m-0 list-none p-0">
          <li v-for="p in g.pages" :key="p.slug" class="border-b border-slate-100 py-2 last:border-b-0">
            <RouterLink :to="`/help/${p.section}/${p.slug}`" class="font-medium text-slate-800 underline hover:text-slate-950">
              {{ p.title }}
            </RouterLink>
            <p v-if="p.summary" class="m-0 text-sm text-slate-600">{{ p.summary }}</p>
          </li>
        </ul>
      </section>
    </template>

    <!-- One page -->
    <template v-else>
      <RouterLink to="/help" class="text-sm text-slate-600 underline hover:text-slate-900">
        ← {{ $t('manual.back') }}
      </RouterLink>
      <!-- Content comes from our own docs/manual files, sanitised by renderPage(). -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <article v-if="allowed" class="manual mt-4" v-html="html" @click="onContentClick" />
      <p v-else-if="page && !authStore.isAuthenticated" class="mt-4 text-slate-700">
        {{ $t('manual.signInToSee') }}
        <RouterLink :to="{ name: 'Login', query: { redirect: route.fullPath } }" class="underline">{{ $t('nav.login') }}</RouterLink>
      </p>
      <p v-else class="mt-4 text-slate-700">{{ $t('manual.notFound') }}</p>
    </template>
  </div>
</template>

<style scoped>
.manual :deep(h1) { margin: 0 0 1rem; font-size: 1.5rem; font-weight: 600; color: rgb(30 41 59); }
.manual :deep(h2) { margin: 1.75rem 0 0.5rem; font-size: 1.15rem; font-weight: 600; color: rgb(51 65 85); }
.manual :deep(h3) { margin: 1.25rem 0 0.5rem; font-weight: 600; color: rgb(51 65 85); }
.manual :deep(p) { margin: 0 0 0.75rem; line-height: 1.6; color: rgb(51 65 85); }
.manual :deep(ul), .manual :deep(ol) { margin: 0 0 0.75rem; padding-left: 1.5rem; line-height: 1.6; color: rgb(51 65 85); }
.manual :deep(ul) { list-style: disc; }
.manual :deep(ol) { list-style: decimal; }
.manual :deep(li) { margin: 0.25rem 0; }
.manual :deep(a) { text-decoration: underline; color: rgb(30 41 59); }
.manual :deep(code) { border-radius: 0.25rem; background: rgb(241 245 249); padding: 0.1rem 0.3rem; font-size: 0.9em; }
.manual :deep(table) { margin: 0 0 0.75rem; border-collapse: collapse; }
.manual :deep(th), .manual :deep(td) { border: 1px solid rgb(226 232 240); padding: 0.35rem 0.6rem; text-align: left; }
.manual :deep(img) { max-width: 100%; border: 1px solid rgb(226 232 240); border-radius: 0.375rem; }
</style>
