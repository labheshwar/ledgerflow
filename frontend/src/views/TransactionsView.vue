<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import DataTable, { type Column } from '@/components/data/DataTable.vue'
import Pager from '@/components/data/Pager.vue'
import SearchInput from '@/components/data/SearchInput.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import { listTransactions, transactionKeys } from '@/lib/api/transactions'
import { formatDate, formatDateTime } from '@/lib/format'
import type { TransactionListItem } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import { useListQuery } from '@/composables/useListQuery'

const COLUMNS: Column[] = [
  { key: 'id', label: 'Reference', sortBy: 'idempotencyKey', class: 'mono' },
  { key: 'description', label: 'Description', sortBy: 'description' },
  { key: 'status', label: 'Status', sortBy: 'status' },
  // The accounting date, not the insert timestamp -- a back-dated
  // correction belongs in the period it corrects, and sorting by when the
  // row happened to be written would bury it at the end of the list.
  { key: 'txnDate', label: 'Date', sortBy: 'txnDate', class: 'mono' },
  { key: 'createdAt', label: 'Entered', sortBy: 'createdAt', class: 'mono' },
]

const auth = useAuthStore()
const { state, params, qInput, setPage, setSort } = useListQuery({ defaultSort: 'txnDate,desc' })

const { data, isPending, error } = useQuery({
  queryKey: computed(() => transactionKeys.list(params.value)),
  queryFn: ({ signal }) => listTransactions(params.value, signal),
})
</script>

<template>
  <AppShell>
    <template #title>Transactions</template>
    <template #sub>
      <span v-if="data">{{ data.totalElements }} transactions</span>
    </template>
    <template #actions>
      <RouterLink v-if="auth.isAdmin" class="btn btn-primary" to="/transactions/new">
        Post journal entry
      </RouterLink>
    </template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Search description or key…" />
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.content.length === 0"
        error-text="Unable to load transactions."
        :skeleton-rows="6"
      >
        <template #empty>
          <EmptyState title="No transactions match that search." hint="Try a different term." />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: TransactionListItem) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:id="{ row }">
            <RouterLink :to="`/transactions/${row.id}`">TXN-{{ row.id }}</RouterLink>
          </template>
          <template #cell:description="{ row }">{{ row.description || '—' }}</template>
          <template #cell:status="{ row }">
            <span class="pill pill-green">{{ row.status }}</span>
          </template>
          <template #cell:txnDate="{ row }">
            <span>{{ formatDate(row.txnDate) }}</span>
          </template>

          <template #cell:createdAt="{ row }">
            <span style="color: var(--ink-soft)">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </DataTable>

        <Pager
          v-if="data && data.totalPages > 1"
          :page="data.page"
          :size="data.size"
          :total-elements="data.totalElements"
          :total-pages="data.totalPages"
          noun="transactions"
          @update:page="setPage"
        />
      </PageState>
    </div>
  </AppShell>
</template>
