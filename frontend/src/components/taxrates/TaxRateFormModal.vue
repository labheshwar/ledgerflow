<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import Modal from '@/components/feedback/Modal.vue'
import type { TaxRateRequestBody } from '@/lib/api/taxRates'
import { ApiError } from '@/lib/http'
import type { TaxRate } from '@/lib/types'

const props = defineProps<{
  taxRate: TaxRate | null
  submitting: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [TaxRateRequestBody]
}>()

const form = reactive({
  name: props.taxRate?.name ?? '',
  rate: props.taxRate ? String(props.taxRate.rate) : '',
})

const submitAttempted = ref(false)
const serverError = ref('')

const nameError = computed(() => {
  if (!submitAttempted.value) return ''
  return form.name.trim() ? '' : 'A name is required.'
})

const rateError = computed(() => {
  if (!submitAttempted.value) return ''
  const value = Number(form.rate)
  if (form.rate.trim() === '' || Number.isNaN(value)) return 'A percentage is required.'
  if (value < 0 || value > 100) return 'Must be between 0 and 100.'
  return ''
})

const isValid = computed(() => !nameError.value && !rateError.value)

function submit() {
  submitAttempted.value = true
  serverError.value = ''
  if (!isValid.value) return

  emit('submit', { name: form.name.trim(), rate: Number(form.rate) })
}

defineExpose({
  showServerError(error: unknown) {
    serverError.value = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
  },
})
</script>

<template>
  <Modal :title="taxRate ? `Edit ${taxRate.name}` : 'New tax rate'" @close="emit('close')">
    <form @submit.prevent="submit">
      <div class="row">
        <div class="field" style="flex: 1">
          <label for="taxrate-name">Name</label>
          <input id="taxrate-name" v-model="form.name" class="input" :class="{ error: nameError }" />
          <div v-if="nameError" class="field-error">{{ nameError }}</div>
        </div>
        <div class="field" style="flex: 0 0 120px">
          <label for="taxrate-rate">Rate (%)</label>
          <input
            id="taxrate-rate"
            v-model="form.rate"
            class="input"
            :class="{ error: rateError }"
            inputmode="decimal"
            placeholder="15"
          />
          <div v-if="rateError" class="field-error">{{ rateError }}</div>
        </div>
      </div>

      <div v-if="serverError" class="field-error server-error">{{ serverError }}</div>
    </form>

    <template #actions>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button type="button" class="btn btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? 'Saving…' : taxRate ? 'Save changes' : 'Create tax rate' }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.row {
  display: flex;
  gap: 12px;
}
.server-error {
  margin-top: 10px;
  padding: 8px 10px;
  background: var(--red-soft);
  border-radius: 5px;
}
</style>
