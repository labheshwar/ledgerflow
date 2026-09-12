<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import DataTable, { type Column } from '@/components/data/DataTable.vue'
import FilterChips from '@/components/data/FilterChips.vue'
import Pager from '@/components/data/Pager.vue'
import SearchInput from '@/components/data/SearchInput.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import { auditLogKeys, listAuditLog } from '@/lib/api/audit-log'
import { formatDateTime } from '@/lib/format'
import type { AuditLogEntry } from '@/lib/types'
import { useListQuery } from '@/composables/useListQuery'

const ENTITY_OPTIONS = [
  { value: '', label: 'All' },
  { value: 'TRANSACTION', label: 'Transaction' },
  { value: 'ACCOUNT', label: 'Account' },
]

const COLUMNS: Column[] = [
  { key: 'createdAt', label: 'Time', sortBy: 'createdAt', class: 'mono' },
  { key: 'actor', label: 'Actor', sortBy: 'actor' },
  { key: 'action', label: 'Action', sortBy: 'action' },
  { key: 'entity', label: 'Entity', class: 'mono' },
  { key: 'detail', label: 'Detail' },
]

const { state, params, qInput, setFilter, setPage, setSort } = useListQuery({
  filters: { entityType: '' },
  defaultSort: 'createdAt,desc',
})

const { data, isPending, error } = useQuery({
  queryKey: computed(() => auditLogKeys.list(params.value)),
  queryFn: ({ signal }) => listAuditLog(params.value, signal),
})

const entityFilter = computed({
  get: () => String(state.value.entityType ?? ''),
  set: (value: string) => setFilter('entityType', value),
})

function actionPillClass(action: string): string {
  return action === 'CREATE' ? 'pill pill-green' : 'pill pill-neutral'
}

/** The stored before/after snapshots are JSON; flatten them for one table cell. */
function detailFor(entry: AuditLogEntry): string {
  const state = entry.afterState ?? entry.beforeState
  if (!state) return '—'
  try {
    return Object.entries(JSON.parse(state) as Record<string, unknown>)
      .map(([k, v]) => `${k}: ${v}`)
      .join(', ')
  } catch {
    return state
  }
}
</script>

<template>
  <AppShell>
    <template #title>Audit Log</template>
    <template #sub>Append-only record of every posting and account change</template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Search actor, entity, action…" />
      <FilterChips v-model="entityFilter" :options="ENTITY_OPTIONS" />
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.content.length === 0"
        error-text="Unable to load the audit log."
        :skeleton-rows="8"
      >
        <template #empty>
          <EmptyState title="No audit entries match those filters." />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: AuditLogEntry) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:createdAt="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          <template #cell:action="{ row }">
            <span :class="actionPillClass(row.action)">{{ row.action }}</span>
          </template>
          <template #cell:entity="{ row }">
            <RouterLink v-if="row.entityType === 'TRANSACTION'" :to="`/transactions/${row.entityId}`">
              TXN-{{ row.entityId }}
            </RouterLink>
            <RouterLink v-else-if="row.entityType === 'ACCOUNT'" :to="`/accounts/${row.entityId}`">
              Account #{{ row.entityId }}
            </RouterLink>
            <template v-else>{{ row.entityType }} #{{ row.entityId }}</template>
          </template>
          <template #cell:detail="{ row }">
            <span class="detail">{{ detailFor(row) }}</span>
          </template>
        </DataTable>

        <Pager
          v-if="data && data.totalPages > 1"
          :page="data.page"
          :size="data.size"
          :total-elements="data.totalElements"
          :total-pages="data.totalPages"
          noun="entries"
          @update:page="setPage"
        />
      </PageState>
    </div>
    <div class="foot-note">Entries are immutable — nothing here can be edited or deleted, only added to.</div>
  </AppShell>
</template>

<style scoped>
.detail {
  color: var(--ink-soft);
  font-size: 12.5px;
}
.foot-note {
  margin-top: 12px;
  font-size: 11.5px;
  color: var(--ink-faint);
}
</style>
