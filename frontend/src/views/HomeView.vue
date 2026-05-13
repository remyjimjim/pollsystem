<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'

const { t } = useI18n()
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
    title: t('home.cards.findPolls.title'),
    description: t('home.cards.findPolls.description'),
    to: '/polls/search',
    cta: t('home.cards.findPolls.cta')
  })

  if (auth.hasAccess(AccessLevel.CREATOR)) {
    out.push({
      title: t('home.cards.creatorDashboard.title'),
      description: t('home.cards.creatorDashboard.description'),
      to: '/creator/dashboard',
      cta: t('home.cards.creatorDashboard.cta')
    })
    out.push({
      title: t('home.cards.newPoll.title'),
      description: t('home.cards.newPoll.description'),
      to: '/creator/polls/new',
      cta: t('home.cards.newPoll.cta')
    })
  } else {
    out.push({
      title: t('home.cards.becomeCreator.title'),
      description: t('home.cards.becomeCreator.description'),
      to: '/creator/request',
      cta: t('home.cards.becomeCreator.cta')
    })
  }

  if (auth.hasAccess(AccessLevel.ADMIN)) {
    out.push({
      title: t('home.cards.adminDashboard.title'),
      description: t('home.cards.adminDashboard.description'),
      to: '/admin/dashboard',
      cta: t('home.cards.adminDashboard.cta')
    })
  } else if (auth.hasAccess(AccessLevel.CREATOR)) {
    out.push({
      title: t('home.cards.becomeAdmin.title'),
      description: t('home.cards.becomeAdmin.description'),
      to: '/admin-request',
      cta: t('home.cards.becomeAdmin.cta')
    })
  }

  if (auth.hasAccess(AccessLevel.SUPER)) {
    out.push({
      title: t('home.cards.superAdmin.title'),
      description: t('home.cards.superAdmin.description'),
      to: '/super/dashboard',
      cta: t('home.cards.superAdmin.cta')
    })
  }

  return out
})
</script>

<template>
  <div class="py-4">
    <h1 class="mb-2 text-3xl font-semibold text-slate-800">{{ $t('brand') }}</h1>

    <template v-if="auth.isAuthenticated">
      <p class="mb-6 text-base text-slate-600">
        {{ $t('home.welcomeBack') }} <strong class="font-semibold text-slate-900">{{ auth.user?.email }}</strong>
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
        {{ $t('home.guestIntro') }}
      </p>
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <article class="flex flex-col gap-2 rounded-md border border-slate-200 bg-white p-5">
          <h2 class="text-lg font-semibold text-slate-800">{{ $t('home.cards.guestResults.title') }}</h2>
          <p class="flex-1 text-sm text-slate-600">
            {{ $t('home.cards.guestResults.description') }}
          </p>
          <router-link
            to="/polls/search"
            class="self-start text-sm font-semibold text-slate-800 hover:underline"
          >
            {{ $t('home.cards.guestResults.cta') }} →
          </router-link>
        </article>
        <article class="flex flex-col gap-2 rounded-md border border-slate-200 bg-white p-5">
          <h2 class="text-lg font-semibold text-slate-800">{{ $t('home.cards.guestSignIn.title') }}</h2>
          <p class="flex-1 text-sm text-slate-600">
            {{ $t('home.cards.guestSignIn.description') }}
          </p>
          <router-link
            to="/login"
            class="self-start text-sm font-semibold text-slate-800 hover:underline"
          >
            {{ $t('home.cards.guestSignIn.cta') }} →
          </router-link>
        </article>
        <article class="flex flex-col gap-2 rounded-md border border-slate-200 bg-white p-5">
          <h2 class="text-lg font-semibold text-slate-800">{{ $t('home.cards.guestRegister.title') }}</h2>
          <p class="flex-1 text-sm text-slate-600">
            {{ $t('home.cards.guestRegister.description') }}
          </p>
          <router-link
            to="/register"
            class="self-start text-sm font-semibold text-slate-800 hover:underline"
          >
            {{ $t('home.cards.guestRegister.cta') }} →
          </router-link>
        </article>
      </div>
    </template>
  </div>
</template>
