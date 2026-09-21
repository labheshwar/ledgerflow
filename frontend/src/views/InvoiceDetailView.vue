<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import InvoiceForm from '@/components/invoices/InvoiceForm.vue'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import { listContacts } from '@/lib/api/contacts'
import {
  deleteInvoice,
  getInvoice,
  invoiceKeys,
  sendInvoice,
  updateInvoice,
  voidInvoice,
  type InvoiceRequestBody,
} from '@/lib/api/invoices'
import { listItems } from '@/lib/api/items'
import { listTaxRates } from '@/lib/api/taxRates'
import { formatDate, formatMoney } from '@/lib/format'
import { confirmDialog } from '@/lib/dialogs'
import { ApiError } from '@/lib/http'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()
const invoiceId = computed(() => route.params.id as string)

const {
  data: invoice,
  isPending,
  error,
} = useQuery({
  queryKey: computed(() => invoiceKeys.detail(invoiceId.value)),
  queryFn: ({ signal }) => getInvoice(invoiceId.value, signal),
})

const { data: contacts } = useQuery({
  queryKey: ['contacts', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listContacts({ includeArchived: false, size: 200 }, signal),
})
const { data: items } = useQuery({
  queryKey: ['items', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listItems({ includeArchived: false, size: 200 }, signal),
})
const { data: taxRates } = useQuery({
  queryKey: ['tax-rates', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listTaxRates({ includeArchived: false, size: 200 }, signal),
})

function invalidate() {
  return queryClient.invalidateQueries({ queryKey: invoiceKeys.all })
}

const saveError = ref('')
const saveMutation = useMutation({
  mutationFn: (body: InvoiceRequestBody) => updateInvoice(invoiceId.value, body),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Draft saved')
  },
  onError: (err) => {
    saveError.value = err instanceof ApiError ? err.message : 'Unable to save this invoice.'
  },
})

const sendMutation = useMutation({
  mutationFn: () => sendInvoice(invoiceId.value),
  onSuccess: async (sent) => {
    await invalidate()
    toasts.success(`Sent as ${sent.invoiceNumber}`)
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Unable to send this invoice.'),
})

const deleteMutation = useMutation({
  mutationFn: () => deleteInvoice(invoiceId.value),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Draft deleted')
    router.back()
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Unable to delete this invoice.'),
})

function onDelete() {
  if (!confirmDialog('Delete this draft? This cannot be undone.')) return
  deleteMutation.mutate()
}

// --- void ---
const voiding = ref(false)
const voidReason = ref('')
const voidError = ref('')
const voidMutation = useMutation({
  mutationFn: () => voidInvoice(invoiceId.value, voidReason.value.trim() || null),
  onSuccess: async () => {
    await invalidate()
    voiding.value = false
    voidReason.value = ''
    toasts.success('Invoice voided')
  },
  onError: (err) => {
    voidError.value = err instanceof ApiError ? err.message : 'Unable to void this invoice.'
  },
})

function statusPillClass(): string {
  if (!invoice.value) return 'pill pill-neutral'
  if (invoice.value.status === 'VOID') return 'pill pill-neutral'
  if (invoice.value.overdue) return 'pill pill-red'
  if (invoice.value.status === 'DRAFT') return 'pill pill-amber'
  return 'pill pill-green'
}
function statusLabel(): string {
  if (!invoice.value) return ''
  if (invoice.value.status === 'SENT' && invoice.value.overdue) return 'OVERDUE'
  return invoice.value.status
}
</script>

<template>
  <AppShell>
    <template #title>
      <template v-if="invoice">
        {{ invoice.invoiceNumber ?? 'Draft invoice' }}
        <span :class="statusPillClass()">{{ statusLabel() }}</span>
      </template>
      <template v-else>Invoice</template>
    </template>
    <template #sub><RouterLink to="/invoices">← Back to invoices</RouterLink></template>
    <template #actions>
      <template v-if="auth.isAdmin && invoice">
        <button v-if="invoice.status === 'DRAFT'" type="button" class="btn" @click="onDelete">Delete</button>
        <button
          v-if="invoice.status === 'DRAFT'"
          type="button"
          class="btn btn-primary"
          :disabled="sendMutation.isPending.value"
          @click="sendMutation.mutate()"
        >
          {{ sendMutation.isPending.value ? 'Sending…' : 'Send' }}
        </button>
        <button
          v-if="invoice.status === 'SENT' && !voiding"
          type="button"
          class="btn"
          @click="voiding = true"
        >
          Void
        </button>
      </template>
    </template>

    <PageState :loading="isPending" :error="error" error-text="Unable to load this invoice.">
      <template v-if="invoice">
        <div v-if="voiding" class="void-card">
          <div class="field" style="margin-bottom: 10px">
            <label>Reason (optional)</label>
            <input v-model="voidReason" class="input" placeholder="Why is this being voided?" />
          </div>
          <div style="display: flex; gap: 8px">
            <button
              class="btn btn-primary"
              type="button"
              :disabled="voidMutation.isPending.value"
              @click="voidMutation.mutate()"
            >
              {{ voidMutation.isPending.value ? 'Voiding…' : 'Reverse the posting and void this invoice' }}
            </button>
            <button class="btn" type="button" @click="voiding = false">Cancel</button>
          </div>
          <div v-if="voidError" class="field-error" style="margin-top: 10px">{{ voidError }}</div>
        </div>

        <InvoiceForm
          v-if="invoice.status === 'DRAFT'"
          :key="invoice.id"
          :initial="invoice"
          :contacts="contacts?.content ?? []"
          :items="items?.content ?? []"
          :tax-rates="taxRates?.content ?? []"
          :submitting="saveMutation.isPending.value"
          :error-text="saveError"
          @submit="(body) => saveMutation.mutate(body)"
        />

        <div v-else class="layout">
          <div class="card">
            <h2>Lines</h2>
            <div class="row-head">
              <div>Description</div>
              <div style="text-align: right">Qty</div>
              <div style="text-align: right">Unit price</div>
              <div style="text-align: right">Tax</div>
              <div style="text-align: right">Line total</div>
            </div>
            <div v-for="line in invoice.lines" :key="line.id" class="entry-row">
              <div>{{ line.description }}</div>
              <div class="amt">{{ line.quantity }}</div>
              <div class="amt">{{ formatMoney(line.unitPrice, invoice.currency) }}</div>
              <div class="amt">{{ formatMoney(line.taxAmount, invoice.currency) }}</div>
              <div class="amt">{{ formatMoney(line.lineTotal, invoice.currency) }}</div>
            </div>
            <div class="totalsblock">
              <div class="num-row">
                <span>Subtotal</span
                ><span class="v">{{ formatMoney(invoice.subtotal, invoice.currency) }}</span>
              </div>
              <div class="num-row">
                <span>Tax</span><span class="v">{{ formatMoney(invoice.taxTotal, invoice.currency) }}</span>
              </div>
              <div class="num-row grand">
                <span>Total</span
                ><span class="v">{{ formatMoney(invoice.grandTotal, invoice.currency) }}</span>
              </div>
            </div>
            <p v-if="invoice.notes" class="notes">{{ invoice.notes }}</p>
          </div>

          <div class="card">
            <h2>Details</h2>
            <div class="meta-row">
              <span class="k">Customer</span><span class="v">{{ invoice.contactName }}</span>
            </div>
            <div class="meta-row">
              <span class="k">Issued</span><span class="v">{{ formatDate(invoice.issueDate) }}</span>
            </div>
            <div class="meta-row">
              <span class="k">Due</span><span class="v">{{ formatDate(invoice.dueDate) }}</span>
            </div>
            <div v-if="invoice.postedTransactionId" class="meta-row">
              <span class="k">Journal</span>
              <RouterLink class="v" :to="`/transactions/${invoice.postedTransactionId}`">
                TXN-{{ invoice.postedTransactionId }}
              </RouterLink>
            </div>
            <div v-else-if="invoice.status === 'SENT'" class="meta-row">
              <span class="k">Journal</span><span class="v">posting…</span>
            </div>
          </div>
        </div>
      </template>
    </PageState>
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
.void-card {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 16px 20px;
  margin-bottom: 16px;
}
.row-head {
  display: grid;
  grid-template-columns: 1.6fr 70px 110px 100px 110px;
  gap: 10px;
  padding: 0 2px 8px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--ink-soft);
  border-bottom: 1px dashed var(--line);
}
.entry-row {
  display: grid;
  grid-template-columns: 1.6fr 70px 110px 100px 110px;
  gap: 10px;
  align-items: center;
  padding: 8px 2px;
  border-bottom: 1px solid var(--line-soft);
}
.amt {
  font-family: 'IBM Plex Mono', monospace;
  text-align: right;
}
.totalsblock {
  margin-top: 8px;
  padding-top: 8px;
}
.totalsblock .num-row {
  display: flex;
  justify-content: space-between;
  padding: 5px 2px;
  font-size: 13px;
}
.totalsblock .num-row .v {
  font-family: 'IBM Plex Mono', monospace;
}
.totalsblock .num-row.grand {
  font-weight: 600;
  font-size: 14px;
  border-top: 1px solid var(--line);
  margin-top: 4px;
  padding-top: 8px;
}
.notes {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
  font-size: 12.5px;
  color: var(--ink-soft);
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
