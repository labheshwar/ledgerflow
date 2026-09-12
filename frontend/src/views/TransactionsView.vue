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
import { formatDateTime } from '@/lib/format'
import type { TransactionListItem } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import { useListQuery } from '@/composables/useListQuery'

const COLUMNS: Column[] = [
  { key: 'id', label: 'Reference', sortBy: 'idempotencyKey', class: 'mono' },
  { key: 'description', label: 'Description', sortBy: 'description' },
  { key: 'status', label: 'Status', sortBy: 'status' },
  { key: 'createdAt', label: 'Posted at', sortBy: 'createdAt', class: 'mono' },
]

const auth = useAuthStore()
const { state, params, qInput, setPage, setSort } = useListQuery({ defaultSort: 'createdAt,desc' })

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
        Post transaction
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
