<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView, RouterLink, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'
import { SUPPORTED_LOCALES, setLocale, type LocaleCode } from '@/i18n'

const authStore = useAuthStore()
const router = useRouter()
const { locale } = useI18n()

onMounted(async () => {
  if (authStore.token) {
    await authStore.fetchUser()
  }
})

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
    <header class="bg-slate-800 text-white px-8">
      <nav class="mx-auto flex h-16 max-w-6xl items-center justify-between">
        <div class="text-xl font-bold">
          <RouterLink to="/" class="text-white no-underline">{{ $t('brand') }}</RouterLink>
        </div>
        <div class="flex items-center gap-6 text-sm">
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

    <main class="mx-auto my-8 max-w-6xl px-8">
      <RouterView />
    </main>
  </div>
</template>
