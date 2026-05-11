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

  // Every signed-in user
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
      description:
        'Review pending Creator requests, manage creators and their polls.',
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
  <div class="view">
    <h1>Poll System</h1>

    <template v-if="auth.isAuthenticated">
      <p class="welcome">
        Welcome back, <strong>{{ auth.user?.email }}</strong>
        <span class="badge">{{ auth.user?.access }}</span>
      </p>

      <div class="card-grid">
        <article v-for="card in cards" :key="card.to" class="card">
          <h2>{{ card.title }}</h2>
          <p>{{ card.description }}</p>
          <router-link :to="card.to" class="btn">{{ card.cta }} →</router-link>
        </article>
      </div>
    </template>

    <template v-else>
      <p class="welcome">
        Anonymous polling and elections. Create an account to vote on published
        polls; apply for Creator access to publish your own.
      </p>
      <div class="card-grid">
        <article class="card">
          <h2>View poll results</h2>
          <p>Browse every published poll and open its results page. No account required.</p>
          <router-link to="/polls/search" class="btn">View results →</router-link>
        </article>
        <article class="card">
          <h2>Sign in</h2>
          <p>Already have an account? We'll email you a one-time sign-in link.</p>
          <router-link to="/login" class="btn">Sign in →</router-link>
        </article>
        <article class="card">
          <h2>Create an account</h2>
          <p>Email + phone + zipcode is all it takes. No passwords.</p>
          <router-link to="/register" class="btn">Register →</router-link>
        </article>
      </div>
    </template>
  </div>
</template>

<style scoped>
.view {
  padding: 1rem 0 2rem;
}
h1 {
  margin: 0 0 0.5rem;
  color: #1a365d;
  font-size: 2rem;
}
.welcome {
  font-size: 1rem;
  color: #4a5568;
  margin: 0 0 1.5rem;
}
.badge {
  display: inline-block;
  margin-left: 0.5rem;
  padding: 0.15rem 0.5rem;
  background: #bee3f8;
  color: #1a365d;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.03em;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1rem;
}
.card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.card h2 {
  font-size: 1.1rem;
  color: #1a365d;
  margin: 0;
}
.card p {
  font-size: 0.9rem;
  color: #4a5568;
  margin: 0 0 0.5rem;
  flex: 1;
}
.btn {
  align-self: flex-start;
  font-size: 0.9rem;
  color: #1a365d;
  text-decoration: none;
  font-weight: 600;
}
.btn:hover {
  text-decoration: underline;
}
</style>
