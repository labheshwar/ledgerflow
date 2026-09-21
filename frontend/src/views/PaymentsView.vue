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
import { listPayments, paymentKeys } from '@/lib/api/payments'
import { formatDate, formatMoney } from '@/lib/format'
import type { Payment, PaymentDirection } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'

const COLUMNS: Column[] = [
  { key: 'paymentDate', label: 'Date', sortBy: 'paymentDate', class: 'mono' },
  { key: 'direction', label: 'Direction' },
  { key: 'contactName', label: 'Contact' },
  { key: 'allocations', label: 'Settles' },
  { key: 'status', label: 'Status' },
  { key: 'amount', label: 'Amount', align: 'right' },
]

const auth = useAuthStore()

const { state, params, qInput, setFilter, setPage, setSort } = useListQuery({
  defaultSort: 'paymentDate,desc',
  filters: { direction: '' },
})

const listParams = computed(() => ({
  q: params.value.q,
  direction: (params.value.direction as PaymentDirection | '') || null,
  page: params.value.page,
  size: params.value.size,
  sort: params.value.sort,
}))

const { data, isPending, error } = useQuery({
  queryKey: computed(() => paymentKeys.list(listParams.value)),
  queryFn: ({ signal }) => listPayments(listParams.value, signal),
})

const directionFilter = computed({
  get: () => String(state.value.direction ?? ''),
  set: (value: string) => setFilter('direction', value),
})

function statusPillClass(payment: Payment): string {
  return payment.status === 'VOID' ? 'pill pill-neutral' : 'pill pill-green'
}
</script>

<template>
  <AppShell>
    <template #title>Payments</template>
    <template #sub>
      <span v-if="data">{{ data.totalElements }} payments</span>
    </template>
    <template #actions>
      <RouterLink v-if="auth.isAdmin" class="btn btn-primary" to="/payments/new">+ Record payment</RouterLink>
    </template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Search contact…" />
      <select v-model="directionFilter" class="input" style="width: 180px">
        <option value="">Received and paid</option>
        <option value="RECEIVED">Received from customer</option>
        <option value="PAID">Paid to vendor</option>
      </select>
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.content.length === 0"
        error-text="Unable to load payments."
        :skeleton-rows="6"
      >
        <template #empty>
          <EmptyState title="No payments match that search." hint="Try a different term or filter." />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: Payment) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:paymentDate="{ row }">
            <RouterLink :to="`/payments/${row.id}`">{{ formatDate(row.paymentDate) }}</RouterLink>
          </template>
          <template #cell:direction="{ row }">{{
            row.direction === 'RECEIVED' ? 'Received' : 'Paid'
          }}</template>
          <template #cell:allocations="{ row }">
            {{ row.allocations.length === 0 ? 'Prepayment only' : `${row.allocations.length} document(s)` }}
          </template>
          <template #cell:status="{ row }">
            <span :class="statusPillClass(row)">{{ row.status }}</span>
          </template>
          <template #cell:amount="{ row }">{{ formatMoney(row.amount, row.currency) }}</template>
        </DataTable>

        <Pager
          v-if="data && data.totalPages > 1"
          :page="data.page"
          :size="data.size"
          :total-elements="data.totalElements"
          :total-pages="data.totalPages"
          noun="payments"
          @update:page="setPage"
        />
      </PageState>
    </div>
  </AppShell>
</template>
