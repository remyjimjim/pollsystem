<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView, RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'

const authStore = useAuthStore()
const router = useRouter()

onMounted(async () => {
  if (authStore.token) {
    await authStore.fetchUser()
  }
})

function handleLogout() {
  authStore.logout()
  router.push({ name: 'Login' })
}
</script>

<template>
  <div id="app" class="min-h-screen">
    <header class="bg-slate-800 text-white px-8">
      <nav class="mx-auto flex h-16 max-w-6xl items-center justify-between">
        <div class="text-xl font-bold">
          <RouterLink to="/" class="text-white no-underline">Poll System</RouterLink>
        </div>
        <div class="flex items-center gap-6 text-sm">
          <RouterLink
            to="/polls/search"
            class="text-white/85 hover:text-white"
            active-class="!text-white"
          >
            {{ authStore.isAuthenticated ? 'Search Polls' : 'View Results' }}
          </RouterLink>

          <RouterLink
            to="/creator/dashboard"
            v-if="authStore.hasAccess(AccessLevel.CREATOR)"
            class="text-white/85 hover:text-white"
            active-class="!text-white"
          >
            Creator
          </RouterLink>

          <RouterLink
            to="/admin/dashboard"
            v-if="authStore.hasAccess(AccessLevel.ADMIN)"
            class="text-white/85 hover:text-white"
            active-class="!text-white"
          >
            Admin
          </RouterLink>

          <RouterLink
            to="/super/dashboard"
            v-if="authStore.hasAccess(AccessLevel.SUPER)"
            class="text-white/85 hover:text-white"
            active-class="!text-white"
          >
            Super Admin
          </RouterLink>

          <template v-if="authStore.isAuthenticated">
            <span class="text-xs text-white/70">{{ authStore.user?.email }}</span>
            <button
              type="button"
              @click="handleLogout"
              class="rounded border border-white/30 bg-white/10 px-3 py-1 text-xs text-white hover:bg-white/25"
            >
              Logout
            </button>
          </template>
          <template v-else>
            <RouterLink to="/login" class="text-white/85 hover:text-white" active-class="!text-white">
              Login
            </RouterLink>
            <RouterLink to="/register" class="text-white/85 hover:text-white" active-class="!text-white">
              Register
            </RouterLink>
          </template>
        </div>
      </nav>
    </header>

    <main class="mx-auto my-8 max-w-6xl px-8">
      <RouterView />
    </main>
  </div>
</template>
