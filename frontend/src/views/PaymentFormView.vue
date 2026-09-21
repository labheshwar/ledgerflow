<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import { listContacts } from '@/lib/api/contacts'
import {
  createPayment,
  listOpenDocuments,
  paymentKeys,
  type PaymentAllocationRequestBody,
  type PaymentRequestBody,
} from '@/lib/api/payments'
import { formatDate, formatMoney } from '@/lib/format'
import { ApiError } from '@/lib/http'
import type { OpenDocument, PaymentDirection } from '@/lib/types'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const { data: contacts } = useQuery({
  queryKey: ['contacts', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listContacts({ includeArchived: false, size: 200 }, signal),
})

const direction = ref<PaymentDirection>((route.query.direction as PaymentDirection) || 'RECEIVED')
const contactId = ref<number | null>(route.query.contactId ? Number(route.query.contactId) : null)
const paymentDate = ref(new Date().toISOString().slice(0, 10))
const amount = ref('')
const notes = ref('')

const { data: openDocuments } = useQuery({
  queryKey: computed(() => paymentKeys.openDocuments(contactId.value ?? 0, direction.value)),
  queryFn: ({ signal }) => listOpenDocuments(contactId.value as number, direction.value, signal),
  enabled: computed(() => contactId.value != null),
})

const allocationsByDocument = reactive<Record<string, string>>({})
function keyFor(doc: OpenDocument): string {
  return `${doc.documentType}:${doc.documentId}`
}
watch([contactId, direction], () => {
  for (const key of Object.keys(allocationsByDocument)) delete allocationsByDocument[key]
})

const allocatedTotal = computed(() =>
  Object.values(allocationsByDocument).reduce((sum, v) => sum + (parseFloat(v) || 0), 0),
)
const unallocated = computed(() => (parseFloat(amount.value) || 0) - allocatedTotal.value)

function documentLabel(doc: OpenDocument): string {
  return doc.number ?? `${doc.documentType === 'INVOICE' ? 'Draft invoice' : 'Draft bill'} #${doc.documentId}`
}

/**
 * The payment itself has no currency picker -- the server derives it from
 * whichever documents end up allocated against, and refuses to mix two.
 * This is only ever shown so the amount field reads as what it will
 * actually mean once at least one foreign-currency document is selected.
 */
const paymentCurrency = computed(() => {
  const allocated = (openDocuments.value ?? []).filter(
    (doc) => parseFloat(allocationsByDocument[keyFor(doc)] ?? '') > 0,
  )
  return allocated.find((doc) => doc.currency)?.currency ?? null
})

const submitAttempted = ref(false)
const contactError = computed(() =>
  submitAttempted.value && contactId.value == null ? 'A contact is required.' : '',
)
const amountError = computed(() => {
  if (!submitAttempted.value) return ''
  const value = parseFloat(amount.value)
  if (!(value > 0)) return 'The amount must be greater than zero.'
  return ''
})
const allocationError = computed(() => {
  if (!submitAttempted.value) return ''
  if (unallocated.value < -0.001) return 'Allocations cannot add up to more than the payment amount.'
  return ''
})
const isValid = computed(() => !contactError.value && !amountError.value && !allocationError.value)

const errorText = ref('')
const createMutation = useMutation({
  mutationFn: (body: PaymentRequestBody) => createPayment(body),
  onSuccess: async (payment) => {
    await queryClient.invalidateQueries({ queryKey: paymentKeys.all })
    await queryClient.invalidateQueries({ queryKey: ['invoices'] })
    await queryClient.invalidateQueries({ queryKey: ['bills'] })
    router.push(`/payments/${payment.id}`)
  },
  onError: (err) => {
    errorText.value = err instanceof ApiError ? err.message : 'Unable to record this payment.'
  },
})

function submit() {
  submitAttempted.value = true
  if (!isValid.value) return

  const allocations: PaymentAllocationRequestBody[] = (openDocuments.value ?? [])
    .map((doc) => ({ doc, value: parseFloat(allocationsByDocument[keyFor(doc)] ?? '') }))
    .filter(({ value }) => value > 0)
    .map(({ doc, value }) => ({ documentType: doc.documentType, documentId: doc.documentId, amount: value }))

  const body: PaymentRequestBody = {
    contactId: contactId.value as number,
    direction: direction.value,
    paymentDate: paymentDate.value,
    amount: parseFloat(amount.value),
    notes: notes.value.trim() || null,
    allocations,
  }
  createMutation.mutate(body)
}
</script>

<template>
  <AppShell>
    <template #title>New payment</template>
    <template #sub>Settles as many open invoices or bills as its amount allows</template>

    <div class="layout">
      <div class="card">
        <h2>Payment</h2>
        <div class="fieldrow">
          <div class="field" style="flex: 0 0 160px">
            <label>Direction</label>
            <select v-model="direction" class="input">
              <option value="RECEIVED">Received from customer</option>
              <option value="PAID">Paid to vendor</option>
            </select>
          </div>
          <div class="field" style="flex: 1">
            <label>{{ direction === 'RECEIVED' ? 'Customer' : 'Vendor' }}</label>
            <select v-model.number="contactId" class="input" :class="{ error: contactError }">
              <option :value="null">— Select a contact —</option>
              <option v-for="c in contacts?.content ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <div v-if="contactError" class="row-error">{{ contactError }}</div>
          </div>
        </div>
        <div class="fieldrow">
          <div class="field" style="flex: 0 0 150px">
            <label>Date</label>
            <input v-model="paymentDate" type="date" class="input" />
          </div>
          <div class="field" style="flex: 0 0 160px">
            <label>Amount{{ paymentCurrency ? ` (${paymentCurrency})` : '' }}</label>
            <input v-model="amount" class="input amt" :class="{ error: amountError }" placeholder="0.00" />
            <div v-if="amountError" class="row-error">{{ amountError }}</div>
          </div>
        </div>

        <template v-if="contactId != null">
          <h3>Allocate to open {{ direction === 'RECEIVED' ? 'invoices' : 'bills' }}</h3>
          <p v-if="openDocuments && openDocuments.length === 0" class="notes">
            Nothing open for this contact -- the whole amount will sit as a prepayment.
          </p>
          <div v-if="openDocuments && openDocuments.length > 0" class="row-head">
            <div>Document</div>
            <div style="text-align: right">Due</div>
            <div style="text-align: right">Balance</div>
            <div style="text-align: right">Allocate</div>
          </div>
          <div v-for="doc in openDocuments ?? []" :key="keyFor(doc)" class="entry-row">
            <div>{{ documentLabel(doc) }}</div>
            <div class="amt">{{ formatDate(doc.dueDate) }}</div>
            <div class="amt">{{ formatMoney(doc.balance, doc.currency) }}</div>
            <input v-model="allocationsByDocument[keyFor(doc)]" class="input amt" placeholder="0.00" />
          </div>
        </template>

        <div class="field" style="margin-top: 16px">
          <label>Notes (optional)</label>
          <textarea
            v-model="notes"
            class="input"
            rows="2"
            placeholder="Reference number, method, anything else"
          />
        </div>
      </div>

      <div class="card totalscard">
        <h2>Summary</h2>
        <div class="num-row">
          <span>Amount</span><span class="v">{{ (parseFloat(amount) || 0).toFixed(2) }}</span>
        </div>
        <div class="num-row">
          <span>Allocated</span><span class="v">{{ allocatedTotal.toFixed(2) }}</span>
        </div>
        <hr />
        <div class="num-row grand">
          <span>{{ unallocated < 0 ? 'Over-allocated' : 'Unallocated (prepayment)' }}</span>
          <span class="v">{{ unallocated.toFixed(2) }}</span>
        </div>
        <div v-if="allocationError" class="field-error" style="margin-top: 10px">{{ allocationError }}</div>
        <div v-if="errorText" class="field-error" style="margin-top: 10px">{{ errorText }}</div>
        <button class="submit" type="button" :disabled="createMutation.isPending.value" @click="submit">
          {{ createMutation.isPending.value ? 'Recording…' : 'Record payment' }}
        </button>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 1fr 280px;
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
.card h3 {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink-soft);
  margin: 18px 0 8px;
}
.fieldrow {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.row-head {
  display: grid;
  grid-template-columns: 1.6fr 100px 100px 120px;
  gap: 8px;
  padding: 8px 2px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--ink-soft);
  border-top: 1px solid var(--line);
}
.entry-row {
  display: grid;
  grid-template-columns: 1.6fr 100px 100px 120px;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid var(--line-soft);
}
.amt {
  font-family: 'IBM Plex Mono', monospace;
  text-align: right;
}
.notes {
  color: var(--ink-soft);
  font-size: 12.5px;
}
.row-error {
  color: var(--red);
  font-size: 10.5px;
  margin-top: 3px;
}
.totalscard .num-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
}
.totalscard .num-row .v {
  font-family: 'IBM Plex Mono', monospace;
}
.totalscard .num-row.grand {
  font-weight: 600;
  font-size: 14px;
}
.totalscard hr {
  border: none;
  border-top: 1px solid var(--line);
  margin: 8px 0;
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
  opacity: 0.6;
  cursor: default;
}
</style>
