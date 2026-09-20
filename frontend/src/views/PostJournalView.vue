<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import { getAccountTree } from '../lib/api/accounts'
import { getTransaction, postTransaction } from '../lib/api/transactions'
import { formatDate, formatMoney } from '../lib/format'
import { ApiError } from '../lib/http'
import { computeJournalBalance, isAmountValid } from '../lib/journal-balance'
import type { Account, AccountNode, EntryDirection, TransactionDetail } from '../lib/types'

interface DraftEntry {
  id: number
  accountId: number | null
  direction: EntryDirection
  amount: string
}

function genIdempotencyKey(): string {
  const chars = 'abcdef0123456789'
  let s = 'idem_'
  for (let i = 0; i < 16; i++) s += chars[Math.floor(Math.random() * chars.length)]
  return s
}

function flattenPostable(nodes: AccountNode[]): Account[] {
  return nodes.flatMap((node) => [
    ...(node.account.postable && !node.account.archived ? [node.account] : []),
    ...flattenPostable(node.children),
  ])
}

const accounts = ref<Account[]>([])
const accountsLoaded = ref(false)

let nextId = 3
const entries = reactive<DraftEntry[]>([
  { id: 1, accountId: null, direction: 'DEBIT', amount: '' },
  { id: 2, accountId: null, direction: 'CREDIT', amount: '' },
])
const description = ref('')
const txnDate = ref(new Date().toISOString().slice(0, 10))
const idempotencyKey = ref(genIdempotencyKey())

const touchedAmounts = ref(false)
const touchedIdemp = ref(false)
const submitAttempted = ref(false)
const submitting = ref(false)
const errorText = ref('')

const posted = ref(false)
const lastPosted = ref<TransactionDetail | null>(null)

onMounted(async () => {
  try {
    // Headings and archived accounts are excluded here rather than merely
    // rejected on submit -- an account that cannot receive an entry should
    // not be offered as though it could.
    accounts.value = flattenPostable(await getAccountTree(false))
    if (accounts.value.length > 0) {
      entries[0].accountId = accounts.value[0].id
      entries[1].accountId = accounts.value[1]?.id ?? accounts.value[0].id
    }
  } finally {
    accountsLoaded.value = true
  }
})

function regenerateKey() {
  idempotencyKey.value = genIdempotencyKey()
  touchedIdemp.value = false
}

function addEntry() {
  entries.push({ id: nextId++, accountId: accounts.value[0]?.id ?? null, direction: 'DEBIT', amount: '' })
}
function removeEntry(id: number) {
  if (entries.length <= 2) return
  const idx = entries.findIndex((e) => e.id === id)
  if (idx !== -1) entries.splice(idx, 1)
}

const balance = computed(() => computeJournalBalance(entries))

const showAmountErrors = computed(() => touchedAmounts.value || submitAttempted.value)
const allAmountsValid = computed(() => entries.every((e) => isAmountValid(e.amount)))
const showIdempError = computed(
  () => (touchedIdemp.value || submitAttempted.value) && idempotencyKey.value.trim().length === 0,
)
const idempValid = computed(() => idempotencyKey.value.trim().length > 0)
const allAccountsSelected = computed(() => entries.every((e) => e.accountId != null))

const canSubmit = computed(
  () => balance.value.balanced && allAmountsValid.value && idempValid.value && allAccountsSelected.value,
)

const statusText = computed(() => {
  if (showAmountErrors.value && !allAmountsValid.value) return 'Fix the highlighted amounts before posting'
  if (showIdempError.value) return 'Idempotency key is required'
  if (balance.value.balanced) return 'Balanced — ready to post'
  if (balance.value.totalDebit === 0 && balance.value.totalCredit === 0) return 'Enter amounts to begin'
  return 'Unbalanced — debits must equal credits'
})

async function submit() {
  submitAttempted.value = true
  touchedAmounts.value = true
  touchedIdemp.value = true
  if (!canSubmit.value || submitting.value) return

  submitting.value = true
  errorText.value = ''
  try {
    const created = await postTransaction({
      idempotencyKey: idempotencyKey.value,
      description: description.value.trim() || null,
      txnDate: txnDate.value || null,
      entries: entries.map((e) => ({
        accountId: e.accountId,
        entryType: e.direction,
        amount: parseFloat(e.amount),
      })),
    })
    lastPosted.value = await getTransaction(created.id)
    posted.value = true
  } catch (e) {
    // A closed period surfaces here with the backend's own PERIOD_CLOSED
    // message, in the same errorText slot as any other posting failure --
    // there is nothing period-specific for this form to say that the
    // server has not already said better.
    errorText.value = e instanceof ApiError ? e.message : 'Unable to post this journal entry.'
  } finally {
    submitting.value = false
  }
}

