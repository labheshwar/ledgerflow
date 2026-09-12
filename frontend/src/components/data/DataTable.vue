<script setup lang="ts" generic="T">
import { computed } from 'vue'

export interface Column {
  key: string
  label: string
  align?: 'left' | 'right'
  /** Property name the API sorts by; omit to make the column unsortable. */
  sortBy?: string
  class?: string
}

const props = defineProps<{
  columns: Column[]
  rows: T[]
  rowKey: (row: T) => string | number
  /** "property,direction" as the API expects it. */
  sort?: string
}>()

const emit = defineEmits<{ 'update:sort': [string] }>()

const current = computed(() => {
  const [property, direction] = (props.sort ?? '').split(',')
  return { property, direction: direction === 'desc' ? 'desc' : 'asc' }
})

function toggle(column: Column) {
  if (!column.sortBy) return
  const nextDirection =
    current.value.property === column.sortBy && current.value.direction === 'asc' ? 'desc' : 'asc'
  emit('update:sort', `${column.sortBy},${nextDirection}`)
}

function indicator(column: Column) {
  if (!column.sortBy || current.value.property !== column.sortBy) return ''
  return current.value.direction === 'asc' ? '↑' : '↓'
}

/** Fallback for columns without a cell slot. */
function cellValue(row: T, key: string): unknown {
  return (row as Record<string, unknown>)[key]
}
</script>

<template>
  <table>
    <thead>
      <tr>
        <th
          v-for="column in columns"
          :key="column.key"
          :style="column.align === 'right' ? 'text-align: right' : undefined"
        >
          <button v-if="column.sortBy" class="sort-btn" type="button" @click="toggle(column)">
            {{ column.label }}<span class="sort-arrow">{{ indicator(column) }}</span>
          </button>
          <template v-else>{{ column.label }}</template>
        </th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="rowKey(row)">
        <td
          v-for="column in columns"
          :key="column.key"
          :class="[column.class, { num: column.align === 'right' }]"
        >
          <slot :name="`cell:${column.key}`" :row="row">{{ cellValue(row, column.key) }}</slot>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
.sort-btn {
  font: inherit;
  color: inherit;
  letter-spacing: inherit;
  text-transform: inherit;
  background: none;
  border: none;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.sort-btn:hover {
  color: var(--ink);
}
.sort-arrow {
  width: 8px;
  display: inline-block;
}
</style>
