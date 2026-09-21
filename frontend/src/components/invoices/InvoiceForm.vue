<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { InvoiceLineRequestBody, InvoiceRequestBody } from '@/lib/api/invoices'
import { computeInvoiceTotals, type InvoiceLineDraftLike } from '@/lib/invoice-totals'
import type { Contact, Item, InvoiceDetail, TaxRate } from '@/lib/types'

const props = defineProps<{
  contacts: Contact[]
  items: Item[]
  taxRates: TaxRate[]
  /** Prefills the form for editing a draft; omit to create a new one. */
  initial?: InvoiceDetail | null
  submitting: boolean
  errorText?: string
}>()

const emit = defineEmits<{ submit: [InvoiceRequestBody] }>()

interface DraftLine {
  key: number
  itemId: number | null
  description: string
  quantity: string
  unitPrice: string
  taxRateId: number | null
}

let nextKey = 1
function blankLine(): DraftLine {
  return { key: nextKey++, itemId: null, description: '', quantity: '1', unitPrice: '', taxRateId: null }
}

const contactId = ref<number | null>(props.initial?.contactId ?? null)
const issueDate = ref(props.initial?.issueDate ?? new Date().toISOString().slice(0, 10))
const dueDate = ref(props.initial?.dueDate ?? new Date().toISOString().slice(0, 10))
const notes = ref(props.initial?.notes ?? '')

const lines = reactive<DraftLine[]>(
  props.initial && props.initial.lines.length > 0
    ? props.initial.lines.map((l) => ({
        key: nextKey++,
        itemId: l.itemId,
        description: l.description,
        quantity: String(l.quantity),
        unitPrice: String(l.unitPrice),
        taxRateId: l.taxRateId,
      }))
    : [blankLine()],
)

function addLine() {
  lines.push(blankLine())
}
function removeLine(key: number) {
  if (lines.length <= 1) return
  const idx = lines.findIndex((l) => l.key === key)
  if (idx !== -1) lines.splice(idx, 1)
}

/** Picking an item resets the row to that item's own defaults. */
function applyItem(line: DraftLine) {
  if (line.itemId == null) return
  const item = props.items.find((i) => i.id === line.itemId)
  if (!item) return
  line.description = item.name
  if (item.defaultUnitPrice != null) line.unitPrice = String(item.defaultUnitPrice)
  line.taxRateId = item.defaultTaxRateId
}

function taxRatePercent(taxRateId: number | null): number | null {
  if (taxRateId == null) return null
  return props.taxRates.find((t) => t.id === taxRateId)?.rate ?? null
}

const totalsInput = computed<InvoiceLineDraftLike[]>(() =>
  lines.map((l) => ({
    quantity: l.quantity,
    unitPrice: l.unitPrice,
    taxRatePercent: taxRatePercent(l.taxRateId),
  })),
)
const totals = computed(() => computeInvoiceTotals(totalsInput.value))

const submitAttempted = ref(false)

const contactError = computed(() =>
  submitAttempted.value && contactId.value == null ? 'A customer is required.' : '',
)
const dateError = computed(() => {
  if (!submitAttempted.value) return ''
  if (!issueDate.value || !dueDate.value) return 'Both dates are required.'
  return dueDate.value < issueDate.value ? 'The due date cannot be before the issue date.' : ''
})
function lineError(line: DraftLine): string {
  if (!submitAttempted.value) return ''
  if (!line.description.trim()) return 'A description is required.'
  const qty = parseFloat(line.quantity)
  if (!(qty > 0)) return 'Quantity must be greater than zero.'
  const price = parseFloat(line.unitPrice)
  if (Number.isNaN(price) || price < 0) return 'Unit price cannot be negative.'
  return ''
}
const linesValid = computed(() => lines.every((l) => !lineError(l)))
const isValid = computed(() => !contactError.value && !dateError.value && linesValid.value)

function submit() {
  submitAttempted.value = true
  if (!isValid.value) return

  const body: InvoiceRequestBody = {
    contactId: contactId.value as number,
    issueDate: issueDate.value,
    dueDate: dueDate.value,
    notes: notes.value.trim() || null,
    lines: lines.map((l): InvoiceLineRequestBody => ({
      itemId: l.itemId,
      description: l.description.trim(),
      quantity: parseFloat(l.quantity),
      unitPrice: parseFloat(l.unitPrice),
      taxRateId: l.taxRateId,
    })),
  }
  emit('submit', body)
}

