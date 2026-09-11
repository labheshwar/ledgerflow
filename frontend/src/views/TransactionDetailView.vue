<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import { apiFetch } from '../lib/api'
import { directionPillClass, formatDateTime, formatMoney } from '../lib/format'
import type { TransactionDetail } from '../lib/types'

const route = useRoute()
const transactionId = computed(() => route.params.id as string)

const transaction = ref<TransactionDetail | null>(null)
const loading = ref(true)
const errorText = ref('')

onMounted(async () => {
  try {
    transaction.value = await apiFetch<TransactionDetail>(`/transactions/${transactionId.value}`)
  } catch {
    errorText.value = 'Unable to load this transaction.'
  } finally {
    loading.value = false
  }
})

const totalDebit = computed(
  () => transaction.value?.entries.filter((e) => e.direction === 'DEBIT').reduce((s, e) => s + e.amount, 0) ?? 0,
)
const totalCredit = computed(
  () => transaction.value?.entries.filter((e) => e.direction === 'CREDIT').reduce((s, e) => s + e.amount, 0) ?? 0,
)
</script>

<template>
  <AppShell>
    <template #title>
      <template v-if="transaction">
        TXN-{{ transaction.id }} <span class="pill pill-green">{{ transaction.status }}</span>
      </template>
      <template v-else>Transaction</template>
    </template>
    <template #sub><RouterLink to="/transactions">← Back to transactions</RouterLink></template>

    <p v-if="loading">Loading…</p>
    <p v-else-if="errorText" class="field-error">{{ errorText }}</p>
    <template v-else-if="transaction">
      <div class="layout">
        <div class="card">
          <h2>Ledger slip</h2>
          <div class="slip">
            <div class="slip-head">
              <div class="ref mono">TXN-{{ transaction.id }}</div>
              <div class="when">{{ formatDateTime(transaction.createdAt) }}</div>
            </div>
            <div class="row-head">
              <div>Account</div>
              <div>Direction</div>
              <div style="text-align: right">Amount</div>
            </div>
            <div v-for="e in transaction.entries" :key="e.accountId + e.direction" class="entry-row">
              <div>{{ e.accountName }}</div>
              <div><span :class="directionPillClass(e.direction)">{{ e.direction }}</span></div>
              <div class="amt">{{ formatMoney(e.amount) }}</div>
            </div>
            <div class="slip-total">
              <span>Total debits / credits</span>
              <span class="v">{{ formatMoney(totalDebit) }} / {{ formatMoney(totalCredit) }}</span>
            </div>
          </div>
        </div>

        <div class="card">
          <h2>Details</h2>
          <div class="meta-row"><span class="k">Reference</span><span class="v">TXN-{{ transaction.id }}</span></div>
          <div class="meta-row"><span class="k">Idempotency key</span><span class="v">{{ transaction.idempotencyKey }}</span></div>
          <div class="meta-row"><span class="k">Description</span><span>{{ transaction.description || '—' }}</span></div>
          <div class="meta-row"><span class="k">Posted at</span><span class="v">{{ formatDateTime(transaction.createdAt) }}</span></div>
        </div>
      </div>
    </template>
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
.slip {
  border: 1px dashed var(--line);
  border-radius: 6px;
  padding: 18px;
}
.slip-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--line);
}
.slip-head .ref {
  font-size: 16px;
  font-weight: 600;
}
.slip-head .when {
  font-size: 11.5px;
  color: var(--ink-faint);
}
.row-head {
  display: grid;
  grid-template-columns: 1.6fr 108px 140px;
  gap: 10px;
  padding: 0 2px 8px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--ink-soft);
}
.entry-row {
  display: grid;
  grid-template-columns: 1.6fr 108px 140px;
  gap: 10px;
  align-items: center;
  padding: 8px 2px;
  border-bottom: 1px solid var(--line-soft);
}
.entry-row:last-of-type {
  border-bottom: none;
}
.amt {
  font-family: 'IBM Plex Mono', monospace;
  text-align: right;
}
.slip-total {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
  font-size: 12.5px;
  color: var(--ink-soft);
}
.slip-total .v {
  font-family: 'IBM Plex Mono', monospace;
  color: var(--ink);
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
  font-family: 'IBM Plex Mono', monospace;
  text-align: right;
}
</style>
