<script setup lang="ts">
defineProps<{ title: string; hint?: string }>()
</script>

<template>
  <div class="empty">
    <div>{{ title }}</div>
    <div v-if="hint" class="empty-hint">{{ hint }}</div>
    <slot name="action" />
  </div>
</template>

<style scoped>
.empty-hint {
  margin-top: 6px;
  font-size: 12px;
}
</style>
