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
import { billKeys, listBills } from '@/lib/api/bills'
import { formatDate, formatMoney } from '@/lib/format'
import type { BillListItem, BillStatus } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'

const COLUMNS: Column[] = [
  { key: 'billNumber', label: 'Number', class: 'mono' },
  { key: 'vendorReference', label: "Vendor's #", class: 'mono' },
  { key: 'contactName', label: 'Vendor' },
  { key: 'billDate', label: 'Billed', sortBy: 'billDate', class: 'mono' },
  { key: 'dueDate', label: 'Due', sortBy: 'dueDate', class: 'mono' },
  { key: 'status', label: 'Status' },
  { key: 'grandTotal', label: 'Total', align: 'right' },
]

const auth = useAuthStore()

const { state, params, qInput, setFilter, setPage, setSort } = useListQuery({
  defaultSort: 'billDate,desc',
  filters: { status: '' },
})

const listParams = computed(() => ({
  q: params.value.q,
  status: (params.value.status as BillStatus | '') || null,
  page: params.value.page,
  size: params.value.size,
  sort: params.value.sort,
}))

const { data, isPending, error } = useQuery({
  queryKey: computed(() => billKeys.list(listParams.value)),
  queryFn: ({ signal }) => listBills(listParams.value, signal),
})

const statusFilter = computed({
  get: () => String(state.value.status ?? ''),
  set: (value: string) => setFilter('status', value),
})

function statusPillClass(bill: BillListItem): string {
  if (bill.status === 'VOID') return 'pill pill-neutral'
  if (bill.overdue) return 'pill pill-red'
  if (bill.status === 'DRAFT') return 'pill pill-amber'
  return 'pill pill-green'
}
function statusLabel(bill: BillListItem): string {
  if (bill.status === 'OPEN' && bill.overdue) return 'OVERDUE'
  if (bill.status === 'OPEN' && bill.paid) return 'PAID'
  return bill.status
}
</script>

<template>
  <AppShell>
    <template #title>Bills</template>
    <template #sub>
      <span v-if="data">{{ data.totalElements }} bills</span>
    </template>
    <template #actions>
      <RouterLink v-if="auth.isAdmin" class="btn btn-primary" to="/bills/new">+ New bill</RouterLink>
    </template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Search number or vendor…" />
      <select v-model="statusFilter" class="input" style="width: 150px">
        <option value="">All statuses</option>
        <option value="DRAFT">Draft</option>
        <option value="OPEN">Open</option>
        <option value="VOID">Void</option>
      </select>
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.content.length === 0"
        error-text="Unable to load bills."
        :skeleton-rows="6"
      >
        <template #empty>
          <EmptyState title="No bills match that search." hint="Try a different term or filter." />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: BillListItem) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:billNumber="{ row }">
            <RouterLink :to="`/bills/${row.id}`">{{ row.billNumber ?? 'DRAFT' }}</RouterLink>
          </template>
          <template #cell:billDate="{ row }">{{ formatDate(row.billDate) }}</template>
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
          noun="bills"
          @update:page="setPage"
        />
      </PageState>
    </div>
  </AppShell>
</template>
