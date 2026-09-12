<script setup lang="ts">
import { useToastStore } from '@/stores/toast'

const toasts = useToastStore()
</script>

<template>
  <div class="toast-host" role="status" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="toast in toasts.toasts" :key="toast.id" :class="['toast', `toast-${toast.tone}`]">
        <span>{{ toast.message }}</span>
        <button class="toast-close" aria-label="Dismiss" @click="toasts.dismiss(toast.id)">×</button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}
.toast {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 260px;
  max-width: 400px;
  padding: 11px 12px 11px 14px;
  border-radius: 6px;
  border: 1px solid var(--line);
  background: var(--raised);
  color: var(--ink);
  font-size: 12.5px;
  box-shadow: 0 2px 10px rgba(27, 36, 32, 0.12);
}
.toast-success {
  border-color: var(--green-line);
  background: var(--green-soft);
  color: var(--green);
}
.toast-error {
  border-color: var(--red-line);
  background: var(--red-soft);
  color: var(--red);
}
.toast-close {
  margin-left: auto;
  border: none;
  background: none;
  color: inherit;
  opacity: 0.6;
  font-size: 15px;
  line-height: 1;
  padding: 0;
}
.toast-close:hover {
  opacity: 1;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>
