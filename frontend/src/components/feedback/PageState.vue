<script setup lang="ts">
import Skeleton from './Skeleton.vue'

defineProps<{
  loading: boolean
  error?: unknown
  /** When true and not loading, the empty slot renders instead of the content. */
  empty?: boolean
  errorText?: string
  skeletonRows?: number
}>()
</script>

<template>
  <Skeleton v-if="loading" :rows="skeletonRows ?? 5" />
  <p v-else-if="error" class="field-error">
    {{ errorText ?? (error instanceof Error ? error.message : 'Something went wrong.') }}
  </p>
  <slot v-else-if="empty" name="empty">
    <div class="empty">Nothing here yet.</div>
  </slot>
  <slot v-else />
</template>
