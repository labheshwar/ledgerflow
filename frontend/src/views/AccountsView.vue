<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import AccountFormModal from '@/components/accounts/AccountFormModal.vue'
import AccountTreeNode from '@/components/accounts/AccountTreeNode.vue'
import FilterChips from '@/components/data/FilterChips.vue'
import SearchInput from '@/components/data/SearchInput.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import {
  accountKeys,
  archiveAccount,
  createAccount,
  deleteAccount,
  getAccountTree,
  restoreAccount,
  updateAccount,
  type AccountRequestBody,
} from '@/lib/api/accounts'
import { confirmDialog } from '@/lib/dialogs'
import { ApiError } from '@/lib/http'
import type { Account, AccountNode } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()

const qInput = ref('')
const includeArchived = ref(false)

const ARCHIVED_OPTIONS = [
  { value: 'active', label: 'Active' },
  { value: 'all', label: 'Include archived' },
]
const archivedFilter = computed({
  get: () => (includeArchived.value ? 'all' : 'active'),
  set: (value: string) => (includeArchived.value = value === 'all'),
})

const { data, isPending, error } = useQuery({
  queryKey: computed(() => accountKeys.tree(includeArchived.value)),
  queryFn: ({ signal }) => getAccountTree(includeArchived.value, signal),
})

function countAccounts(nodes: AccountNode[]): number {
  return nodes.reduce((sum, n) => sum + 1 + countAccounts(n.children), 0)
}
function flatten(nodes: AccountNode[]): AccountNode[] {
  return nodes.flatMap((n) => [n, ...flatten(n.children)])
}
const flatNodes = computed(() => flatten(data.value ?? []))

// --- create / edit modal ---
const formOpen = ref(false)
const editingAccount = ref<Account | null>(null)
const presetParentId = ref<number | null>(null)
const formRef = ref<InstanceType<typeof AccountFormModal> | null>(null)

function openCreate(parentNode?: AccountNode) {
  editingAccount.value = null
  presetParentId.value = parentNode?.account.id ?? null
  formOpen.value = true
}
function openEdit(node: AccountNode) {
  editingAccount.value = node.account
  presetParentId.value = null
  formOpen.value = true
}
function closeForm() {
  formOpen.value = false
}

function invalidateTree() {
  return queryClient.invalidateQueries({ queryKey: accountKeys.all })
}

const saveMutation = useMutation({
  mutationFn: (body: AccountRequestBody) =>
    editingAccount.value ? updateAccount(editingAccount.value.id, body) : createAccount(body),
  onSuccess: async () => {
    await invalidateTree()
    toasts.success(editingAccount.value ? 'Account updated' : 'Account created')
    formOpen.value = false
  },
  onError: (err) => formRef.value?.showServerError(err),
})

function handleSubmit(body: AccountRequestBody) {
  saveMutation.mutate(presetParentId.value != null ? { ...body, parentId: presetParentId.value } : body)
}

// --- archive / restore / delete ---
const archiveMutation = useMutation({
  mutationFn: (id: number) => archiveAccount(id),
  onSuccess: async () => {
    await invalidateTree()
    toasts.success('Account archived')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not archive that account.'),
})

const restoreMutation = useMutation({
  mutationFn: (id: number) => restoreAccount(id),
  onSuccess: async () => {
    await invalidateTree()
    toasts.success('Account restored')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not restore that account.'),
})

const deleteMutation = useMutation({
  mutationFn: (id: number) => deleteAccount(id),
  onSuccess: async () => {
    await invalidateTree()
    toasts.success('Account deleted')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not delete that account.'),
})

function onArchive(node: AccountNode) {
  archiveMutation.mutate(node.account.id)
}
function onRestore(node: AccountNode) {
  restoreMutation.mutate(node.account.id)
}
function onRemove(node: AccountNode) {
  if (!confirmDialog(`Delete ${node.account.name}? This only works if it has never been posted to.`)) return
  deleteMutation.mutate(node.account.id)
}
</script>

<template>
  <AppShell>
    <template #title>Chart of accounts</template>
    <template #sub>
      <span v-if="data">{{ countAccounts(data) }} accounts</span>
    </template>
    <template #actions>
      <button v-if="auth.isAdmin" type="button" class="btn btn-primary" @click="openCreate()">
        + New account
      </button>
    </template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Filter by code or name…" />
      <FilterChips v-model="archivedFilter" :options="ARCHIVED_OPTIONS" />
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.length === 0"
        error-text="Unable to load the chart of accounts."
        :skeleton-rows="8"
      >
        <template #empty>
          <EmptyState title="No accounts yet." hint="Create the first account to start posting." />
        </template>

        <table v-if="data">
          <thead>
            <tr>
              <th>Account</th>
              <th>Type</th>
              <th>Currency</th>
              <th class="num">Balance</th>
              <th class="num">Total (incl. children)</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <AccountTreeNode
              v-for="node in data"
              :key="node.account.id"
              :node="node"
              :depth="0"
              :is-admin="auth.isAdmin"
              :filter-term="qInput.trim()"
              @edit="openEdit"
              @add-child="openCreate"
              @archive="onArchive"
              @restore="onRestore"
              @remove="onRemove"
            />
          </tbody>
        </table>
        <div v-if="data && flatNodes.length === 0" class="empty">No accounts match that filter.</div>
      </PageState>
    </div>

    <AccountFormModal
      v-if="formOpen"
      ref="formRef"
      :account="editingAccount"
      :all-accounts="data ?? []"
      :submitting="saveMutation.isPending.value"
      @close="closeForm"
      @submit="handleSubmit"
    />
  </AppShell>
</template>

<style scoped>
table {
  width: 100%;
  border-collapse: collapse;
}
th {
  text-align: left;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--ink-soft);
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
th.num {
  text-align: right;
}
td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
  vertical-align: middle;
}
td.num {
  text-align: right;
}
</style>
