<script setup lang="ts">
// The header gear: a Settings box with a Theme drawer (Dark/Light, each with
// text and background colors) and the text size. Closes on an outside click
// or Escape.
import { onMounted, onUnmounted, ref } from 'vue'
import { THEME_DEFAULTS, useAppearance, type Theme } from '@/composables/useAppearance'

const appearance = useAppearance()
const open = ref(false)
const themeOpen = ref(true)
const root = ref<HTMLElement | null>(null)

function toggleTheme() { appearance.theme = appearance.theme === 'dark' ? 'light' : 'dark' }
type Which = 'text' | 'background'
const isDefault = (t: Theme, which: Which) =>
  appearance.colors[t][which].toLowerCase() === THEME_DEFAULTS[t][which]

// "Use Default": ticking it resets the color; unticking brings back the last
// color picked (kept for this visit), so the two can be compared.
const lastPicked: Record<string, string> = {}
function setUseDefault(t: Theme, which: Which, on: boolean) {
  const key = `${t}.${which}`
  if (on) {
    if (!isDefault(t, which)) lastPicked[key] = appearance.colors[t][which]
    appearance.colors[t][which] = THEME_DEFAULTS[t][which]
  } else if (lastPicked[key]) {
    appearance.colors[t][which] = lastPicked[key]
  }
}

function onDocClick(e: MouseEvent) { if (open.value && root.value && !root.value.contains(e.target as Node)) open.value = false }
function onKey(e: KeyboardEvent) { if (e.key === 'Escape') open.value = false }
onMounted(() => { document.addEventListener('click', onDocClick); document.addEventListener('keydown', onKey) })
onUnmounted(() => { document.removeEventListener('click', onDocClick); document.removeEventListener('keydown', onKey) })
</script>

<template>
  <div ref="root" class="relative" data-test="settings">
    <button
      type="button"
      @click="open = !open"
      :aria-expanded="open"
      :aria-label="$t('settings.title')"
      :title="$t('settings.title')"
      class="rounded p-1 text-white/85 hover:bg-white/10 hover:text-white"
    >
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="M10.3 4.3c.4-1.7 3-1.7 3.4 0a1.7 1.7 0 0 0 2.6 1.1c1.5-.9 3.3.9 2.4 2.4a1.7 1.7 0 0 0 1 2.6c1.8.4 1.8 3 0 3.4a1.7 1.7 0 0 0-1 2.6c.9 1.5-.9 3.3-2.4 2.4a1.7 1.7 0 0 0-2.6 1c-.4 1.8-3 1.8-3.4 0a1.7 1.7 0 0 0-2.6-1c-1.5.9-3.3-.9-2.4-2.4a1.7 1.7 0 0 0-1-2.6c-1.7-.4-1.7-3 0-3.4a1.7 1.7 0 0 0 1-2.6c-.9-1.5.9-3.3 2.4-2.4a1.7 1.7 0 0 0 2.6-1.1Z" />
        <circle cx="12" cy="12" r="3" />
      </svg>
    </button>

    <!-- Fixed colors: the box sits in the header, outside the themed page. -->
    <div
      v-if="open"
      class="absolute right-0 top-full z-50 mt-2 w-72 rounded-md border border-slate-300 bg-white p-3 text-sm text-slate-800 shadow-lg"
      role="dialog"
      :aria-label="$t('settings.title')"
    >
      <h2 class="mb-2 text-sm font-semibold">{{ $t('settings.title') }}</h2>

      <button
        type="button"
        @click="themeOpen = !themeOpen"
        :aria-expanded="themeOpen"
        class="flex w-full items-center justify-between rounded px-1 py-1 font-semibold hover:bg-slate-100"
      >
        <span>{{ $t('settings.theme') }}</span>
        <span aria-hidden="true">{{ themeOpen ? '▾' : '▸' }}</span>
      </button>

      <div v-if="themeOpen" class="mt-2 flex flex-col gap-3 border-t border-slate-200 pt-3">
        <div class="flex items-center gap-2">
          <span :class="appearance.theme === 'dark' ? 'font-semibold' : 'text-slate-500'">{{ $t('settings.dark') }}</span>
          <button
            type="button"
            role="switch"
            :aria-checked="appearance.theme === 'light'"
            :aria-label="$t('settings.theme')"
            @click="toggleTheme"
            class="relative inline-flex h-6 w-11 items-center rounded-full bg-slate-400 transition"
            data-test="theme-switch"
          >
            <span
              class="inline-block h-5 w-5 rounded-full bg-white shadow transition"
              :class="appearance.theme === 'light' ? 'translate-x-5' : 'translate-x-0.5'"
            />
          </button>
          <span :class="appearance.theme === 'light' ? 'font-semibold' : 'text-slate-500'">{{ $t('settings.light') }}</span>
        </div>

        <div class="flex items-center gap-2">
          <label :for="`color-text`" class="w-28">{{ $t('settings.textColor') }}</label>
          <input :id="`color-text`" type="color" v-model="appearance.colors[appearance.theme].text" class="h-7 w-10 cursor-pointer rounded border border-slate-300" />
          <label class="flex items-center gap-1 text-xs">
            <input
              type="checkbox"
              :checked="isDefault(appearance.theme, 'text')"
              @change="setUseDefault(appearance.theme, 'text', ($event.target as HTMLInputElement).checked)"
              data-test="use-default-text"
            />
            {{ $t('settings.useDefault') }}
          </label>
        </div>
        <div class="flex items-center gap-2">
          <label :for="`color-background`" class="w-28">{{ $t('settings.backgroundColor') }}</label>
          <input :id="`color-background`" type="color" v-model="appearance.colors[appearance.theme].background" class="h-7 w-10 cursor-pointer rounded border border-slate-300" />
          <label class="flex items-center gap-1 text-xs">
            <input
              type="checkbox"
              :checked="isDefault(appearance.theme, 'background')"
              @change="setUseDefault(appearance.theme, 'background', ($event.target as HTMLInputElement).checked)"
              data-test="use-default-background"
            />
            {{ $t('settings.useDefault') }}
          </label>
        </div>

        <fieldset class="flex flex-wrap items-center gap-x-3 gap-y-1">
          <legend class="mb-1 w-full">{{ $t('settings.textSize') }}</legend>
          <label v-for="s in (['small', 'medium', 'large'] as const)" :key="s" class="flex items-center gap-1">
            <input type="radio" name="text-size" :value="s" v-model="appearance.size" />
            {{ $t(`settings.${s}`) }}
          </label>
        </fieldset>
      </div>
    </div>
  </div>
</template>
