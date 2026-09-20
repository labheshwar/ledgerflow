<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import { getTransaction, reverseTransaction } from '../lib/api/transactions'
import { directionPillClass, formatDate, formatDateTime, formatMoney } from '../lib/format'
import { ApiError } from '../lib/http'
import type { TransactionDetail } from '../lib/types'
import { useAuthStore } from '../stores/auth'
import { useToastStore } from '../stores/toast'

const route = useRoute()
const auth = useAuthStore()
const toasts = useToastStore()
const transactionId = computed(() => route.params.id as string)

const transaction = ref<TransactionDetail | null>(null)
const loading = ref(true)
const errorText = ref('')

async function load() {
  try {
    transaction.value = await getTransaction(transactionId.value)
  } catch {
    errorText.value = 'Unable to load this transaction.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const totalDebit = computed(
  () =>
    transaction.value?.entries.filter((e) => e.direction === 'DEBIT').reduce((s, e) => s + e.amount, 0) ?? 0,
)
const totalCredit = computed(
  () =>
    transaction.value?.entries.filter((e) => e.direction === 'CREDIT').reduce((s, e) => s + e.amount, 0) ?? 0,
)

// --- reversal ---
const reversing = ref(false)
const reason = ref('')
const reverseError = ref('')
const submittingReverse = ref(false)

async function submitReversal() {
  if (!transaction.value) return
  submittingReverse.value = true
  reverseError.value = ''
  try {
    const created = await reverseTransaction(transaction.value.id, { reason: reason.value.trim() || null })
    reversing.value = false
    reason.value = ''
    toasts.success(`Reversal posted as TXN-${created.id}`)
    await load()
  } catch (e) {
    // A closed period on the reversal's own date surfaces here with the
    // backend's own message -- there is nothing this form needs to add.
    reverseError.value = e instanceof ApiError ? e.message : 'Unable to reverse this transaction.'
  } finally {
    submittingReverse.value = false
  }
}
</script>

<template>
  <AppShell>
    <template #title>
      <template v-if="transaction">
        TXN-{{ transaction.id }} <span class="pill pill-green">{{ transaction.status }}</span>
        <span v-if="transaction.reversalOfTransactionId" class="pill pill-neutral">reversal</span>
        <span v-if="transaction.reversedByTransactionId" class="pill pill-amber">reversed</span>
      </template>
      <template v-else>Transaction</template>
    </template>
    <template #sub><RouterLink to="/transactions">← Back to transactions</RouterLink></template>
    <template #actions>
      <button
        v-if="auth.isAdmin && transaction && !transaction.reversedByTransactionId && !reversing"
        type="button"
        class="btn"
        @click="reversing = true"
      >
        Reverse
      </button>
    </template>

    <p v-if="loading">Loading…</p>
    <p v-else-if="errorText" class="field-error">{{ errorText }}</p>
    <template v-else-if="transaction">
      <div v-if="reversing" class="reverse-card">
        <div class="field" style="margin-bottom: 10px">
          <label>Reason (optional)</label>
          <input v-model="reason" class="input" placeholder="Why is this being reversed?" />
        </div>
        <div style="display: flex; gap: 8px">
          <button class="btn btn-primary" type="button" :disabled="submittingReverse" @click="submitReversal">
            {{ submittingReverse ? 'Posting…' : 'Post the mirror entry, dated today' }}
          </button>
          <button class="btn" type="button" @click="reversing = false">Cancel</button>
        </div>
        <div v-if="reverseError" class="field-error" style="margin-top: 10px">{{ reverseError }}</div>
      </div>

      <div
        v-if="transaction.reversalOfTransactionId || transaction.reversedByTransactionId"
        class="link-note"
      >
        <template v-if="transaction.reversalOfTransactionId">
          This is the reversal of
          <RouterLink :to="`/transactions/${transaction.reversalOfTransactionId}`"
            >TXN-{{ transaction.reversalOfTransactionId }}</RouterLink
          >.
        </template>
        <template v-if="transaction.reversedByTransactionId">
          Reversed by
          <RouterLink :to="`/transactions/${transaction.reversedByTransactionId}`"
            >TXN-{{ transaction.reversedByTransactionId }}</RouterLink
          >.
        </template>
      </div>

      <div class="layout">
        <div class="card">
          <h2>Ledger slip</h2>
          <div class="slip">
            <div class="slip-head">
              <div class="ref mono">TXN-{{ transaction.id }}</div>
              <div class="when">{{ formatDate(transaction.txnDate) }}</div>
            </div>
            <div class="row-head">
              <div>Account</div>
              <div>Direction</div>
              <div style="text-align: right">Amount</div>
            </div>
            <div v-for="e in transaction.entries" :key="e.accountId + e.direction" class="entry-row">
              <div>{{ e.accountName }}</div>
              <div>
                <span :class="directionPillClass(e.direction)">{{ e.direction }}</span>
              </div>
              <div class="amt">{{ formatMoney(e.amount, e.currency) }}</div>
            </div>
            <div class="slip-total">
              <span>Total debits / credits</span>
              <span class="v">{{ formatMoney(totalDebit) }} / {{ formatMoney(totalCredit) }}</span>
            </div>
          </div>
        </div>

        <div class="card">
          <h2>Details</h2>
          <div class="meta-row">
            <span class="k">Reference</span><span class="v">TXN-{{ transaction.id }}</span>
          </div>
          <div class="meta-row">
            <span class="k">Idempotency key</span><span class="v">{{ transaction.idempotencyKey }}</span>
          </div>
          <div class="meta-row">
            <span class="k">Description</span><span>{{ transaction.description || '—' }}</span>
          </div>
          <div class="meta-row">
            <span class="k">Date</span><span class="v">{{ formatDate(transaction.txnDate) }}</span>
            <span class="k">Entered</span><span class="v">{{ formatDateTime(transaction.createdAt) }}</span>
          </div>
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
.reverse-card {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 16px 20px;
  margin-bottom: 16px;
}
.link-note {
  font-size: 12.5px;
  color: var(--ink-soft);
  margin-bottom: 16px;
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
