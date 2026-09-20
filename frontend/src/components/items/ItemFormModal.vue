<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import Modal from '@/components/feedback/Modal.vue'
import type { ItemRequestBody } from '@/lib/api/items'
import { ApiError } from '@/lib/http'
import type { Item, TaxRate } from '@/lib/types'

const props = defineProps<{
  item: Item | null
  /** Active tax rates, for the default-tax picker. */
  taxRates: TaxRate[]
  submitting: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [ItemRequestBody]
}>()

const form = reactive({
  sku: props.item?.sku ?? '',
  name: props.item?.name ?? '',
  description: props.item?.description ?? '',
  defaultUnitPrice: props.item?.defaultUnitPrice != null ? String(props.item.defaultUnitPrice) : '',
  defaultTaxRateId: props.item?.defaultTaxRateId ?? null,
})

const submitAttempted = ref(false)
const serverError = ref('')

const nameError = computed(() => {
  if (!submitAttempted.value) return ''
  return form.name.trim() ? '' : 'A name is required.'
})

const priceError = computed(() => {
  if (!submitAttempted.value || !form.defaultUnitPrice.trim()) return ''
  const value = Number(form.defaultUnitPrice)
  return Number.isNaN(value) || value < 0 ? 'Must be a non-negative amount.' : ''
})

const isValid = computed(() => !nameError.value && !priceError.value)

function submit() {
  submitAttempted.value = true
  serverError.value = ''
  if (!isValid.value) return

  emit('submit', {
    sku: form.sku.trim() || null,
    name: form.name.trim(),
    description: form.description.trim() || null,
    defaultUnitPrice: form.defaultUnitPrice.trim() ? Number(form.defaultUnitPrice) : null,
    defaultTaxRateId: form.defaultTaxRateId,
  })
}

defineExpose({
  showServerError(error: unknown) {
    serverError.value = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
  },
})
</script>

<template>
  <Modal :title="item ? `Edit ${item.name}` : 'New item'" @close="emit('close')">
    <form @submit.prevent="submit">
      <div class="row">
        <div class="field" style="flex: 1">
          <label for="item-name">Name</label>
          <input id="item-name" v-model="form.name" class="input" :class="{ error: nameError }" />
          <div v-if="nameError" class="field-error">{{ nameError }}</div>
        </div>
        <div class="field" style="flex: 0 0 140px">
          <label for="item-sku">SKU</label>
          <input id="item-sku" v-model="form.sku" class="input" placeholder="Optional" />
        </div>
      </div>

      <div class="field">
        <label for="item-desc">Description</label>
        <input id="item-desc" v-model="form.description" class="input" placeholder="Optional" />
      </div>

      <div class="row">
        <div class="field" style="flex: 1">
          <label for="item-price">Default unit price</label>
          <input
            id="item-price"
            v-model="form.defaultUnitPrice"
            class="input"
            :class="{ error: priceError }"
            inputmode="decimal"
            placeholder="Optional"
          />
          <div v-if="priceError" class="field-error">{{ priceError }}</div>
        </div>
        <div class="field" style="flex: 1">
          <label for="item-tax">Default tax rate</label>
          <select id="item-tax" v-model="form.defaultTaxRateId" class="input">
            <option :value="null">— None —</option>
            <option v-for="t in taxRates" :key="t.id" :value="t.id">{{ t.name }} ({{ t.rate }}%)</option>
          </select>
        </div>
      </div>

      <div v-if="serverError" class="field-error server-error">{{ serverError }}</div>
    </form>

    <template #actions>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button type="button" class="btn btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? 'Saving…' : item ? 'Save changes' : 'Create item' }}
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
