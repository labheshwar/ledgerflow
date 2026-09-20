<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import Modal from '@/components/feedback/Modal.vue'
import type { ContactRequestBody } from '@/lib/api/contacts'
import { ApiError } from '@/lib/http'
import type { Contact, ContactType } from '@/lib/types'

const props = defineProps<{
  /** null creates a new contact; otherwise edits it. */
  contact: Contact | null
  submitting: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [ContactRequestBody]
}>()

const TYPES: ContactType[] = ['CUSTOMER', 'VENDOR', 'BOTH']

const form = reactive({
  type: props.contact?.type ?? 'CUSTOMER',
  name: props.contact?.name ?? '',
  email: props.contact?.email ?? '',
  phone: props.contact?.phone ?? '',
  taxId: props.contact?.taxId ?? '',
  addressLine1: props.contact?.addressLine1 ?? '',
  addressLine2: props.contact?.addressLine2 ?? '',
  city: props.contact?.city ?? '',
  state: props.contact?.state ?? '',
  postalCode: props.contact?.postalCode ?? '',
  country: props.contact?.country ?? '',
  notes: props.contact?.notes ?? '',
})

const submitAttempted = ref(false)
const serverError = ref('')

const nameError = computed(() => {
  if (!submitAttempted.value) return ''
  return form.name.trim() ? '' : 'A name is required.'
})

const isValid = computed(() => !nameError.value)

function blank(value: string): string | null {
  return value.trim() || null
}

function submit() {
  submitAttempted.value = true
  serverError.value = ''
  if (!isValid.value) return

  emit('submit', {
    type: form.type,
    name: form.name.trim(),
    email: blank(form.email),
    phone: blank(form.phone),
    taxId: blank(form.taxId),
    addressLine1: blank(form.addressLine1),
    addressLine2: blank(form.addressLine2),
    city: blank(form.city),
    state: blank(form.state),
    postalCode: blank(form.postalCode),
    country: blank(form.country),
    notes: blank(form.notes),
  })
}

defineExpose({
  showServerError(error: unknown) {
    serverError.value = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
  },
})
</script>

<template>
  <Modal :title="contact ? `Edit ${contact.name}` : 'New contact'" @close="emit('close')">
    <form @submit.prevent="submit">
      <div class="row">
        <div class="field" style="flex: 1">
          <label for="contact-name">Name</label>
          <input id="contact-name" v-model="form.name" class="input" :class="{ error: nameError }" />
          <div v-if="nameError" class="field-error">{{ nameError }}</div>
        </div>
        <div class="field" style="flex: 0 0 140px">
          <label for="contact-type">Type</label>
          <select id="contact-type" v-model="form.type" class="input">
            <option v-for="t in TYPES" :key="t" :value="t">{{ t }}</option>
          </select>
        </div>
      </div>

      <div class="row">
        <div class="field" style="flex: 1">
          <label for="contact-email">Email</label>
          <input id="contact-email" v-model="form.email" type="email" class="input" placeholder="Optional" />
        </div>
        <div class="field" style="flex: 1">
          <label for="contact-phone">Phone</label>
          <input id="contact-phone" v-model="form.phone" class="input" placeholder="Optional" />
        </div>
      </div>

      <div class="field">
        <label for="contact-tax-id">Tax ID</label>
        <input id="contact-tax-id" v-model="form.taxId" class="input" placeholder="Optional" />
      </div>

      <div class="field">
        <label for="contact-address1">Address</label>
        <input id="contact-address1" v-model="form.addressLine1" class="input" placeholder="Line 1" />
      </div>
      <div class="field">
        <input v-model="form.addressLine2" class="input" placeholder="Line 2 (optional)" />
      </div>

      <div class="row">
        <div class="field" style="flex: 1">
          <input v-model="form.city" class="input" placeholder="City" />
        </div>
        <div class="field" style="flex: 1">
          <input v-model="form.state" class="input" placeholder="State / province" />
        </div>
        <div class="field" style="flex: 0 0 110px">
          <input v-model="form.postalCode" class="input" placeholder="Postal code" />
        </div>
      </div>
      <div class="field">
        <input v-model="form.country" class="input" placeholder="Country" />
      </div>

      <div class="field">
        <label for="contact-notes">Notes</label>
        <textarea id="contact-notes" v-model="form.notes" class="input" rows="2" placeholder="Optional" />
      </div>

      <div v-if="serverError" class="field-error server-error">{{ serverError }}</div>
    </form>

    <template #actions>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button type="button" class="btn btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? 'Saving…' : contact ? 'Save changes' : 'Create contact' }}
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
