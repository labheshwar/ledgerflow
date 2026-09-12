<script setup lang="ts">
withDefaults(defineProps<{ rows?: number }>(), { rows: 5 })
</script>

<template>
  <div class="skeleton" aria-hidden="true">
    <div v-for="row in rows" :key="row" class="skeleton-row"></div>
  </div>
</template>

<style scoped>
.skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}
.skeleton-row {
  height: 14px;
  border-radius: 4px;
  background: var(--line-soft);
  animation: skeleton-pulse 1.4s ease-in-out infinite;
}
.skeleton-row:nth-child(even) {
  width: 85%;
}
.skeleton-row:nth-child(3n) {
  width: 70%;
}
@keyframes skeleton-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}
@media (prefers-reduced-motion: reduce) {
  .skeleton-row {
    animation: none;
  }
}
</style>
