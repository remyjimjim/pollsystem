<script setup lang="ts">
import { defineAsyncComponent, onMounted, onUnmounted } from 'vue'
import BrandLogo from '@/components/BrandLogo.vue'
import { RouterView, RouterLink, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'
import { SUPPORTED_LOCALES, setLocale, type LocaleCode } from '@/i18n'
import { useNewVersion } from '@/composables/useNewVersion'
import SettingsMenu from '@/components/SettingsMenu.vue'

const authStore = useAuthStore()
// Local development only: null in a production build, so it isn't bundled.
const DevHeaderLink = import.meta.env.DEV
  ? defineAsyncComponent(() => import('@/components/DevHeaderLink.vue'))
  : null
const router = useRouter()
const newVersion = useNewVersion()
const { locale } = useI18n()

onMounted(async () => {
  if (authStore.token) {
    await authStore.fetchUser()
  }
})

// Logging in or out (or in as someone else) in one tab applies to every tab.
// On the Login page a new login (signed out before, or as someone else) is
// left to the page itself: it adopts it and moves on to where you were going.
function onStorage(e: StorageEvent) {
  if (router.currentRoute.value.name === 'Login' && e.key === 'token' && e.newValue) return
  const change = authStore.syncFromOtherTab(e)
  if (change === 'signedOut') router.push('/')
  else if (change === 'switched') window.location.reload()
  // Signed in elsewhere (e.g. the emailed link in a new tab): pick it up.
  else if (change === 'signedIn') authStore.adoptToken(e.newValue!)
}
onMounted(() => window.addEventListener('storage', onStorage))
onUnmounted(() => window.removeEventListener('storage', onStorage))

function handleLogout() {
  authStore.logout()
  router.push({ name: 'Login' })
}

function onLocaleChange(e: Event) {
  setLocale((e.target as HTMLSelectElement).value as LocaleCode)
}
</script>

<template>
  <div id="app" class="min-h-screen">
    <div
      v-if="newVersion.available.value"
      class="flex flex-wrap items-center justify-center gap-x-3 gap-y-1 bg-sky-700 px-4 py-2 text-sm text-white print:hidden"
      role="status"
      data-test="new-version"
    >
      <span>{{ $t('common.newVersion') }}</span>
      <button type="button" @click="newVersion.reload" class="rounded bg-white px-3 py-1 text-sm font-semibold text-sky-800 hover:bg-sky-50">{{ $t('common.reload') }}</button>
    </div>
    <!-- Hidden when printing (e.g. a creator's poll report). -->
    <header class="bg-slate-800 text-white px-4 sm:px-8 print:hidden">
      <nav class="mx-auto flex min-h-20 max-w-6xl flex-wrap items-center justify-between gap-y-2 pt-2 pb-3.5 sm:pt-0 sm:pb-1.5 xl:pb-0">
        <div class="order-1 text-xl font-bold">
          <RouterLink to="/" class="flex items-center gap-3 text-white no-underline" data-test="brand">
            <span class="h-14 sm:h-[72px]"><BrandLogo /></span>
            {{ $t('brand') }}
          </RouterLink>
        </div>
        <!-- The gear stays top right: beside the brand up to an extra-large screen
             (the menu wraps to its own row below), after the menu from there. -->
        <div class="order-2 ml-auto xl:order-3 xl:ml-6">
          <SettingsMenu />
        </div>
        <div class="order-3 flex w-full flex-wrap items-center gap-x-4 gap-y-2 text-sm sm:gap-x-6 xl:order-2 xl:ml-auto xl:w-auto">
          <RouterLink
            to="/polls/search"
            class="text-white/85 hover:text-white"
            active-class="!text-white"
          >
            {{ authStore.isAuthenticated ? $t('nav.searchPolls') : $t('nav.viewResults') }}
          </RouterLink>

          <RouterLink
            to="/creator/dashboard"
            v-if="authStore.hasAccess(AccessLevel.CREATOR)"
            class="text-white/85 hover:text-white"
            active-class="!text-white"
          >
            {{ $t('nav.creator') }}
          </RouterLink>

          <RouterLink
            to="/admin/dashboard"
            v-if="authStore.hasAccess(AccessLevel.ADMIN)"
            class="text-white/85 hover:text-white"
            active-class="!text-white"
          >
            {{ $t('nav.admin') }}
          </RouterLink>

          <RouterLink
            to="/super/dashboard"
            v-if="authStore.hasAccess(AccessLevel.SUPER)"
            class="text-white/85 hover:text-white"
            active-class="!text-white"
          >
            {{ $t('nav.superAdmin') }}
          </RouterLink>

          <RouterLink
            to="/help"
            class="text-white/85 hover:text-white"
            active-class="!text-white"
          >
            {{ $t('nav.help') }}
          </RouterLink>

          <template v-if="authStore.isAuthenticated">
            <span class="text-xs text-white/70">{{ authStore.user?.email }}</span>
            <button
              type="button"
              @click="handleLogout"
              class="rounded border border-white/30 bg-white/10 px-3 py-1 text-xs text-white hover:bg-white/25"
            >
              {{ $t('nav.logout') }}
            </button>
          </template>
          <template v-else>
            <RouterLink to="/login" class="text-white/85 hover:text-white" active-class="!text-white">
              {{ $t('nav.login') }}
            </RouterLink>
            <RouterLink to="/register" class="text-white/85 hover:text-white" active-class="!text-white">
              {{ $t('nav.register') }}
            </RouterLink>
          </template>

          <component :is="DevHeaderLink" v-if="DevHeaderLink" />
          <select
            :value="locale"
            @change="onLocaleChange"
            :aria-label="$t('nav.language')"
            class="rounded border border-white/30 bg-white/10 px-2 py-1 text-xs text-white"
          >
            <option v-for="l in SUPPORTED_LOCALES" :key="l.code" :value="l.code" class="text-slate-900">
              {{ l.name }}
            </option>
          </select>
        </div>
      </nav>
    </header>

    <main class="app-content mx-auto my-8 max-w-6xl px-4 sm:px-8">
      <RouterView />
    </main>
    <footer class="app-content mx-auto mb-8 flex max-w-6xl flex-wrap justify-center gap-x-4 gap-y-1 px-4 text-xs text-slate-500 sm:px-8 print:hidden" data-test="site-footer">
      <RouterLink to="/legal/terms" class="hover:text-slate-800 hover:underline">{{ $t('legal.terms') }}</RouterLink>
      <RouterLink to="/legal/privacy" class="hover:text-slate-800 hover:underline">{{ $t('legal.privacy') }}</RouterLink>
      <RouterLink to="/legal/refunds" class="hover:text-slate-800 hover:underline">{{ $t('legal.refunds') }}</RouterLink>
      <RouterLink to="/legal/why" class="hover:text-slate-800 hover:underline" data-test="footer-why">{{ $t('legal.why') }}</RouterLink>
    </footer>
  </div>
</template>
