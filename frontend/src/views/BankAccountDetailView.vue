<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import BankAccountFormModal from '@/components/bankAccounts/BankAccountFormModal.vue'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import { getAccountTree } from '@/lib/api/accounts'
import {
  bankAccountKeys,
  getBankAccount,
  listCommittedLines,
  listImports,
  updateBankAccount,
  type BankAccountRequestBody,
} from '@/lib/api/bankAccounts'
import { flattenPostableAccounts } from '@/lib/accounts'
import { formatDate, formatDateTime, formatMoney } from '@/lib/format'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import type { StatementImport } from '@/lib/types'

const route = useRoute()
const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()
const bankAccountId = computed(() => route.params.id as string)

const {
  data: bankAccount,
  isPending,
  error,
} = useQuery({
  queryKey: computed(() => bankAccountKeys.detail(bankAccountId.value)),
  queryFn: ({ signal }) => getBankAccount(bankAccountId.value, signal),
})

const { data: imports } = useQuery({
  queryKey: computed(() => bankAccountKeys.imports(bankAccountId.value)),
  queryFn: ({ signal }) => listImports(bankAccountId.value, { size: 10 }, signal),
})

const { data: lines } = useQuery({
  queryKey: computed(() => bankAccountKeys.lines(bankAccountId.value)),
  queryFn: ({ signal }) => listCommittedLines(bankAccountId.value, { size: 20 }, signal),
})

const { data: accountTree } = useQuery({
  queryKey: ['accounts', 'tree', false],
  queryFn: ({ signal }) => getAccountTree(false, signal),
})
const accounts = computed(() => flattenPostableAccounts(accountTree.value ?? []))

const formOpen = ref(false)
const formRef = ref<InstanceType<typeof BankAccountFormModal> | null>(null)

const saveMutation = useMutation({
  mutationFn: (body: BankAccountRequestBody) => updateBankAccount(bankAccountId.value, body),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: bankAccountKeys.all })
    toasts.success('Bank account updated')
    formOpen.value = false
  },
  onError: (err) => formRef.value?.showServerError(err),
})

function statusPillClass(status: StatementImport['status']): string {
  if (status === 'COMMITTED') return 'pill pill-green'
  if (status === 'FAILED') return 'pill pill-red'
  if (status === 'PREVIEWED') return 'pill pill-amber'
  return 'pill pill-neutral'
}
</script>

<template>
  <AppShell>
    <template #title>
      <template v-if="bankAccount">{{ bankAccount.name }}</template>
      <template v-else>Bank account</template>
    </template>
    <template #sub><RouterLink to="/bank-accounts">← Back to bank accounts</RouterLink></template>
    <template #actions>
      <template v-if="auth.isAdmin && bankAccount">
        <button type="button" class="btn" @click="formOpen = true">Edit</button>
        <RouterLink class="btn" :to="`/bank-accounts/${bankAccountId}/reconcile`"> Reconcile </RouterLink>
        <RouterLink class="btn btn-primary" :to="`/bank-accounts/${bankAccountId}/imports/new`">
          + Import statement
        </RouterLink>
      </template>
    </template>

    <PageState :loading="isPending" :error="error" error-text="Unable to load this bank account.">
      <template v-if="bankAccount">
        <div class="layout">
          <div class="card">
            <h2>Imports</h2>
            <div v-if="imports && imports.content.length === 0" class="notes" style="margin-top: 0">
              No statements imported yet.
            </div>
            <div v-for="imp in imports?.content ?? []" :key="imp.id" class="entry-row">
              <RouterLink :to="`/bank-accounts/${bankAccountId}/imports/${imp.id}`">
                {{ imp.originalFilename }}
              </RouterLink>
              <span class="meta">{{ formatDateTime(imp.createdAt) }}</span>
              <span :class="statusPillClass(imp.status)">{{ imp.status }}</span>
            </div>

            <h2 style="margin-top: 24px">Statement</h2>
            <div v-if="lines && lines.content.length === 0" class="notes" style="margin-top: 0">
              Nothing committed yet.
            </div>
            <div v-if="lines && lines.content.length > 0" class="row-head">
              <div>Date</div>
              <div>Description</div>
              <div style="text-align: right">Amount</div>
              <div></div>
            </div>
            <div v-for="line in lines?.content ?? []" :key="line.id" class="line-row">
              <div class="meta">{{ formatDate(line.txnDate) }}</div>
              <div>{{ line.description }}</div>
              <div class="amt" :class="{ negative: line.amount < 0 }">
                {{ formatMoney(line.amount, bankAccount.currency) }}
              </div>
              <span :class="line.matchedEntryId ? 'pill pill-green' : 'pill pill-amber'">
                {{ line.matchedEntryId ? 'Matched' : 'Unmatched' }}
              </span>
            </div>
          </div>

          <div class="card">
            <h2>Details</h2>
            <div class="meta-row">
              <span class="k">Ledger account</span><span class="v">{{ bankAccount.accountName }}</span>
            </div>
            <div class="meta-row">
              <span class="k">Account #</span>
              <span class="v">{{
                bankAccount.accountNumberLast4 ? `•••• ${bankAccount.accountNumberLast4}` : '—'
              }}</span>
            </div>
            <div class="meta-row">
              <span class="k">Currency</span><span class="v">{{ bankAccount.currency }}</span>
            </div>
            <div class="meta-row">
              <span class="k">Status</span>
              <span :class="bankAccount.archived ? 'pill pill-amber' : 'pill pill-green'">
                {{ bankAccount.archived ? 'Archived' : 'Active' }}
              </span>
            </div>
          </div>
        </div>
      </template>
    </PageState>

    <BankAccountFormModal
      v-if="formOpen && bankAccount"
      ref="formRef"
      :bank-account="bankAccount"
      :accounts="accounts"
      :submitting="saveMutation.isPending.value"
      @close="formOpen = false"
      @submit="(body) => saveMutation.mutate(body)"
    />
  </AppShell>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 20px;
  align-items: start;
}
.card {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 20px;
}
.card h2 {
  font-family: 'Source Serif 4', serif;
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 14px;
}
.entry-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 2px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
}
.meta {
  color: var(--ink-soft);
  font-size: 11.5px;
  font-family: 'IBM Plex Mono', monospace;
}
.row-head {
  display: grid;
  grid-template-columns: 100px 1fr 110px 90px;
  gap: 10px;
  padding: 8px 2px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--ink-soft);
  border-top: 1px solid var(--line);
  margin-top: 8px;
}
.line-row {
  display: grid;
  grid-template-columns: 100px 1fr 110px 90px;
  gap: 10px;
  align-items: center;
  padding: 6px 2px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
}
.amt {
  font-family: 'IBM Plex Mono', monospace;
  text-align: right;
}
.amt.negative {
  color: var(--red);
}
.notes {
  color: var(--ink-soft);
  font-size: 12.5px;
}
.meta-row {
  display: flex;
  justify-content: space-between;
  padding: 7px 0;
  border-bottom: 1px solid var(--line-soft);
  font-size: 12.5px;
  gap: 12px;
}
.meta-row:last-child {
  border-bottom: none;
}
.meta-row .k {
  color: var(--ink-soft);
  flex: none;
}
.meta-row .v {
  text-align: right;
}
</style>
