<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import AccountFormModal from '@/components/accounts/AccountFormModal.vue'
import AppShell from '../layouts/AppShell.vue'
import {
  accountKeys,
  archiveAccount,
  getAccount,
  getAccountEntries,
  getAccountTree,
  restoreAccount,
  updateAccount,
  type AccountRequestBody,
} from '../lib/api/accounts'
import {
  accountTypePillClass,
  directionPillClass,
  formatDate,
  formatMoney,
  signedAmount,
} from '../lib/format'
import { ApiError } from '../lib/http'
import type { AccountNode } from '../lib/types'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'

const route = useRoute()
const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()
const accountId = computed(() => route.params.id as string)

const {
  data: account,
  isPending: accountLoading,
  error: accountError,
} = useQuery({
  queryKey: computed(() => accountKeys.detail(accountId.value)),
  queryFn: ({ signal }) => getAccount(accountId.value, signal),
})

const { data: entriesData } = useQuery({
  queryKey: computed(() => accountKeys.entries(accountId.value)),
  queryFn: ({ signal }) => getAccountEntries(accountId.value, signal),
})
const entries = computed(() => entriesData.value ?? [])

// Only needed to populate the parent picker if the account is edited here.
const { data: tree } = useQuery({
  queryKey: accountKeys.tree(true),
  queryFn: ({ signal }) => getAccountTree(true, signal),
  enabled: computed(() => auth.isAdmin),
})

function flattenNodes(nodes: AccountNode[]): AccountNode['account'][] {
  return nodes.flatMap((node) => [node.account, ...flattenNodes(node.children)])
}

const parentName = computed(() => {
  if (!account.value?.parentId || !tree.value) return null
  return flattenNodes(tree.value).find((a) => a.id === account.value!.parentId)?.name ?? null
})

const page = ref(1)
const perPage = 8
const pageCount = computed(() => Math.max(1, Math.ceil(entries.value.length / perPage)))
const pagedEntries = computed(() => entries.value.slice((page.value - 1) * perPage, page.value * perPage))

// --- edit / archive / restore ---
const formOpen = ref(false)
const formRef = ref<InstanceType<typeof AccountFormModal> | null>(null)

function invalidate() {
  return Promise.all([queryClient.invalidateQueries({ queryKey: accountKeys.all })])
}

const saveMutation = useMutation({
  mutationFn: (body: AccountRequestBody) => updateAccount(accountId.value, body),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Account updated')
    formOpen.value = false
  },
  onError: (err) => formRef.value?.showServerError(err),
})

const archiveMutation = useMutation({
  mutationFn: () => archiveAccount(accountId.value),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Account archived')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not archive this account.'),
})

const restoreMutation = useMutation({
  mutationFn: () => restoreAccount(accountId.value),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Account restored')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not restore this account.'),
})
</script>

