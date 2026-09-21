<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { downloadPublicInvoicePdf, getPublicInvoice } from '@/lib/api/publicInvoices'
import { downloadBlob } from '@/lib/download'
import { formatDate, formatMoney } from '@/lib/format'

const route = useRoute()
const token = computed(() => route.params.token as string)

const {
  data: invoice,
  isPending,
  error,
} = useQuery({
  queryKey: computed(() => ['public-invoice', token.value]),
  queryFn: ({ signal }) => getPublicInvoice(token.value, signal),
})

const downloading = ref(false)
async function download() {
  if (!invoice.value) return
  downloading.value = true
  try {
    const blob = await downloadPublicInvoicePdf(token.value)
    downloadBlob(blob, `${invoice.value.invoiceNumber ?? 'invoice'}.pdf`)
  } finally {
    downloading.value = false
  }
}
</script>

<template>
  <div class="wrap theme-light">
    <div class="card">
      <div class="mark">LF</div>

      <p v-if="isPending">Loading…</p>
      <p v-else-if="error" class="field-error">This link is no longer valid.</p>

      <template v-else-if="invoice">
        <div class="head">
          <h1>Invoice {{ invoice.invoiceNumber }}</h1>
          <button type="button" class="btn btn-primary" :disabled="downloading" @click="download">
            {{ downloading ? 'Preparing…' : 'Download PDF' }}
          </button>
        </div>
        <div class="sub">
          Billed to {{ invoice.contactName }} · Issued {{ formatDate(invoice.issueDate) }} · Due
          {{ formatDate(invoice.dueDate) }}
        </div>

        <table>
          <thead>
            <tr>
              <th>Description</th>
              <th class="num">Qty</th>
              <th class="num">Unit price</th>
              <th class="num">Tax</th>
              <th class="num">Line total</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="line in invoice.lines" :key="line.id">
              <td>{{ line.description }}</td>
              <td class="num">{{ line.quantity }}</td>
              <td class="num">{{ formatMoney(line.unitPrice, invoice.currency) }}</td>
              <td class="num">{{ formatMoney(line.taxAmount, invoice.currency) }}</td>
              <td class="num">{{ formatMoney(line.lineTotal, invoice.currency) }}</td>
            </tr>
          </tbody>
        </table>

        <div class="totals">
          <div class="row">
            <span>Subtotal</span><span>{{ formatMoney(invoice.subtotal, invoice.currency) }}</span>
          </div>
          <div class="row">
            <span>Tax</span><span>{{ formatMoney(invoice.taxTotal, invoice.currency) }}</span>
          </div>
          <div class="row grand">
            <span>Total</span><span>{{ formatMoney(invoice.grandTotal, invoice.currency) }}</span>
          </div>
        </div>

        <p v-if="invoice.notes" class="notes">{{ invoice.notes }}</p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.wrap {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  overflow: auto;
  padding: 40px 16px;
}
.card {
  width: 100%;
  max-width: 640px;
  background: var(--raised);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 32px;
}
.mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--green);
  color: var(--paper);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 16px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.card h1 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}
.sub {
  color: var(--ink-soft);
  font-size: 12.5px;
  margin: 6px 0 20px;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th {
  text-align: left;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--ink-soft);
  padding: 8px 6px;
  border-bottom: 1px solid var(--line);
}
th.num,
td.num {
  text-align: right;
}
td {
  padding: 8px 6px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
}
.totals {
  width: 240px;
  margin-left: auto;
  margin-top: 14px;
}
.totals .row {
  display: flex;
  justify-content: space-between;
  padding: 4px 6px;
  font-size: 13px;
}
.totals .grand {
  font-weight: 600;
  font-size: 14px;
  border-top: 1px solid var(--line);
  margin-top: 4px;
  padding-top: 8px;
}
.notes {
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
  font-size: 12.5px;
  color: var(--ink-soft);
}
</style>
