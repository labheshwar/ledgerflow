<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import Modal from '@/components/feedback/Modal.vue'
import type { BankAccountRequestBody } from '@/lib/api/bankAccounts'
import { ApiError } from '@/lib/http'
import type { Account, BankAccount } from '@/lib/types'

const props = defineProps<{
  bankAccount: BankAccount | null
  /** Postable, non-archived accounts only -- see lib/accounts.ts. */
  accounts: Account[]
  submitting: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [BankAccountRequestBody]
}>()

const form = reactive({
  accountId: props.bankAccount?.accountId ?? props.accounts[0]?.id ?? null,
  name: props.bankAccount?.name ?? '',
  accountNumberLast4: props.bankAccount?.accountNumberLast4 ?? '',
})

const submitAttempted = ref(false)
const serverError = ref('')

const accountError = computed(() =>
  submitAttempted.value && form.accountId == null ? 'An account is required.' : '',
)
const nameError = computed(() => {
  if (!submitAttempted.value) return ''
  return form.name.trim() ? '' : 'A name is required.'
})
const last4Error = computed(() => {
  if (!submitAttempted.value || !form.accountNumberLast4.trim()) return ''
  return /^\d{4}$/.test(form.accountNumberLast4.trim()) ? '' : 'Must be exactly 4 digits.'
})

const isValid = computed(() => !accountError.value && !nameError.value && !last4Error.value)

function submit() {
  submitAttempted.value = true
  serverError.value = ''
  if (!isValid.value) return

  emit('submit', {
    accountId: form.accountId as number,
    name: form.name.trim(),
    accountNumberLast4: form.accountNumberLast4.trim() || null,
  })
}

defineExpose({
  showServerError(error: unknown) {
    serverError.value = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
  },
})
</script>

<template>
  <Modal :title="bankAccount ? `Edit ${bankAccount.name}` : 'New bank account'" @close="emit('close')">
    <form @submit.prevent="submit">
      <div class="field">
        <label for="bank-account-name">Name</label>
        <input
          id="bank-account-name"
          v-model="form.name"
          class="input"
          :class="{ error: nameError }"
          placeholder="e.g. Business Checking"
        />
        <div v-if="nameError" class="field-error">{{ nameError }}</div>
      </div>

      <div class="row">
        <div class="field" style="flex: 1">
          <label for="bank-account-gl">Ledger account</label>
          <template v-if="bankAccount">
            <input class="input" :value="bankAccount.accountName" disabled />
          </template>
          <template v-else>
            <select
              id="bank-account-gl"
              v-model.number="form.accountId"
              class="input"
              :class="{ error: accountError }"
            >
              <option :value="null">— Select an account —</option>
              <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.code }} {{ a.name }}</option>
            </select>
            <div v-if="accountError" class="field-error">{{ accountError }}</div>
          </template>
        </div>
        <div class="field" style="flex: 0 0 140px">
          <label for="bank-account-last4">Last 4 digits</label>
          <input
            id="bank-account-last4"
            v-model="form.accountNumberLast4"
            class="input"
            :class="{ error: last4Error }"
            placeholder="Optional"
            maxlength="4"
          />
          <div v-if="last4Error" class="field-error">{{ last4Error }}</div>
        </div>
      </div>

      <div v-if="serverError" class="field-error server-error">{{ serverError }}</div>
    </form>

    <template #actions>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button type="button" class="btn btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? 'Saving…' : bankAccount ? 'Save changes' : 'Create bank account' }}
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