<template>
  <AppShell>
    <template #title>
      {{ account?.name ?? 'Account' }}
      <span v-if="account" :class="accountTypePillClass(account.type)">{{ account.type }}</span>
      <span v-if="account?.archived" class="pill pill-amber">archived</span>
    </template>
    <template #sub><RouterLink to="/accounts">← Back to the chart of accounts</RouterLink></template>
    <template #actions>
      <template v-if="auth.isAdmin && account">
        <button type="button" class="btn" @click="formOpen = true">Edit</button>
        <button v-if="!account.archived" type="button" class="btn" @click="archiveMutation.mutate()">
          Archive
        </button>
        <button v-else type="button" class="btn" @click="restoreMutation.mutate()">Restore</button>
      </template>
    </template>

    <p v-if="accountLoading">Loading…</p>
    <p v-else-if="accountError" class="field-error">Unable to load this account.</p>
    <template v-else-if="account">
      <div class="summary">
        <div class="card">
          <div class="label">
            {{ account.postable ? 'Current balance' : 'Total (this heading + children)' }}
          </div>
          <div class="balance mono">
            {{ formatMoney(account.postable ? account.balance : account.rollupBalance, account.currency) }}
          </div>
          <div class="hint">
            {{
              account.postable
                ? 'Derived from this account’s entries'
                : 'Sum of every account filed under this heading'
            }}
          </div>
        </div>
        <div class="card">
          <div class="label">Account details</div>
          <div class="meta-grid">
            <div>
              <div class="k">Code</div>
              <div class="v mono">{{ account.code }}</div>
            </div>
            <div>
              <div class="k">Currency</div>
              <div class="v">{{ account.currency }}</div>
            </div>
            <div>
              <div class="k">Parent</div>
              <div class="v">
                <RouterLink v-if="account.parentId" :to="`/accounts/${account.parentId}`">
                  {{ parentName ?? `#${account.parentId}` }}
                </RouterLink>
                <span v-else class="ink-faint">— top level —</span>
              </div>
            </div>
            <div>
              <div class="k">Posts entries</div>
              <div class="v">{{ account.postable ? 'Yes' : 'No — this is a heading' }}</div>
            </div>
            <div>
              <div class="k">System role</div>
              <div class="v">{{ account.systemRole ?? '—' }}</div>
            </div>
            <div>
              <div class="k">Opened</div>
              <div class="v">{{ formatDate(account.createdAt) }}</div>
            </div>
          </div>
          <div v-if="account.description" class="description">{{ account.description }}</div>
        </div>
      </div>

      <div class="sectionhead"><h2>Ledger entries</h2></div>
      <div class="tablecard">
        <table>
          <tr>
            <th>Transaction</th>
            <th>Direction</th>
            <th style="text-align: right">Amount</th>
            <th style="text-align: right">Running balance</th>
            <th>Date</th>
          </tr>
          <tr v-for="e in pagedEntries" :key="e.id">
            <td class="mono">
              <RouterLink :to="`/transactions/${e.transactionId}`">TXN-{{ e.transactionId }}</RouterLink>
            </td>
            <td>
              <span :class="directionPillClass(e.direction)">{{ e.direction }}</span>
            </td>
            <td class="num">{{ signedAmount(e.direction, e.amount, account?.currency) }}</td>
            <td class="num">{{ formatMoney(e.runningBalance, account?.currency) }}</td>
            <td class="mono" style="color: var(--ink-soft)">{{ formatDate(e.txnDate) }}</td>
          </tr>
        </table>
        <div v-if="entries.length === 0" class="empty">
          {{ account.postable ? 'No ledger entries yet.' : 'Headings never carry entries of their own.' }}
        </div>
        <div v-else class="pager">
          <div class="info">Page {{ page }} of {{ pageCount }} · {{ entries.length }} entries</div>
          <div class="btns">
            <button class="pgbtn" :disabled="page <= 1" @click="page--">← Previous</button>
            <button class="pgbtn" :disabled="page >= pageCount" @click="page++">Next →</button>
          </div>
        </div>
      </div>
    </template>

    <AccountFormModal
      v-if="formOpen && account"
      ref="formRef"
      :account="account"
      :all-accounts="tree ?? []"
      :submitting="saveMutation.isPending.value"
      @close="formOpen = false"
      @submit="(body) => saveMutation.mutate(body)"
    />
  </AppShell>
</template>

<style scoped>
.summary {
  display: grid;
  grid-template-columns: 1.1fr 1.4fr;
  gap: 16px;
  margin: 16px 0 24px;
}
.card {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 20px;
}
.card .label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--ink-soft);
  font-weight: 500;
  margin-bottom: 6px;
}
.balance {
  font-size: 32px;
  font-weight: 600;
}
.hint {
  font-size: 12px;
  color: var(--ink-faint);
  margin-top: 6px;
}
.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 20px;
}
.meta-grid .k {
  font-size: 11px;
  color: var(--ink-soft);
  margin-bottom: 3px;
}
.meta-grid .v {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 13px;
}
.description {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
  font-size: 13px;
  color: var(--ink-soft);
}
.ink-faint {
  color: var(--ink-faint);
}
</style>