defineExpose({ submit })
</script>

<template>
  <div class="layout">
    <div class="card">
      <h2>Invoice</h2>
      <div class="fieldrow">
        <div class="field" style="flex: 1">
          <label>Customer</label>
          <select v-model.number="contactId" class="input" :class="{ error: contactError }">
            <option :value="null">— Select a customer —</option>
            <option v-for="c in contacts" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <div v-if="contactError" class="row-error">{{ contactError }}</div>
        </div>
        <div class="field" style="flex: 0 0 150px">
          <label>Issue date</label>
          <input v-model="issueDate" type="date" class="input" />
        </div>
        <div class="field" style="flex: 0 0 150px">
          <label>Due date</label>
          <input v-model="dueDate" type="date" class="input" />
        </div>
      </div>
      <div v-if="dateError" class="row-error" style="margin-bottom: 10px">{{ dateError }}</div>

      <div class="row-head">
        <div>Item</div>
        <div>Description</div>
        <div style="text-align: right">Qty</div>
        <div style="text-align: right">Unit price</div>
        <div>Tax</div>
        <div style="text-align: right">Line total</div>
        <div></div>
      </div>
      <div v-for="(line, i) in lines" :key="line.key" class="entry-row">
        <select v-model.number="line.itemId" class="input" @change="applyItem(line)">
          <option :value="null">—</option>
          <option v-for="it in items" :key="it.id" :value="it.id">{{ it.name }}</option>
        </select>
        <input v-model="line.description" class="input" :class="{ error: lineError(line) }" />
        <input v-model="line.quantity" class="input amt" placeholder="1" />
        <input v-model="line.unitPrice" class="input amt" placeholder="0.00" />
        <select v-model.number="line.taxRateId" class="input">
          <option :value="null">None</option>
          <option v-for="t in taxRates" :key="t.id" :value="t.id">{{ t.name }} ({{ t.rate }}%)</option>
        </select>
        <div class="amt linetotal">{{ totals.lines[i]?.lineTotal.toFixed(2) ?? '0.00' }}</div>
        <button class="rm" type="button" :disabled="lines.length <= 1" @click="removeLine(line.key)">
          ×
        </button>
      </div>
      <div v-if="submitAttempted && !linesValid" class="row-error">
        Every line needs a description and a positive quantity.
      </div>

      <button class="addrow" type="button" @click="addLine">+ Add line</button>

      <div class="field" style="margin-top: 16px">
        <label>Notes (optional)</label>
        <textarea v-model="notes" class="input" rows="2" placeholder="Terms, thank-you note, anything else" />
      </div>
    </div>

    <div class="card totalscard">
      <h2>Totals</h2>
      <div class="num-row">
        <span>Subtotal</span><span class="v">{{ totals.subtotal.toFixed(2) }}</span>
      </div>
      <div class="num-row">
        <span>Tax</span><span class="v">{{ totals.taxTotal.toFixed(2) }}</span>
      </div>
      <hr />
      <div class="num-row grand">
        <span>Total</span><span class="v">{{ totals.grandTotal.toFixed(2) }}</span>
      </div>
      <div v-if="errorText" class="field-error" style="margin-top: 10px">{{ errorText }}</div>
      <button class="submit" type="button" :disabled="submitting" @click="submit">
        {{ submitting ? 'Saving…' : initial ? 'Save changes' : 'Create draft' }}
      </button>
    </div>
  </div>
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
.fieldrow {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.row-head {
  display: grid;
  grid-template-columns: 1fr 1.4fr 70px 100px 140px 90px 28px;
  gap: 8px;
  padding: 10px 2px 8px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--ink-soft);
  border-top: 1px solid var(--line);
}
.entry-row {
  display: grid;
  grid-template-columns: 1fr 1.4fr 70px 100px 140px 90px 28px;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
}
.amt {
  font-family: 'IBM Plex Mono', monospace;
  text-align: right;
}
.linetotal {
  font-size: 13px;
  color: var(--ink-soft);
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
