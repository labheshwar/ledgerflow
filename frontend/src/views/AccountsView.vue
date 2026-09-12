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
import { accountKeys, listAccounts } from '@/lib/api/accounts'
import { accountTypePillClass, formatMoney, formatRelativeTime } from '@/lib/format'
import type { Account, AccountType } from '@/lib/types'
import { useListQuery } from '@/composables/useListQuery'

const TYPE_OPTIONS = [
  { value: '', label: 'All' },
  ...(['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE'] as AccountType[]).map((type) => ({
    value: type,
    label: type.charAt(0) + type.slice(1).toLowerCase(),
  })),
]

const COLUMNS: Column[] = [
  { key: 'name', label: 'Name', sortBy: 'name' },
  { key: 'type', label: 'Type', sortBy: 'type' },
  { key: 'currency', label: 'Currency', sortBy: 'currency', class: 'mono' },
  { key: 'balance', label: 'Balance', align: 'right', sortBy: 'balance' },
  { key: 'updatedAt', label: 'Last updated', sortBy: 'updatedAt', class: 'mono' },
]

const { state, params, qInput, setFilter, setPage, setSort } = useListQuery({
  filters: { type: '' },
  defaultSort: 'name,asc',
})

const { data, isPending, error } = useQuery({
  queryKey: computed(() => accountKeys.list(params.value)),
  queryFn: ({ signal }) => listAccounts(params.value, signal),
})

const typeFilter = computed({
  get: () => String(state.value.type ?? ''),
  set: (value: string) => setFilter('type', value),
})
</script>

<template>
  <AppShell>
    <template #title>Accounts</template>
    <template #sub>
      <span v-if="data">{{ data.totalElements }} accounts</span>
    </template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Search accounts…" />
      <FilterChips v-model="typeFilter" :options="TYPE_OPTIONS" />
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.content.length === 0"
        error-text="Unable to load accounts."
        :skeleton-rows="6"
      >
        <template #empty>
          <EmptyState
            title="No accounts match those filters."
            hint="Try a different search term or clear the type filter."
          />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: Account) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:name="{ row }">
            <RouterLink :to="`/accounts/${row.id}`">{{ row.name }}</RouterLink>
          </template>
          <template #cell:type="{ row }">
            <span :class="accountTypePillClass(row.type)">{{ row.type }}</span>
          </template>
          <template #cell:balance="{ row }">{{ formatMoney(row.balance) }}</template>
          <template #cell:updatedAt="{ row }">
            <span style="color: var(--ink-soft)">{{ formatRelativeTime(row.updatedAt) }}</span>
          </template>
        </DataTable>

        <Pager
          v-if="data && data.totalPages > 1"
          :page="data.page"
          :size="data.size"
          :total-elements="data.totalElements"
          :total-pages="data.totalPages"
          noun="accounts"
          @update:page="setPage"
        />
      </PageState>
    </div>
  </AppShell>
</template>
