<script setup lang="ts">
// Local development only (App.vue loads it only in development builds): a
// header link to the Dev settings page. English only: it's a developer tool.
</script>

<template>
  <RouterLink
    to="/dev/settings"
    class="rounded border border-amber-300/60 px-2 py-0.5 text-xs text-amber-100 no-underline hover:bg-white/10"
    data-test="dev-settings-link"
  >Dev settings</RouterLink>
</template>
