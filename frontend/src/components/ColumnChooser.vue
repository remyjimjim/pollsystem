<script setup lang="ts" generic="K extends string">
/** The "Show: Title ☑ Type ☑ …" row above a table; pair with useColumns. */
const columns = defineModel<Record<K, boolean>>({ required: true })
defineProps<{ options: { key: K; label: string }[] }>()

function set(key: K, shown: boolean) {
  columns.value = { ...columns.value, [key]: shown }
}
</script>

<template>
  <fieldset class="mb-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-600" data-test="column-chooser">
    <legend class="sr-only">{{ $t('search.columns.legend') }}</legend>
    <span class="font-semibold">{{ $t('search.columns.show') }}</span>
    <label v-for="o in options" :key="o.key" class="inline-flex items-center gap-1">
      <input
        type="checkbox"
        class="h-3.5 w-3.5"
        :checked="columns[o.key]"
        :data-test="`show-${o.key}`"
        @change="set(o.key, ($event.target as HTMLInputElement).checked)"
      />{{ o.label }}
    </label>
  </fieldset>
</template>