function startNew() {
  posted.value = false
  lastPosted.value = null
  description.value = ''
  txnDate.value = new Date().toISOString().slice(0, 10)
  idempotencyKey.value = genIdempotencyKey()
  touchedAmounts.value = false
  touchedIdemp.value = false
  submitAttempted.value = false
  errorText.value = ''
  entries.splice(0, entries.length)
  entries.push(
    { id: nextId++, accountId: accounts.value[0]?.id ?? null, direction: 'DEBIT', amount: '' },
    {
      id: nextId++,
      accountId: accounts.value[1]?.id ?? accounts.value[0]?.id ?? null,
      direction: 'CREDIT',
      amount: '',
    },
  )
}
</script>

<template>
  <AppShell>
    <template #title>Post journal entry</template>
    <template #sub>Every entry must balance to zero before it can be submitted</template>

    <p v-if="!accountsLoaded">Loading…</p>
    <template v-else-if="!posted">
      <div class="layout">
        <div class="card">
          <h2>Entries</h2>
          <div class="fieldrow">
            <div class="field">
              <label>Date</label>
              <input v-model="txnDate" type="date" class="input" />
            </div>
            <div class="field" style="flex: 1">
              <label>Description (optional)</label>
              <input v-model="description" class="input" placeholder="What is this entry for?" />
            </div>
          </div>

          <div class="row-head">
            <div>Account</div>
            <div>Direction</div>
            <div style="text-align: right">Amount</div>
            <div></div>
          </div>
          <div v-for="row in entries" :key="row.id" class="entry-row">
            <select v-model.number="row.accountId" class="input">
              <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.code }} · {{ a.name }}</option>
            </select>
            <div class="dirseg">
              <button
                :class="{ on: row.direction === 'DEBIT' }"
                class="debit"
                type="button"
                @click="row.direction = 'DEBIT'"
              >
                DR
              </button>
              <button
                :class="{ on: row.direction === 'CREDIT' }"
                class="credit"
                type="button"
                @click="row.direction = 'CREDIT'"
              >
                CR
              </button>
            </div>
            <input
              v-model="row.amount"
              class="input amt"
              :class="{ error: showAmountErrors && !isAmountValid(row.amount) }"
              placeholder="0.00"
              @input="touchedAmounts = true"
            />
            <button class="rm" type="button" :disabled="entries.length <= 2" @click="removeEntry(row.id)">
              ×
            </button>
          </div>
          <div v-if="showAmountErrors && !allAmountsValid" class="row-error">
            Every entry needs an amount greater than zero.
          </div>

          <button class="addrow" type="button" @click="addEntry">+ Add entry</button>

          <div class="idemp">
            <label>Idempotency key</label>
            <div class="idemp-row">
              <input
                v-model="idempotencyKey"
                class="input mono"
                :class="{ error: showIdempError }"
                @input="touchedIdemp = true"
              />
              <button class="btn" type="button" @click="regenerateKey">Regenerate</button>
            </div>
            <div v-if="showIdempError" class="row-error">Idempotency key is required.</div>
          </div>
        </div>

        <div class="card balancecard">
          <h2>Balance check</h2>
          <div class="num-row">
            <span>Total debits</span><span class="v">{{ formatMoney(balance.totalDebit) }}</span>
          </div>
          <div class="num-row">
            <span>Total credits</span><span class="v">{{ formatMoney(balance.totalCredit) }}</span>
          </div>
          <hr />
          <div class="num-row">
            <span>Difference</span><span class="v">{{ formatMoney(Math.abs(balance.difference)) }}</span>
          </div>
          <div :class="['status', canSubmit ? 'ok' : 'bad']">{{ statusText }}</div>
          <div v-if="errorText" class="field-error" style="margin-top: 10px">{{ errorText }}</div>
          <button class="submit" type="button" :disabled="!canSubmit || submitting" @click="submit">
            {{ submitting ? 'Posting…' : 'Post journal entry' }}
          </button>
        </div>
      </div>
    </template>

    <template v-else-if="lastPosted">
      <div class="layout">
        <div class="card success">
          <div class="tick">
            <svg viewBox="0 0 20 20"><path d="M4 10.5l4 4 8-9"></path></svg>
          </div>
          <h2>Journal entry posted</h2>
          <div style="font-size: 12.5px; color: var(--ink-soft); margin-bottom: 14px">
            Written atomically · audit log updated
          </div>
          <div class="receipt-row">
            <span>Reference</span><span class="mono">TXN-{{ lastPosted.id }}</span>
          </div>
          <div class="receipt-row">
            <span>Date</span><span class="mono">{{ formatDate(lastPosted.txnDate) }}</span>
          </div>
          <div class="receipt-row">
            <span>Idempotency key</span><span class="mono">{{ lastPosted.idempotencyKey }}</span>
          </div>
          <div class="receipt-row">
            <span>Status</span><span class="pill pill-green">{{ lastPosted.status }}</span>
          </div>
          <div v-for="e in lastPosted.entries" :key="e.accountId + e.direction" class="receipt-row">
            <span
              >{{ e.accountName }}
              <span :class="e.direction === 'DEBIT' ? 'pill pill-red' : 'pill pill-green'">{{
                e.direction
              }}</span></span
            >
            <span class="mono">{{ formatMoney(e.amount, e.currency) }}</span>
          </div>
          <RouterLink
            class="newbtn"
            :to="`/transactions/${lastPosted.id}`"
            style="display: block; text-align: center; text-decoration: none"
          >
            View transaction
          </RouterLink>
          <button class="newbtn" type="button" @click="startNew">Post another entry</button>
        </div>
        <div class="card">
          <h2>What just happened</h2>
          <div style="font-size: 12.5px; color: var(--ink-soft); line-height: 1.6">
            The idempotency key was checked first — no existing transaction matched it — then the entries were
            validated (debits = credits) and confirmed against the accounting period covering the date above,
            and written inside one database transaction.
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
.fieldrow {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.fieldrow .field:first-child {
  flex: 0 0 160px;
}
.row-head {
  display: grid;
  grid-template-columns: 1.6fr 108px 140px 30px;
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
  grid-template-columns: 1.6fr 108px 140px 30px;
  gap: 10px;
  align-items: center;
  padding: 7px 0;
}
.amt {
  font-family: 'IBM Plex Mono', monospace;
  text-align: right;
}
.dirseg {
  display: flex;
  border: 1px solid var(--line);
  border-radius: 5px;
  overflow: hidden;
}
.dirseg button {
  flex: 1;
  padding: 6px 0;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10.5px;
  font-weight: 500;
  background: var(--paper);
  color: var(--ink-soft);
  border: none;
}
.dirseg button.debit.on {
  background: var(--red-soft);
  color: var(--red);
}
.dirseg button.credit.on {
  background: var(--green-soft);
  color: var(--green);
}
.rm {
  width: 24px;
  height: 24px;
  border-radius: 5px;
  border: 1px solid var(--line);
  background: var(--paper);
  color: var(--ink-faint);
  font-size: 13px;
  line-height: 1;
}
.rm:hover {
  border-color: var(--red-line);
  color: var(--red);
}
.rm:disabled {
  opacity: 0.4;
  cursor: default;
}
.addrow {
  margin-top: 8px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11.5px;
  padding: 7px 12px;
  border-radius: 5px;
  border: 1px dashed var(--line);
  background: transparent;
  color: var(--ink-soft);
}
.addrow:hover {
  border-color: var(--green-line);
  color: var(--green);
}
.row-error {
  color: var(--red);
  font-size: 10.5px;
  margin-top: 3px;
}
.idemp {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}
.idemp label {
  display: block;
  font-size: 11px;
  color: var(--ink-soft);
  margin-bottom: 6px;
  font-weight: 500;
}
.idemp-row {
  display: flex;
  gap: 8px;
}
.idemp-row input {
  flex: 1;
}
.balancecard .num-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
}
.balancecard .num-row .v {
  font-family: 'IBM Plex Mono', monospace;
}
.balancecard hr {
  border: none;
  border-top: 1px solid var(--line);
  margin: 8px 0;
}
.status {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 5px;
  text-align: center;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11.5px;
  font-weight: 500;
}
.status.ok {
  background: var(--green-soft);
  color: var(--green);
}
.status.bad {
  background: var(--red-soft);
  color: var(--red);
}
.submit {
  width: 100%;
  margin-top: 14px;
  padding: 11px 0;
  border-radius: 5px;
  border: 1px solid var(--green);
  background: var(--green);
  color: var(--paper);
  font-size: 13px;
  font-weight: 500;
}
.submit:disabled {
  opacity: 0.38;
  cursor: default;
}
.success .tick {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--green-soft);
  color: var(--green);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}
.success .tick svg {
  width: 18px;
  height: 18px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.receipt-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid var(--line-soft);
  font-size: 12.5px;
}
.receipt-row:last-of-type {
  border-bottom: none;
}
.newbtn {
  width: 100%;
  margin-top: 12px;
  padding: 10px 0;
  border-radius: 5px;
  border: 1px solid var(--line);
  background: var(--raised);
  color: var(--ink);
  font-size: 13px;
  font-weight: 500;
}
</style>
