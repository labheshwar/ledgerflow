<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BillForm from '@/components/bills/BillForm.vue'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import { getAccountTree } from '@/lib/api/accounts'
import {
  billKeys,
  deleteBill,
  deleteBillAttachment,
  downloadBillAttachment,
  getBill,
  listBillAttachments,
  postBill,
  updateBill,
  uploadBillAttachment,
  voidBill,
  type BillRequestBody,
} from '@/lib/api/bills'
import { listContacts } from '@/lib/api/contacts'
import { listItems } from '@/lib/api/items'
import { listTaxRates } from '@/lib/api/taxRates'
import { flattenPostableAccounts } from '@/lib/accounts'
import { formatBytes, formatDate, formatDateTime, formatMoney } from '@/lib/format'
import { confirmDialog } from '@/lib/dialogs'
import { downloadBlob, firstFileFrom } from '@/lib/download'
import { ApiError } from '@/lib/http'
import type { Attachment } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()
const billId = computed(() => route.params.id as string)

const {
  data: bill,
  isPending,
  error,
} = useQuery({
  queryKey: computed(() => billKeys.detail(billId.value)),
  queryFn: ({ signal }) => getBill(billId.value, signal),
})

const { data: contacts } = useQuery({
  queryKey: ['contacts', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listContacts({ includeArchived: false, size: 200 }, signal),
})
const { data: accountTree } = useQuery({
  queryKey: ['accounts', 'tree', false],
  queryFn: ({ signal }) => getAccountTree(false, signal),
})
const accounts = computed(() => flattenPostableAccounts(accountTree.value ?? []))
function accountLabel(accountId: number): string {
  const account = accounts.value.find((a) => a.id === accountId)
  return account ? `${account.code} ${account.name}` : `#${accountId}`
}
const { data: items } = useQuery({
  queryKey: ['items', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listItems({ includeArchived: false, size: 200 }, signal),
})
const { data: taxRates } = useQuery({
  queryKey: ['tax-rates', 'list', { includeArchived: false, size: 200 }],
  queryFn: ({ signal }) => listTaxRates({ includeArchived: false, size: 200 }, signal),
})

function invalidate() {
  return queryClient.invalidateQueries({ queryKey: billKeys.all })
}

const saveError = ref('')
const saveMutation = useMutation({
  mutationFn: (body: BillRequestBody) => updateBill(billId.value, body),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Draft saved')
  },
  onError: (err) => {
    saveError.value = err instanceof ApiError ? err.message : 'Unable to save this bill.'
  },
})

const postMutation = useMutation({
  mutationFn: () => postBill(billId.value),
  onSuccess: async (posted) => {
    await invalidate()
    toasts.success(`Posted as ${posted.billNumber}`)
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Unable to post this bill.'),
})

const deleteMutation = useMutation({
  mutationFn: () => deleteBill(billId.value),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Draft deleted')
    router.back()
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Unable to delete this bill.'),
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
  mutationFn: () => voidBill(billId.value, voidReason.value.trim() || null),
  onSuccess: async () => {
    await invalidate()
    voiding.value = false
    voidReason.value = ''
    toasts.success('Bill voided')
  },
  onError: (err) => {
    voidError.value = err instanceof ApiError ? err.message : 'Unable to void this bill.'
  },
})

// --- attachments ---
const { data: attachments } = useQuery({
  queryKey: computed(() => ['bills', 'attachments', billId.value]),
  queryFn: () => listBillAttachments(billId.value),
})

function invalidateAttachments() {
  return queryClient.invalidateQueries({ queryKey: ['bills', 'attachments', billId.value] })
}

const uploadMutation = useMutation({
  mutationFn: (file: Parameters<typeof uploadBillAttachment>[1]) => uploadBillAttachment(billId.value, file),
  onSuccess: () => invalidateAttachments(),
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Unable to upload that file.'),
})

function onFileChosen(event: Parameters<typeof firstFileFrom>[0]) {
  const file = firstFileFrom(event)
  if (file) uploadMutation.mutate(file)
}

const deleteAttachmentMutation = useMutation({
  mutationFn: (attachmentId: number) => deleteBillAttachment(billId.value, attachmentId),
  onSuccess: () => invalidateAttachments(),
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Unable to delete that attachment.'),
})

async function onDownloadAttachment(attachment: Attachment) {
  const blob = await downloadBillAttachment(billId.value, attachment.id)
  downloadBlob(blob, attachment.filename)
}

function onDeleteAttachment(attachment: Attachment) {
  if (!confirmDialog(`Delete ${attachment.filename}?`)) return
  deleteAttachmentMutation.mutate(attachment.id)
}

function statusPillClass(): string {
  if (!bill.value) return 'pill pill-neutral'
  if (bill.value.status === 'VOID') return 'pill pill-neutral'
  if (bill.value.overdue) return 'pill pill-red'
  if (bill.value.status === 'DRAFT') return 'pill pill-amber'
  return 'pill pill-green'
}
function statusLabel(): string {
  if (!bill.value) return ''
  if (bill.value.status === 'OPEN' && bill.value.overdue) return 'OVERDUE'
  return bill.value.status
}
</script>

<template>
  <AppShell>
    <template #title>
      <template v-if="bill">
        {{ bill.billNumber ?? 'Draft bill' }}
        <span :class="statusPillClass()">{{ statusLabel() }}</span>
      </template>
      <template v-else>Bill</template>
    </template>
    <template #sub><RouterLink to="/bills">← Back to bills</RouterLink></template>
    <template #actions>
      <template v-if="auth.isAdmin && bill">
        <button v-if="bill.status === 'DRAFT'" type="button" class="btn" @click="onDelete">Delete</button>
        <button
          v-if="bill.status === 'DRAFT'"
          type="button"
          class="btn btn-primary"
          :disabled="postMutation.isPending.value"
          @click="postMutation.mutate()"
        >
          {{ postMutation.isPending.value ? 'Posting…' : 'Post' }}
        </button>
        <button v-if="bill.status === 'OPEN' && !voiding" type="button" class="btn" @click="voiding = true">
          Void
        </button>
      </template>
    </template>

    <PageState :loading="isPending" :error="error" error-text="Unable to load this bill.">
      <template v-if="bill">
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
              {{ voidMutation.isPending.value ? 'Voiding…' : 'Reverse the posting and void this bill' }}
            </button>
            <button class="btn" type="button" @click="voiding = false">Cancel</button>
          </div>
          <div v-if="voidError" class="field-error" style="margin-top: 10px">{{ voidError }}</div>
        </div>

        <BillForm
          v-if="bill.status === 'DRAFT'"
          :key="bill.id"
          :initial="bill"
          :contacts="contacts?.content ?? []"
          :accounts="accounts"
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
              <div>Account</div>
              <div>Description</div>
              <div style="text-align: right">Qty</div>
              <div style="text-align: right">Unit price</div>
              <div style="text-align: right">Tax</div>
              <div style="text-align: right">Line total</div>
            </div>
            <div v-for="line in bill.lines" :key="line.id" class="entry-row">
              <div>{{ accountLabel(line.accountId) }}</div>
              <div>{{ line.description }}</div>
              <div class="amt">{{ line.quantity }}</div>
              <div class="amt">{{ formatMoney(line.unitPrice, bill.currency) }}</div>
              <div class="amt">{{ formatMoney(line.taxAmount, bill.currency) }}</div>
              <div class="amt">{{ formatMoney(line.lineTotal, bill.currency) }}</div>
            </div>
            <div class="totalsblock">
              <div class="num-row">
                <span>Subtotal</span><span class="v">{{ formatMoney(bill.subtotal, bill.currency) }}</span>
              </div>
              <div class="num-row">
                <span>Tax</span><span class="v">{{ formatMoney(bill.taxTotal, bill.currency) }}</span>
              </div>
              <div class="num-row grand">
                <span>Total</span><span class="v">{{ formatMoney(bill.grandTotal, bill.currency) }}</span>
              </div>
            </div>
            <p v-if="bill.notes" class="notes">{{ bill.notes }}</p>
          </div>

          <div class="card">
            <h2>Details</h2>
            <div class="meta-row">
              <span class="k">Vendor</span><span class="v">{{ bill.contactName }}</span>
            </div>
            <div class="meta-row">
              <span class="k">Vendor's bill #</span><span class="v">{{ bill.vendorReference }}</span>
            </div>
            <div class="meta-row">
              <span class="k">Billed</span><span class="v">{{ formatDate(bill.billDate) }}</span>
            </div>
            <div class="meta-row">
              <span class="k">Due</span><span class="v">{{ formatDate(bill.dueDate) }}</span>
            </div>
            <div v-if="bill.postedTransactionId" class="meta-row">
              <span class="k">Journal</span>
              <RouterLink class="v" :to="`/transactions/${bill.postedTransactionId}`">
                TXN-{{ bill.postedTransactionId }}
              </RouterLink>
            </div>
            <div v-else-if="bill.status === 'OPEN'" class="meta-row">
              <span class="k">Journal</span><span class="v">posting…</span>
            </div>
          </div>

          <div class="card">
            <h2>Attachments</h2>
            <div v-for="attachment in attachments ?? []" :key="attachment.id" class="attachment-row">
              <div>
                <a href="#" @click.prevent="onDownloadAttachment(attachment)">{{ attachment.filename }}</a>
                <div class="attachment-meta">
                  {{ formatBytes(attachment.sizeBytes) }} · {{ formatDateTime(attachment.createdAt) }}
                </div>
              </div>
              <button
                v-if="auth.isAdmin"
                class="linkbtn"
                type="button"
                @click="onDeleteAttachment(attachment)"
              >
                Delete
              </button>
            </div>
            <p v-if="attachments && attachments.length === 0" class="notes" style="margin-top: 0">
              No attachments yet.
            </p>
            <label v-if="auth.isAdmin" class="upload-btn">
              {{ uploadMutation.isPending.value ? 'Uploading…' : '+ Add receipt' }}
              <input type="file" :disabled="uploadMutation.isPending.value" @change="onFileChosen" />
            </label>
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
  grid-template-columns: 1.4fr 1.6fr 70px 110px 100px 110px;
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
  grid-template-columns: 1.4fr 1.6fr 70px 110px 100px 110px;
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
.attachment-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line-soft);
  font-size: 12.5px;
}
.attachment-row:last-of-type {
  border-bottom: none;
}
.attachment-meta {
  color: var(--ink-faint);
  font-size: 11px;
  margin-top: 2px;
}
.linkbtn {
  background: none;
  border: none;
  color: var(--red);
  font-size: 11.5px;
  cursor: pointer;
  padding: 2px 0;
  flex: none;
}
.linkbtn:hover {
  text-decoration: underline;
}
.upload-btn {
  display: inline-block;
  margin-top: 12px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11.5px;
  padding: 7px 12px;
  border-radius: 5px;
  border: 1px dashed var(--line);
  color: var(--ink-soft);
  cursor: pointer;
}
.upload-btn:hover {
  border-color: var(--green-line);
  color: var(--green);
}
.upload-btn input[type='file'] {
  display: none;
}
</style>
