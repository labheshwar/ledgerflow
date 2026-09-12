<script setup lang="ts">
defineProps<{ placeholder?: string }>()
const model = defineModel<string>({ required: true })
</script>

<template>
  <div class="search">
    <svg viewBox="0 0 16 16">
      <circle cx="7" cy="7" r="5"></circle>
      <path d="M11 11l3.2 3.2"></path>
    </svg>
    <input v-model="model" :placeholder="placeholder ?? 'Search…'" />
  </div>
</template>
