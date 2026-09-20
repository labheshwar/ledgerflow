<script setup lang="ts">
import { useEscapeKey } from '@/composables/useEscapeKey'

const props = defineProps<{ title: string }>()
const emit = defineEmits<{ close: [] }>()

useEscapeKey(() => emit('close'))
</script>

<template>
  <Teleport to="body">
    <div class="modal-backdrop" @mousedown.self="emit('close')">
      <div class="modal-card" role="dialog" aria-modal="true" :aria-label="props.title">
        <div class="modal-head">
          <h2>{{ props.title }}</h2>
          <button type="button" class="modal-close" aria-label="Close" @click="emit('close')">×</button>
        </div>
        <div class="modal-body">
          <slot />
        </div>
        <div v-if="$slots.actions" class="modal-actions">
          <slot name="actions" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 100;
}
.modal-card {
  background: var(--raised);
  border: 1px solid var(--line);
  border-radius: 8px;
  width: 100%;
  max-width: 480px;
  max-height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
}
.modal-head h2 {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}
.modal-close {
  background: none;
  border: none;
  font-size: 20px;
  line-height: 1;
  color: var(--ink-soft);
  cursor: pointer;
  padding: 2px 6px;
}
.modal-close:hover {
  color: var(--ink);
}
.modal-body {
  padding: 20px;
  overflow-y: auto;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--line);
}
</style>
