<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import AppShell from '../layouts/AppShell.vue'
import { agingKeys, listArAging, listApAging } from '../lib/api/aging'
import { dashboardKeys, getDashboardSummary } from '../lib/api/dashboard'
import { transactionKeys, listTransactions } from '../lib/api/transactions'
import { formatMoney, formatRelativeTime } from '../lib/format'
import type { AgingBucket } from '../lib/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()

const {
  data: summary,
  isPending,
  error,
} = useQuery({
  queryKey: dashboardKeys.summary(),
  queryFn: ({ signal }) => getDashboardSummary(signal),
})

const { data: transactionsPage } = useQuery({
  queryKey: transactionKeys.list({ size: 8 }),
  queryFn: ({ signal }) => listTransactions({ size: 8 }, signal),
})

const { data: arAging } = useQuery({
  queryKey: agingKeys.ar(),
  queryFn: ({ signal }) => listArAging(signal),
})

const { data: apAging } = useQuery({
  queryKey: agingKeys.ap(),
  queryFn: ({ signal }) => listApAging(signal),
})

const BUCKET_LABELS: Record<AgingBucket, string> = {
  CURRENT: 'Current',
  DAYS_1_30: '1-30 days',
  DAYS_31_60: '31-60 days',
  DAYS_61_90: '61-90 days',
  DAYS_OVER_90: '90+ days',
}
const BUCKET_ORDER: AgingBucket[] = ['CURRENT', 'DAYS_1_30', 'DAYS_31_60', 'DAYS_61_90', 'DAYS_OVER_90']

function bucketCounts(rows: { bucket: AgingBucket }[] | undefined) {
  const counts = new Map<AgingBucket, number>()
  for (const bucket of BUCKET_ORDER) counts.set(bucket, 0)
  for (const row of rows ?? []) counts.set(row.bucket, (counts.get(row.bucket) ?? 0) + 1)
  return BUCKET_ORDER.map((bucket) => ({
    bucket,
    label: BUCKET_LABELS[bucket],
    count: counts.get(bucket) ?? 0,
  }))
}
</script>

<template>
  <AppShell>
    <template #title>Dashboard</template>
    <template #sub>Ledger overview · live</template>
    <template #actions>
      <template v-if="auth.isAdmin">
        <RouterLink class="btn" to="/transactions/new">Post journal entry</RouterLink>
        <RouterLink class="btn" to="/periods">Periods</RouterLink>
        <RouterLink class="btn" to="/bank-accounts">Bank accounts</RouterLink>
      </template>
      <RouterLink class="btn btn-primary" to="/accounts">View accounts</RouterLink>
    </template>

    <p v-if="isPending">Loading…</p>
    <p v-else-if="error" class="field-error">Unable to load dashboard data.</p>
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
          <div class="label">Open receivables</div>
          <div class="value mono">{{ formatMoney(summary.openArTotal) }}</div>
          <div class="delta" :class="{ 'field-error': summary.overdueArTotal > 0 }">
            {{ formatMoney(summary.overdueArTotal) }} overdue
          </div>
        </div>
        <div class="stat-card">
          <div class="label">Open payables</div>
          <div class="value mono">{{ formatMoney(summary.openApTotal) }}</div>
          <div class="delta" :class="{ 'field-error': summary.overdueApTotal > 0 }">
            {{ formatMoney(summary.overdueApTotal) }} overdue
          </div>
        </div>
      </div>

      <div class="aging-row">
        <div class="tablecard">
          <div class="sectionhead">
            <h2>Receivables by age</h2>
            <RouterLink to="/invoices" class="mono" style="font-size: 12px">Invoices →</RouterLink>
          </div>
          <table>
            <tr>
              <th>Bucket</th>
              <th style="text-align: right">Open invoices</th>
            </tr>
            <tr v-for="row in bucketCounts(arAging)" :key="row.bucket">
              <td>{{ row.label }}</td>
              <td class="mono" style="text-align: right">{{ row.count }}</td>
            </tr>
          </table>
        </div>
        <div class="tablecard">
          <div class="sectionhead">
            <h2>Payables by age</h2>
            <RouterLink to="/bills" class="mono" style="font-size: 12px">Bills →</RouterLink>
          </div>
          <table>
            <tr>
              <th>Bucket</th>
              <th style="text-align: right">Open bills</th>
            </tr>
            <tr v-for="row in bucketCounts(apAging)" :key="row.bucket">
              <td>{{ row.label }}</td>
              <td class="mono" style="text-align: right">{{ row.count }}</td>
            </tr>
          </table>
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
          <tr v-for="txn in transactionsPage?.content ?? []" :key="txn.id">
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
        <div v-if="(transactionsPage?.content ?? []).length === 0" class="empty">No transactions yet.</div>
      </div>
    </template>
  </AppShell>
</template>

<style scoped>
.aging-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}
</style>
