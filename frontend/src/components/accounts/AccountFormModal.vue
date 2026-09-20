<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import Modal from '@/components/feedback/Modal.vue'
import { ApiError } from '@/lib/http'
import type { Account, AccountNode, AccountType, SystemAccountRole } from '@/lib/types'

/**
 * One form, two modes. The rules that constrain what can change differ by
 * what the account already *is* -- not by which mode this was opened in --
 * so the backend is the actual source of truth here; this form does not try
 * to duplicate its logic, only to make the common path pleasant and to show
 * whatever the backend refuses in plain language.
 */
const props = defineProps<{
  /** null creates a new account; otherwise edits it. */
  account: Account | null
  /** Flat list of every non-archived account, for the parent picker. */
  allAccounts: AccountNode[]
  submitting: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [
    {
      code: string
      name: string
      description: string | null
      type: AccountType
      currency: string | null
      parentId: number | null
      systemRole: SystemAccountRole | null
      postable: boolean
    },
  ]
}>()

const TYPES: AccountType[] = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE']
const ROLES: SystemAccountRole[] = [
  'CASH',
  'ACCOUNTS_RECEIVABLE',
  'ACCOUNTS_PAYABLE',
  'OWNER_EQUITY',
  'RETAINED_EARNINGS',
  'SALES_REVENUE',
  'TAX_PAYABLE',
  'TAX_RECEIVABLE',
  'FX_GAIN_LOSS',
  'ROUNDING',
  'CUSTOMER_PREPAYMENTS',
]

const form = reactive({
  code: props.account?.code ?? '',
  name: props.account?.name ?? '',
  description: props.account?.description ?? '',
  type: props.account?.type ?? 'ASSET',
  currency: props.account?.currency ?? '',
  parentId: props.account?.parentId ?? null,
  systemRole: props.account?.systemRole ?? null,
  postable: props.account?.postable ?? true,
})

const submitAttempted = ref(false)
const serverError = ref('')

function flatten(nodes: AccountNode[]): Account[] {
  return nodes.flatMap((node) => [node.account, ...flatten(node.children)])
}

/**
 * A parent has to be the same type as the child (the backend enforces this
 * too, but surfacing it in the picker means most mistakes are never
 * attempted rather than corrected after a 400). Editing an account also
 * excludes itself -- an account cannot be its own parent.
 */
const parentOptions = computed(() =>
  flatten(props.allAccounts).filter((a) => a.type === form.type && a.id !== props.account?.id && !a.archived),
)

// Switching type invalidates whatever parent was chosen for the old type.
watch(
  () => form.type,
  () => {
    if (form.parentId != null && !parentOptions.value.some((a) => a.id === form.parentId)) {
      form.parentId = null
    }
  },
)

const codeError = computed(() => {
  if (!submitAttempted.value) return ''
  if (!form.code.trim()) return 'A code is required.'
  if (!/^[A-Za-z0-9][A-Za-z0-9.-]*$/.test(form.code.trim())) {
    return 'Only letters, digits, dots and dashes are allowed.'
  }
  return ''
})

const nameError = computed(() => {
  if (!submitAttempted.value) return ''
  return form.name.trim() ? '' : 'A name is required.'
})

const isValid = computed(() => !codeError.value && !nameError.value)

function submit() {
  submitAttempted.value = true
  serverError.value = ''
  if (!isValid.value) return

  emit('submit', {
    code: form.code.trim(),
    name: form.name.trim(),
    description: form.description.trim() || null,
    type: form.type,
    currency: form.currency.trim() || null,
    parentId: form.parentId,
    systemRole: form.systemRole,
    postable: form.postable,
  })
}

defineExpose({
  showServerError(error: unknown) {
    serverError.value = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
  },
})
</script>

<template>
  <Modal :title="account ? `Edit ${account.name}` : 'New account'" @close="emit('close')">
    <form @submit.prevent="submit">
      <div class="row">
        <div class="field" style="flex: 0 0 120px">
          <label for="acct-code">Code</label>
          <input
            id="acct-code"
            v-model="form.code"
            class="input"
            :class="{ error: codeError }"
            autocomplete="off"
          />
          <div v-if="codeError" class="field-error">{{ codeError }}</div>
        </div>
        <div class="field" style="flex: 1">
          <label for="acct-name">Name</label>
          <input id="acct-name" v-model="form.name" class="input" :class="{ error: nameError }" />
          <div v-if="nameError" class="field-error">{{ nameError }}</div>
        </div>
      </div>

      <div class="field">
        <label for="acct-desc">Description</label>
        <input id="acct-desc" v-model="form.description" class="input" placeholder="Optional" />
      </div>

      <div class="row">
        <div class="field" style="flex: 1">
          <label for="acct-type">Type</label>
          <select id="acct-type" v-model="form.type" class="input">
            <option v-for="t in TYPES" :key="t" :value="t">{{ t }}</option>
          </select>
        </div>
        <div class="field" style="flex: 1">
          <label for="acct-currency">Currency</label>
          <input
            id="acct-currency"
            v-model="form.currency"
            class="input"
            placeholder="Org default"
            maxlength="3"
          />
        </div>
      </div>

      <div class="field">
        <label for="acct-parent">Parent (heading)</label>
        <select id="acct-parent" v-model="form.parentId" class="input">
          <option :value="null">— None, this is a top-level account —</option>
          <option v-for="p in parentOptions" :key="p.id" :value="p.id">{{ p.code }} · {{ p.name }}</option>
        </select>
      </div>

      <div class="field">
        <label for="acct-role">System role</label>
        <select id="acct-role" v-model="form.systemRole" class="input">
          <option :value="null">— None —</option>
          <option v-for="r in ROLES" :key="r" :value="r">{{ r }}</option>
        </select>
      </div>

      <label class="checkbox-field">
        <input v-model="form.postable" type="checkbox" />
        Entries can be posted directly to this account
      </label>

      <div v-if="serverError" class="field-error server-error">{{ serverError }}</div>
    </form>

    <template #actions>
      <button type="button" class="btn" @click="emit('close')">Cancel</button>
      <button type="button" class="btn btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? 'Saving…' : account ? 'Save changes' : 'Create account' }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.row {
  display: flex;
  gap: 12px;
}
.checkbox-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ink-soft);
  margin: 6px 0 4px;
}
.server-error {
  margin-top: 10px;
  padding: 8px 10px;
  background: var(--red-soft);
  border-radius: 5px;
}
</style>
