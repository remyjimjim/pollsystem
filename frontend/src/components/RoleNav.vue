<script setup lang="ts">
// The header's role links (decided 2026-10-28): Vote for every member; then a
// creator gets Create, an admin an Admin menu (Admin, Create), and a super a
// Super menu (Super, Admin, Create). The menu opens on click and closes on a
// pick, a click elsewhere, or Escape.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'

const auth = useAuthStore()
const route = useRoute()
const { t } = useI18n()

const linkClass = 'text-white/85 hover:text-white'
const menu = computed(() => {
  if (auth.hasAccess(AccessLevel.SUPER)) return {
    label: t('nav.super'),
    items: [
      { to: '/super/dashboard', label: t('nav.super') },
      { to: '/admin/dashboard', label: t('nav.admin') },
      { to: '/creator/dashboard', label: t('nav.create') },
    ],
  }
  if (auth.hasAccess(AccessLevel.ADMIN)) return {
    label: t('nav.admin'),
    items: [
      { to: '/admin/dashboard', label: t('nav.admin') },
      { to: '/creator/dashboard', label: t('nav.create') },
    ],
  }
  return null
})
const inMenu = computed(() => !!menu.value?.items.some(i => route.path.startsWith(i.to.replace('/dashboard', ''))))

const open = ref(false)
const root = ref<HTMLElement | null>(null)
function onDocClick(e: MouseEvent) {
  if (open.value && root.value && !root.value.contains(e.target as Node)) open.value = false
}
function onKey(e: KeyboardEvent) { if (e.key === 'Escape') open.value = false }
watch(() => route.fullPath, () => { open.value = false })
onMounted(() => { document.addEventListener('click', onDocClick); document.addEventListener('keydown', onKey) })
onUnmounted(() => { document.removeEventListener('click', onDocClick); document.removeEventListener('keydown', onKey) })
</script>

<template>
  <RouterLink to="/polls/search" :class="linkClass" active-class="!text-white" data-test="nav-vote">
    {{ auth.isAuthenticated ? $t('nav.vote') : $t('nav.viewResults') }}
  </RouterLink>

  <RouterLink
    v-if="!menu && auth.hasAccess(AccessLevel.CREATOR)"
    to="/creator/dashboard"
    :class="linkClass"
    active-class="!text-white"
    data-test="nav-create"
  >{{ $t('nav.create') }}</RouterLink>

  <div v-if="menu" ref="root" class="relative" data-test="nav-role-menu">
    <button
      type="button"
      :aria-expanded="open"
      aria-haspopup="menu"
      class="flex items-center gap-1"
      :class="[linkClass, inMenu ? '!text-white' : '']"
      @click="open = !open"
    >
      {{ menu.label }}
      <svg viewBox="0 0 20 20" class="h-3.5 w-3.5 transition-transform" :class="{ 'rotate-180': open }" aria-hidden="true">
        <path fill="currentColor" d="M5.3 7.3 4 8.6 10 14.6l6-6L14.7 7.3 10 12z" />
      </svg>
    </button>
    <div
      v-if="open"
      role="menu"
      class="absolute left-0 z-30 mt-2 min-w-36 rounded border border-slate-600 bg-slate-700 py-1 shadow-lg"
    >
      <RouterLink
        v-for="item in menu.items"
        :key="item.to"
        :to="item.to"
        role="menuitem"
        class="block px-4 py-2 text-sm text-white/90 hover:bg-slate-600 hover:text-white"
        active-class="font-semibold !text-white"
      >{{ item.label }}</RouterLink>
    </div>
  </div>
</template>
