// Global test setup. happy-dom provides window/document/localStorage out of
// the box; this file installs vue-i18n on @vue/test-utils so any component
// that calls useI18n() (or {{ $t() }}) mounts without
// "Need to install with `app.use` function" blowing up.
//
// We use a minimal en-only i18n instance — production locales come in via
// src/i18n/index.ts which reads localStorage + navigator.language. Tests
// don't care about those code paths; they only need $t / t() to return a
// non-throwing string so the component renders.

import { config } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import en from './src/i18n/en.json'

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  // Treat missing keys as silent (return the key) — keeps a test from
  // failing on an unrelated translation gap.
  missingWarn: false,
  fallbackWarn: false,
  messages: { en },
})

config.global.plugins = [...(config.global.plugins ?? []), i18n]
