<script setup lang="ts">
export interface ChipOption {
  value: string
  label: string
}

defineProps<{ options: ChipOption[] }>()
const model = defineModel<string>({ required: true })
</script>

<template>
  <div class="filters">
    <button
      v-for="option in options"
      :key="option.value"
      :class="['chip', { on: model === option.value }]"
      type="button"
      @click="model = option.value"
    >
      {{ option.label }}
    </button>
  </div>
</template>
