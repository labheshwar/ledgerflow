<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import { apiFetch } from '../lib/api'
import {
  accountTypePillClass,
  directionPillClass,
  formatDate,
  formatMoney,
  formatRelativeTime,
  signedAmount,
} from '../lib/format'
import type { Account, LedgerEntry } from '../lib/types'

const route = useRoute()
const accountId = computed(() => route.params.id as string)

const account = ref<Account | null>(null)
const entries = ref<LedgerEntry[]>([])
const loading = ref(true)
const errorText = ref('')
const page = ref(1)
const perPage = 8

onMounted(async () => {
  try {
    const [accountData, entriesData] = await Promise.all([
      apiFetch<Account>(`/accounts/${accountId.value}`),
      apiFetch<LedgerEntry[]>(`/accounts/${accountId.value}/entries`),
    ])
    account.value = accountData
    entries.value = entriesData
  } catch {
    errorText.value = 'Unable to load this account.'
  } finally {
    loading.value = false
  }
})

const pageCount = computed(() => Math.max(1, Math.ceil(entries.value.length / perPage)))
const pagedEntries = computed(() => entries.value.slice((page.value - 1) * perPage, page.value * perPage))
</script>

<template>
  <AppShell>
    <template #title>
      {{ account?.name ?? 'Account' }}
      <span v-if="account" :class="accountTypePillClass(account.type)">{{ account.type }}</span>
    </template>
    <template #sub><RouterLink to="/accounts">← Back to accounts</RouterLink></template>

    <p v-if="loading">Loading…</p>
    <p v-else-if="errorText" class="field-error">{{ errorText }}</p>
    <template v-else-if="account">
      <div class="summary">
        <div class="card">
          <div class="label">Current balance</div>
          <div class="balance mono">{{ formatMoney(account.balance) }}</div>
          <div class="hint">Cached in Redis · invalidated on next posting</div>
        </div>
        <div class="card">
          <div class="label">Account details</div>
          <div class="meta-grid">
            <div>
              <div class="k">Account ID</div>
              <div class="v">#{{ account.id }}</div>
            </div>
            <div>
              <div class="k">Currency</div>
              <div class="v">{{ account.currency }}</div>
            </div>
            <div>
              <div class="k">Type</div>
              <div class="v">{{ account.type }}</div>
            </div>
            <div>
              <div class="k">Opened</div>
              <div class="v">{{ formatDate(account.createdAt) }}</div>
            </div>
          </div>
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
            <th>Time</th>
          </tr>
          <tr v-for="e in pagedEntries" :key="e.id">
            <td class="mono">
              <RouterLink :to="`/transactions/${e.transactionId}`">TXN-{{ e.transactionId }}</RouterLink>
            </td>
            <td>
              <span :class="directionPillClass(e.direction)">{{ e.direction }}</span>
            </td>
            <td class="num">{{ signedAmount(e.direction, e.amount) }}</td>
            <td class="num">{{ formatMoney(e.runningBalance) }}</td>
            <td class="mono" style="color: var(--ink-soft)">{{ formatRelativeTime(e.createdAt) }}</td>
          </tr>
        </table>
        <div v-if="entries.length === 0" class="empty">No ledger entries yet.</div>
        <div v-else class="pager">
          <div class="info">Page {{ page }} of {{ pageCount }} · {{ entries.length }} entries</div>
          <div class="btns">
            <button class="pgbtn" :disabled="page <= 1" @click="page--">← Previous</button>
            <button class="pgbtn" :disabled="page >= pageCount" @click="page++">Next →</button>
          </div>
        </div>
      </div>
    </template>
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
</style>
