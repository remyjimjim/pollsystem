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
  <div id="app">
    <header>
      <nav>
        <div class="nav-brand">
          <RouterLink to="/">Poll System</RouterLink>
        </div>
        <div class="nav-links">
          <RouterLink to="/polls/search">
            {{ authStore.isAuthenticated ? 'Search Polls' : 'View Results' }}
          </RouterLink>

          <!-- Creator Drawer Link -->
          <RouterLink
            to="/creator/dashboard"
            v-if="authStore.hasAccess(AccessLevel.CREATOR)"
          >
            Creator
          </RouterLink>

          <!-- Admin Drawer Link -->
          <RouterLink
            to="/admin/dashboard"
            v-if="authStore.hasAccess(AccessLevel.ADMIN)"
          >
            Admin
          </RouterLink>

          <!-- Super Drawer Link -->
          <RouterLink
            to="/super/dashboard"
            v-if="authStore.hasAccess(AccessLevel.SUPER)"
          >
            Super Admin
          </RouterLink>

          <template v-if="authStore.isAuthenticated">
            <span class="user-info">{{ authStore.user?.email }}</span>
            <button @click="handleLogout" class="btn-logout">Logout</button>
          </template>
          <template v-else>
            <RouterLink to="/login">Login</RouterLink>
            <RouterLink to="/register">Register</RouterLink>
          </template>
        </div>
      </nav>
    </header>

    <main>
      <RouterView />
    </main>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  color: #333;
  background-color: #f5f5f5;
}

#app {
  min-height: 100vh;
}

header {
  background-color: #1a365d;
  color: white;
  padding: 0 2rem;
}

nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
  height: 64px;
}

.nav-brand a {
  color: white;
  text-decoration: none;
  font-size: 1.25rem;
  font-weight: 700;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.nav-links a {
  color: rgba(255, 255, 255, 0.85);
  text-decoration: none;
  font-size: 0.9rem;
  transition: color 0.2s;
}

.nav-links a:hover,
.nav-links a.router-link-active {
  color: white;
}

.user-info {
  font-size: 0.85rem;
  opacity: 0.8;
}

.btn-logout {
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  padding: 0.4rem 0.8rem;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: background 0.2s;
}

.btn-logout:hover {
  background: rgba(255, 255, 255, 0.25);
}

main {
  max-width: 1200px;
  margin: 2rem auto;
  padding: 0 2rem;
}
</style>
