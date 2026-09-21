<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import DataTable, { type Column } from '@/components/data/DataTable.vue'
import Pager from '@/components/data/Pager.vue'
import SearchInput from '@/components/data/SearchInput.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import { useListQuery } from '@/composables/useListQuery'
import AppShell from '@/layouts/AppShell.vue'
import { invoiceKeys, listInvoices } from '@/lib/api/invoices'
import { formatDate, formatMoney } from '@/lib/format'
import type { InvoiceListItem, InvoiceStatus } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'

const COLUMNS: Column[] = [
  { key: 'invoiceNumber', label: 'Number', class: 'mono' },
  { key: 'contactName', label: 'Customer' },
  { key: 'issueDate', label: 'Issued', sortBy: 'issueDate', class: 'mono' },
  { key: 'dueDate', label: 'Due', sortBy: 'dueDate', class: 'mono' },
  { key: 'status', label: 'Status' },
  { key: 'grandTotal', label: 'Total', align: 'right' },
]

const auth = useAuthStore()

const { state, params, qInput, setFilter, setPage, setSort } = useListQuery({
  defaultSort: 'issueDate,desc',
  filters: { status: '' },
})

const listParams = computed(() => ({
  q: params.value.q,
  status: (params.value.status as InvoiceStatus | '') || null,
  page: params.value.page,
  size: params.value.size,
  sort: params.value.sort,
}))

const { data, isPending, error } = useQuery({
  queryKey: computed(() => invoiceKeys.list(listParams.value)),
  queryFn: ({ signal }) => listInvoices(listParams.value, signal),
})

const statusFilter = computed({
  get: () => String(state.value.status ?? ''),
  set: (value: string) => setFilter('status', value),
})

function statusPillClass(invoice: InvoiceListItem): string {
  if (invoice.status === 'VOID') return 'pill pill-neutral'
  if (invoice.overdue) return 'pill pill-red'
  if (invoice.status === 'DRAFT') return 'pill pill-amber'
  return 'pill pill-green'
}
function statusLabel(invoice: InvoiceListItem): string {
  if (invoice.status === 'SENT' && invoice.overdue) return 'OVERDUE'
  if (invoice.status === 'SENT' && invoice.paid) return 'PAID'
  return invoice.status
}
</script>

<template>
  <AppShell>
    <template #title>Invoices</template>
    <template #sub>
      <span v-if="data">{{ data.totalElements }} invoices</span>
    </template>
    <template #actions>
      <RouterLink v-if="auth.isAdmin" class="btn btn-primary" to="/invoices/new">+ New invoice</RouterLink>
    </template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Search number or customer…" />
      <select v-model="statusFilter" class="input" style="width: 150px">
        <option value="">All statuses</option>
        <option value="DRAFT">Draft</option>
        <option value="SENT">Sent</option>
        <option value="VOID">Void</option>
      </select>
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.content.length === 0"
        error-text="Unable to load invoices."
        :skeleton-rows="6"
      >
        <template #empty>
          <EmptyState title="No invoices match that search." hint="Try a different term or filter." />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: InvoiceListItem) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:invoiceNumber="{ row }">
            <RouterLink :to="`/invoices/${row.id}`">{{ row.invoiceNumber ?? 'DRAFT' }}</RouterLink>
          </template>
          <template #cell:issueDate="{ row }">{{ formatDate(row.issueDate) }}</template>
          <template #cell:dueDate="{ row }">{{ formatDate(row.dueDate) }}</template>
          <template #cell:status="{ row }">
            <span :class="statusPillClass(row)">{{ statusLabel(row) }}</span>
          </template>
          <template #cell:grandTotal="{ row }">{{ formatMoney(row.grandTotal, row.currency) }}</template>
        </DataTable>

        <Pager
          v-if="data && data.totalPages > 1"
          :page="data.page"
          :size="data.size"
          :total-elements="data.totalElements"
          :total-pages="data.totalPages"
          noun="invoices"
          @update:page="setPage"
        />
      </PageState>
    </div>
  </AppShell>
</template>
