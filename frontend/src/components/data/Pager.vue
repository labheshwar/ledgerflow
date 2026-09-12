<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  size: number
  totalElements: number
  totalPages: number
  /** Plural noun for the count, e.g. "accounts". */
  noun?: string
}>()

const emit = defineEmits<{ 'update:page': [number] }>()

const first = computed(() => props.page * props.size + 1)
const last = computed(() => Math.min((props.page + 1) * props.size, props.totalElements))
</script>

<template>
  <div class="pager">
    <div class="info">Showing {{ first }}–{{ last }} of {{ totalElements }} {{ noun ?? 'results' }}</div>
    <div class="btns">
      <button class="pgbtn" :disabled="page <= 0" @click="emit('update:page', page - 1)">← Previous</button>
      <button class="pgbtn" :disabled="page >= totalPages - 1" @click="emit('update:page', page + 1)">
        Next →
      </button>
    </div>
  </div>
</template>
