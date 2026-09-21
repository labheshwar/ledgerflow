<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import DataTable, { type Column } from '@/components/data/DataTable.vue'
import Pager from '@/components/data/Pager.vue'
import SearchInput from '@/components/data/SearchInput.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import BankAccountFormModal from '@/components/bankAccounts/BankAccountFormModal.vue'
import { useListQuery } from '@/composables/useListQuery'
import AppShell from '@/layouts/AppShell.vue'
import { getAccountTree } from '@/lib/api/accounts'
import {
  archiveBankAccount,
  bankAccountKeys,
  createBankAccount,
  listBankAccounts,
  restoreBankAccount,
  updateBankAccount,
  type BankAccountRequestBody,
} from '@/lib/api/bankAccounts'
import { flattenPostableAccounts } from '@/lib/accounts'
import { ApiError } from '@/lib/http'
import type { BankAccount } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const COLUMNS: Column[] = [
  { key: 'name', label: 'Name', sortBy: 'name' },
  { key: 'accountName', label: 'Ledger account' },
  { key: 'accountNumberLast4', label: 'Account #' },
  { key: 'archived', label: 'Status' },
  { key: 'actions', label: '' },
]

const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()

const { state, params, qInput, setFilter, setPage, setSort } = useListQuery({
  defaultSort: 'name,asc',
  filters: { includeArchived: 'false' },
})

const listParams = computed(() => ({
  q: params.value.q,
  includeArchived: params.value.includeArchived === 'true',
  page: params.value.page,
  size: params.value.size,
  sort: params.value.sort,
}))

const { data, isPending, error } = useQuery({
  queryKey: computed(() => bankAccountKeys.list(listParams.value)),
  queryFn: ({ signal }) => listBankAccounts(listParams.value, signal),
})

const { data: accountTree } = useQuery({
  queryKey: ['accounts', 'tree', false],
  queryFn: ({ signal }) => getAccountTree(false, signal),
})
const accounts = computed(() => flattenPostableAccounts(accountTree.value ?? []))

const includeArchivedFilter = computed({
  get: () => state.value.includeArchived === 'true',
  set: (value: boolean) => setFilter('includeArchived', value ? 'true' : 'false'),
})

function invalidate() {
  return queryClient.invalidateQueries({ queryKey: bankAccountKeys.all })
}

const formOpen = ref(false)
const editingBankAccount = ref<BankAccount | null>(null)
const formRef = ref<InstanceType<typeof BankAccountFormModal> | null>(null)

function openCreate() {
  editingBankAccount.value = null
  formOpen.value = true
}
function openEdit(bankAccount: BankAccount) {
  editingBankAccount.value = bankAccount
  formOpen.value = true
}

const saveMutation = useMutation({
  mutationFn: (body: BankAccountRequestBody) =>
    editingBankAccount.value ? updateBankAccount(editingBankAccount.value.id, body) : createBankAccount(body),
  onSuccess: async () => {
    await invalidate()
    toasts.success(editingBankAccount.value ? 'Bank account updated' : 'Bank account created')
    formOpen.value = false
  },
  onError: (err) => formRef.value?.showServerError(err),
})

const archiveMutation = useMutation({
  mutationFn: (id: number) => archiveBankAccount(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Bank account archived')
  },
  onError: (err) =>
    toasts.error(err instanceof ApiError ? err.message : 'Could not archive that bank account.'),
})

const restoreMutation = useMutation({
  mutationFn: (id: number) => restoreBankAccount(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Bank account restored')
  },
  onError: (err) =>
    toasts.error(err instanceof ApiError ? err.message : 'Could not restore that bank account.'),
})
</script>

<template>
  <AppShell>
    <template #title>Bank accounts</template>
    <template #sub>
      <span v-if="data">{{ data.totalElements }} bank accounts</span>
    </template>
    <template #actions>
      <button v-if="auth.isAdmin" type="button" class="btn btn-primary" @click="openCreate()">
        + New bank account
      </button>
    </template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Search name…" />
      <label class="checkbox-inline">
        <input v-model="includeArchivedFilter" type="checkbox" />
        Include archived
      </label>
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.content.length === 0"
        error-text="Unable to load bank accounts."
        :skeleton-rows="6"
      >
        <template #empty>
          <EmptyState title="No bank accounts yet." hint="Create one to import a statement for it." />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: BankAccount) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:name="{ row }">
            <RouterLink :to="`/bank-accounts/${row.id}`">{{ row.name }}</RouterLink>
          </template>
          <template #cell:accountNumberLast4="{ row }">{{
            row.accountNumberLast4 ? `•••• ${row.accountNumberLast4}` : '—'
          }}</template>
          <template #cell:archived="{ row }">
            <span :class="row.archived ? 'pill pill-amber' : 'pill pill-green'">
              {{ row.archived ? 'Archived' : 'Active' }}
            </span>
          </template>
          <template #cell:actions="{ row }">
            <div v-if="auth.isAdmin" style="display: flex; gap: 10px; justify-content: flex-end">
              <button class="linkbtn" type="button" @click="openEdit(row)">Edit</button>
              <button
                v-if="!row.archived"
                class="linkbtn"
                type="button"
                @click="archiveMutation.mutate(row.id)"
              >
                Archive
              </button>
              <button v-else class="linkbtn" type="button" @click="restoreMutation.mutate(row.id)">
                Restore
              </button>
            </div>
          </template>
        </DataTable>

        <Pager
          v-if="data && data.totalPages > 1"
          :page="data.page"
          :size="data.size"
          :total-elements="data.totalElements"
          :total-pages="data.totalPages"
          noun="bank accounts"
          @update:page="setPage"
        />
      </PageState>
    </div>

    <BankAccountFormModal
      v-if="formOpen"
      ref="formRef"
      :bank-account="editingBankAccount"
      :accounts="accounts"
      :submitting="saveMutation.isPending.value"
      @close="formOpen = false"
      @submit="(body) => saveMutation.mutate(body)"
    />
  </AppShell>
</template>

<style scoped>
.checkbox-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--ink-soft);
}
.linkbtn {
  background: none;
  border: none;
  color: var(--green);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}
.linkbtn:hover {
  text-decoration: underline;
}
</style>
