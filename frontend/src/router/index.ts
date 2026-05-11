import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'

const routes: RouteRecordRaw[] = [
  // Public routes
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/RegisterView.vue')
  },
  {
    // Redemption landing page — Mailpit/SendGrid magic-link URLs point here.
    path: '/auth/magic-link',
    name: 'MagicLink',
    component: () => import('@/views/MagicLinkView.vue')
  },
  {
    path: '/polls/:type/:id/results',
    name: 'PollResults',
    component: () => import('@/views/PollResultsView.vue')
  },

  // User routes (requires USER+)
  {
    path: '/polls/search',
    name: 'PollSearch',
    component: () => import('@/views/PollSearchView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.USER }
  },
  {
    path: '/polls/:type/:id',
    name: 'PollDetail',
    component: () => import('@/views/PollDetailView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.USER }
  },
  {
    path: '/creator/request',
    name: 'CreatorRequest',
    component: () => import('@/views/CreatorRequestView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.USER }
  },
  {
    path: '/admin-request',
    name: 'AdminRequest',
    component: () => import('@/views/AdminRequestView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.CREATOR }
  },

  // Creator routes (requires CREATOR+)
  {
    path: '/creator/dashboard',
    name: 'CreatorDashboard',
    component: () => import('@/views/creator/DashboardView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.CREATOR }
  },
  {
    path: '/creator/polls/new',
    name: 'CreatePoll',
    component: () => import('@/views/creator/CreatePollView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.CREATOR }
  },
  {
    path: '/creator/polls/:type/:id/edit',
    name: 'EditPoll',
    component: () => import('@/views/creator/EditPollView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.CREATOR }
  },

  // Admin routes (requires ADMIN+)
  {
    path: '/admin/dashboard',
    name: 'AdminDashboard',
    component: () => import('@/views/admin/DashboardView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.ADMIN }
  },
  {
    path: '/admin/creator-requests',
    name: 'CreatorRequests',
    component: () => import('@/views/admin/CreatorRequestsView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.ADMIN }
  },
  {
    path: '/admin/manage-creators',
    name: 'ManageCreators',
    component: () => import('@/views/admin/ManageCreatorsView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.ADMIN }
  },
  {
    path: '/admin/manage-polls',
    name: 'ManagePolls',
    component: () => import('@/views/admin/ManagePollsView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.ADMIN }
  },

  // Super routes (requires SUPER)
  {
    path: '/super/dashboard',
    name: 'SuperDashboard',
    component: () => import('@/views/super/DashboardView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.SUPER }
  },
  {
    path: '/super/admin-requests',
    name: 'SuperAdminRequests',
    component: () => import('@/views/super/AdminRequestsView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.SUPER }
  },
  {
    path: '/super/ip-management',
    name: 'IpManagement',
    component: () => import('@/views/super/IpManagementView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.SUPER }
  },
  {
    path: '/super/poll-templates',
    name: 'PollTemplates',
    component: () => import('@/views/super/PollTemplatesView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.SUPER }
  },
  {
    path: '/super/manage-admins',
    name: 'ManageAdmins',
    component: () => import('@/views/super/ManageAdminsView.vue'),
    meta: { requiresAuth: true, minAccess: AccessLevel.SUPER }
  },

  // Catch-all
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Access level hierarchy for comparison
const accessHierarchy: Record<AccessLevel, number> = {
  [AccessLevel.VIEWER]: 0,
  [AccessLevel.USER]: 1,
  [AccessLevel.CREATOR]: 2,
  [AccessLevel.ADMIN]: 3,
  [AccessLevel.SUPER]: 4
}

// Navigation guard for auth and role checking
router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  if (to.meta.minAccess) {
    const requiredLevel = accessHierarchy[to.meta.minAccess as AccessLevel]
    const userLevel = accessHierarchy[authStore.user?.access ?? AccessLevel.VIEWER]

    if (userLevel < requiredLevel) {
      next({ name: 'Home' })
      return
    }
  }

  next()
})

export default router
