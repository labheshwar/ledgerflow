<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'
import DataTable, { type Column } from '@/components/data/DataTable.vue'
import FilterChips from '@/components/data/FilterChips.vue'
import Pager from '@/components/data/Pager.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import { listBatches, reconciliationKeys, triggerReconciliation } from '@/lib/api/reconciliation'
import { ApiError } from '@/lib/http'
import { formatDuration, formatRelativeTime, reconciliationPillClass } from '@/lib/format'
import type { ReconciliationBatchSummary } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { useListQuery } from '@/composables/useListQuery'

const STATUS_OPTIONS = [
  { value: '', label: 'All' },
  { value: 'PENDING', label: 'Pending' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'FAILED', label: 'Failed' },
]

const COLUMNS: Column[] = [
  { key: 'id', label: 'Batch', class: 'mono' },
  { key: 'status', label: 'Status', sortBy: 'status' },
  { key: 'mismatched', label: 'Discrepancies', align: 'right' },
  { key: 'triggeredAt', label: 'Started', sortBy: 'triggeredAt', class: 'mono' },
  { key: 'duration', label: 'Duration', sortBy: 'completedAt', class: 'mono' },
]

const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()

const { state, params, setFilter, setPage, setSort } = useListQuery({
  filters: { status: '' },
  defaultSort: 'triggeredAt,desc',
})

const { data, isPending, error } = useQuery({
  queryKey: computed(() => reconciliationKeys.list(params.value)),
  queryFn: ({ signal }) => listBatches(params.value, signal),
})

const statusFilter = computed({
  get: () => String(state.value.status ?? ''),
  set: (value: string) => setFilter('status', value),
})

const trigger = useMutation({
  mutationFn: triggerReconciliation,
  onSuccess: (batch) => {
    // The worker consumes the message asynchronously, so the batch lands as
    // PENDING and flips to COMPLETED moments later -- refetch to show it.
    queryClient.invalidateQueries({ queryKey: reconciliationKeys.all })
    toasts.success(`Reconciliation BATCH-${batch.id} queued`)
  },
  onError: (e) => {
    toasts.error(e instanceof ApiError ? e.message : 'Unable to trigger reconciliation.')
  },
})
</script>

<template>
  <AppShell>
    <template #title>Reconciliation</template>
    <template #sub>Async batches compare ledger balances against the external statement feed</template>
    <template #actions>
      <button
        v-if="auth.isAdmin"
        class="btn btn-primary"
        :disabled="trigger.isPending.value"
        @click="trigger.mutate()"
      >
        {{ trigger.isPending.value ? 'Starting…' : 'Run reconciliation' }}
      </button>
    </template>

    <div class="toolbar">
      <FilterChips v-model="statusFilter" :options="STATUS_OPTIONS" />
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.content.length === 0"
        error-text="Unable to load reconciliation batches."
        :skeleton-rows="5"
      >
        <template #empty>
          <EmptyState
            title="No reconciliation batches yet."
            hint="Run one to compare ledger balances against the statement feed."
          />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: ReconciliationBatchSummary) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:id="{ row }">
            <RouterLink :to="`/reconciliation/${row.id}`">BATCH-{{ row.id }}</RouterLink>
          </template>
          <template #cell:status="{ row }">
            <span :class="reconciliationPillClass(row.status)">{{ row.status.replace('_', ' ') }}</span>
          </template>
          <template #cell:triggeredAt="{ row }">{{ formatRelativeTime(row.triggeredAt) }}</template>
          <template #cell:duration="{ row }">
            {{ formatDuration(row.triggeredAt, row.completedAt) }}
          </template>
        </DataTable>

        <Pager
          v-if="data && data.totalPages > 1"
          :page="data.page"
          :size="data.size"
          :total-elements="data.totalElements"
          :total-pages="data.totalPages"
          noun="batches"
          @update:page="setPage"
        />
      </PageState>
    </div>
  </AppShell>
</template>
