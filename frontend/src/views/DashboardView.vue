<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import { apiFetch } from '../lib/api'
import { formatMoney, formatRelativeTime, reconciliationPillClass } from '../lib/format'
import type { DashboardSummary, TransactionListItem } from '../lib/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()

const summary = ref<DashboardSummary | null>(null)
const recentTransactions = ref<TransactionListItem[]>([])
const loading = ref(true)
const errorText = ref('')

const latestBatch = computed(() => summary.value?.latestReconciliation ?? null)

onMounted(async () => {
  try {
    const [summaryData, transactions] = await Promise.all([
      apiFetch<DashboardSummary>('/dashboard/summary'),
      apiFetch<TransactionListItem[]>('/transactions'),
    ])
    summary.value = summaryData
    recentTransactions.value = transactions.slice(0, 8)
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
    <template #sub>Ledger overview · all figures in PKR</template>
    <template #actions>
      <template v-if="auth.isAdmin">
        <RouterLink class="btn" to="/transactions/new">Post transaction</RouterLink>
        <RouterLink class="btn" to="/reconciliation">Run reconciliation</RouterLink>
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
        <div class="stat-card">
          <div class="label">Last reconciliation</div>
          <template v-if="latestBatch">
            <div class="value">
              <span :class="reconciliationPillClass(latestBatch.status)">{{ latestBatch.status }}</span>
            </div>
            <div class="delta">
              BATCH-{{ latestBatch.id }} · {{ formatRelativeTime(latestBatch.triggeredAt) }}
            </div>
          </template>
          <template v-else>
            <div class="value"><span class="pill pill-neutral">NONE</span></div>
            <div class="delta">No reconciliation run yet</div>
          </template>
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
