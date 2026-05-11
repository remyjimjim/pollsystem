<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'

const auth = useAuthStore()

interface ActionCard {
  title: string
  description: string
  to: string
  cta: string
}

const cards = computed<ActionCard[]>(() => {
  if (!auth.isAuthenticated) return []
  const out: ActionCard[] = []

  out.push({
    title: 'Find polls and view results',
    description:
      'Search by title, creator, zipcode, candidate, or type. Open a poll to vote or jump to its results page.',
    to: '/polls/search',
    cta: 'Search polls'
  })

  if (auth.hasAccess(AccessLevel.CREATOR)) {
    out.push({
      title: 'Creator dashboard',
      description: 'See your polls in draft, published, and closed states. Create new polls or edit existing drafts.',
      to: '/creator/dashboard',
      cta: 'Open dashboard'
    })
    out.push({
      title: 'Start a new poll',
      description: 'Build an Election, Questionnaire, or Referendum and send respondent links once it goes live.',
      to: '/creator/polls/new',
      cta: 'Create poll'
    })
  } else {
    out.push({
      title: 'Become a Creator',
      description:
        'Apply to host your own polls in specific zipcodes. An admin reviews your request and grants access.',
      to: '/creator/request',
      cta: 'Request Creator access'
    })
  }

  if (auth.hasAccess(AccessLevel.ADMIN)) {
    out.push({
      title: 'Admin dashboard',
      description: 'Review pending Creator requests, manage creators and their polls.',
      to: '/admin/dashboard',
      cta: 'Open admin'
    })
  } else if (auth.hasAccess(AccessLevel.CREATOR)) {
    out.push({
      title: 'Become an Admin',
      description:
        'Apply for admin rights over zipcodes you already create polls in. Reviewed by a Super.',
      to: '/admin-request',
      cta: 'Request Admin access'
    })
  }

  if (auth.hasAccess(AccessLevel.SUPER)) {
    out.push({
      title: 'Super admin',
      description:
        'Review admin requests, manage IP rules, edit poll templates, and oversee all admins.',
      to: '/super/dashboard',
      cta: 'Open super admin'
    })
  }

  return out
})
</script>

<template>
  <div class="py-4">
    <h1 class="mb-2 text-3xl font-semibold text-slate-800">Poll System</h1>

    <template v-if="auth.isAuthenticated">
      <p class="mb-6 text-base text-slate-600">
        Welcome back, <strong class="font-semibold text-slate-900">{{ auth.user?.email }}</strong>
        <span
          class="ml-2 inline-block rounded-full bg-sky-100 px-2 py-0.5 text-xs font-semibold tracking-wide text-slate-800"
        >
          {{ auth.user?.access }}
        </span>
      </p>

      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <article
          v-for="card in cards"
          :key="card.to"
          class="flex flex-col gap-2 rounded-md border border-slate-200 bg-white p-5"
        >
          <h2 class="text-lg font-semibold text-slate-800">{{ card.title }}</h2>
          <p class="flex-1 text-sm text-slate-600">{{ card.description }}</p>
          <router-link
            :to="card.to"
            class="self-start text-sm font-semibold text-slate-800 hover:underline"
          >
            {{ card.cta }} →
          </router-link>
        </article>
      </div>
    </template>

    <template v-else>
      <p class="mb-6 text-base text-slate-600">
        Anonymous polling and elections. Create an account to vote on published
        polls; apply for Creator access to publish your own.
      </p>
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <article class="flex flex-col gap-2 rounded-md border border-slate-200 bg-white p-5">
          <h2 class="text-lg font-semibold text-slate-800">View poll results</h2>
          <p class="flex-1 text-sm text-slate-600">
            Browse every published poll and open its results page. No account required.
          </p>
          <router-link
            to="/polls/search"
            class="self-start text-sm font-semibold text-slate-800 hover:underline"
          >
            View results →
          </router-link>
        </article>
        <article class="flex flex-col gap-2 rounded-md border border-slate-200 bg-white p-5">
          <h2 class="text-lg font-semibold text-slate-800">Sign in</h2>
          <p class="flex-1 text-sm text-slate-600">
            Already have an account? We'll email you a one-time sign-in link.
          </p>
          <router-link
            to="/login"
            class="self-start text-sm font-semibold text-slate-800 hover:underline"
          >
            Sign in →
          </router-link>
        </article>
        <article class="flex flex-col gap-2 rounded-md border border-slate-200 bg-white p-5">
          <h2 class="text-lg font-semibold text-slate-800">Create an account</h2>
          <p class="flex-1 text-sm text-slate-600">
            Email + phone + zipcode is all it takes. No passwords.
          </p>
          <router-link
            to="/register"
            class="self-start text-sm font-semibold text-slate-800 hover:underline"
          >
            Register →
          </router-link>
        </article>
      </div>
    </template>
  </div>
</template>
