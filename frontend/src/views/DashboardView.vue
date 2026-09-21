<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import { apiFetch } from '../lib/api'
import { formatMoney, formatRelativeTime } from '../lib/format'
import type { DashboardSummary, Paged, TransactionListItem } from '../lib/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()

const summary = ref<DashboardSummary | null>(null)
const recentTransactions = ref<TransactionListItem[]>([])
const loading = ref(true)
const errorText = ref('')

onMounted(async () => {
  try {
    const [summaryData, transactions] = await Promise.all([
      apiFetch<DashboardSummary>('/dashboard/summary'),
      apiFetch<Paged<TransactionListItem>>('/transactions?size=8'),
    ])
    summary.value = summaryData
    recentTransactions.value = transactions.content
  } catch {
    errorText.value = 'Unable to load dashboard data.'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <AppShell>
    <template #title>Dashboard</template>
    <template #sub>Ledger overview · all figures in USD</template>
    <template #actions>
      <template v-if="auth.isAdmin">
        <RouterLink class="btn" to="/transactions/new">Post journal entry</RouterLink>
        <RouterLink class="btn" to="/periods">Periods</RouterLink>
        <RouterLink class="btn" to="/bank-accounts">Bank accounts</RouterLink>
      </template>
      <RouterLink class="btn btn-primary" to="/accounts">View accounts</RouterLink>
    </template>

    <p v-if="loading">Loading…</p>
    <p v-else-if="errorText" class="field-error">{{ errorText }}</p>
    <template v-else-if="summary">
      <div class="stat-row">
        <div class="stat-card">
          <div class="label">Total accounts</div>
          <div class="value mono">{{ summary.totalAccounts }}</div>
        </div>
        <div class="stat-card">
          <div class="label">Total ledger balance</div>
          <div class="value mono">{{ formatMoney(summary.totalLedgerBalance) }}</div>
          <div class="delta">across all account types</div>
        </div>
        <div class="stat-card">
          <div class="label">Postings today</div>
          <div class="value mono">{{ summary.postingsToday }}</div>
        </div>
      </div>

      <div class="sectionhead">
        <h2>Recent activity</h2>
        <RouterLink to="/transactions" class="mono" style="font-size: 12px">View all →</RouterLink>
      </div>
      <div class="tablecard">
        <table>
          <tr>
            <th>Reference</th>
            <th>Detail</th>
            <th>Status</th>
            <th>Time</th>
          </tr>
          <tr v-for="txn in recentTransactions" :key="txn.id">
            <td class="mono">
              <RouterLink :to="`/transactions/${txn.id}`">TXN-{{ txn.id }}</RouterLink>
            </td>
            <td>{{ txn.description || '—' }}</td>
            <td>
              <span class="pill pill-green">{{ txn.status }}</span>
            </td>
            <td class="mono" style="color: var(--ink-soft)">{{ formatRelativeTime(txn.createdAt) }}</td>
          </tr>
        </table>
        <div v-if="recentTransactions.length === 0" class="empty">No transactions yet.</div>
      </div>
    </template>
  </AppShell>
</template>
