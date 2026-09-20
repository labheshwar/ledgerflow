<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import DataTable, { type Column } from '@/components/data/DataTable.vue'
import Pager from '@/components/data/Pager.vue'
import SearchInput from '@/components/data/SearchInput.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import TaxRateFormModal from '@/components/taxrates/TaxRateFormModal.vue'
import { useListQuery } from '@/composables/useListQuery'
import AppShell from '@/layouts/AppShell.vue'
import {
  archiveTaxRate,
  createTaxRate,
  deleteTaxRate,
  listTaxRates,
  restoreTaxRate,
  taxRateKeys,
  updateTaxRate,
  type TaxRateRequestBody,
} from '@/lib/api/taxRates'
import { confirmDialog } from '@/lib/dialogs'
import { ApiError } from '@/lib/http'
import type { TaxRate } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const COLUMNS: Column[] = [
  { key: 'name', label: 'Name', sortBy: 'name' },
  { key: 'rate', label: 'Rate', sortBy: 'rate', align: 'right' },
  { key: 'archived', label: 'Status' },
  { key: 'actions', label: '' },
]

const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()

const { state, params, qInput, setFilter, setPage, setSort } = useListQuery({
  defaultSort: 'name,asc',
  filters: { includeArchived: 'false' },
})

const listParams = computed(() => ({
  q: params.value.q,
  includeArchived: params.value.includeArchived === 'true',
  page: params.value.page,
  size: params.value.size,
  sort: params.value.sort,
}))

const { data, isPending, error } = useQuery({
  queryKey: computed(() => taxRateKeys.list(listParams.value)),
  queryFn: ({ signal }) => listTaxRates(listParams.value, signal),
})

const includeArchivedFilter = computed({
  get: () => state.value.includeArchived === 'true',
  set: (value: boolean) => setFilter('includeArchived', value ? 'true' : 'false'),
})

function invalidate() {
  return queryClient.invalidateQueries({ queryKey: taxRateKeys.all })
}

const formOpen = ref(false)
const editingTaxRate = ref<TaxRate | null>(null)
const formRef = ref<InstanceType<typeof TaxRateFormModal> | null>(null)

function openCreate() {
  editingTaxRate.value = null
  formOpen.value = true
}
function openEdit(taxRate: TaxRate) {
  editingTaxRate.value = taxRate
  formOpen.value = true
}

const saveMutation = useMutation({
  mutationFn: (body: TaxRateRequestBody) =>
    editingTaxRate.value ? updateTaxRate(editingTaxRate.value.id, body) : createTaxRate(body),
  onSuccess: async () => {
    await invalidate()
    toasts.success(editingTaxRate.value ? 'Tax rate updated' : 'Tax rate created')
    formOpen.value = false
  },
  onError: (err) => formRef.value?.showServerError(err),
})

const archiveMutation = useMutation({
  mutationFn: (id: number) => archiveTaxRate(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Tax rate archived')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not archive that tax rate.'),
})

const restoreMutation = useMutation({
  mutationFn: (id: number) => restoreTaxRate(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Tax rate restored')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not restore that tax rate.'),
})

const deleteMutation = useMutation({
  mutationFn: (id: number) => deleteTaxRate(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Tax rate deleted')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not delete that tax rate.'),
})

function onRemove(taxRate: TaxRate) {
  if (!confirmDialog(`Delete ${taxRate.name}? This only works if it has never been used.`)) return
  deleteMutation.mutate(taxRate.id)
}
</script>

<template>
  <AppShell>
    <template #title>Tax rates</template>
    <template #sub>
      <span v-if="data">{{ data.totalElements }} tax rates</span>
    </template>
    <template #actions>
      <button v-if="auth.isAdmin" type="button" class="btn btn-primary" @click="openCreate()">
        + New tax rate
      </button>
    </template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Search by name…" />
      <label class="checkbox-inline">
        <input v-model="includeArchivedFilter" type="checkbox" />
        Include archived
      </label>
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.content.length === 0"
        error-text="Unable to load tax rates."
        :skeleton-rows="6"
      >
        <template #empty>
          <EmptyState title="No tax rates yet." hint="Create one to start pricing items with tax." />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: TaxRate) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:name="{ row }">
            <a href="#" @click.prevent="openEdit(row)">{{ row.name }}</a>
          </template>
          <template #cell:rate="{ row }">{{ row.rate }}%</template>
          <template #cell:archived="{ row }">
            <span :class="row.archived ? 'pill pill-amber' : 'pill pill-green'">
              {{ row.archived ? 'Archived' : 'Active' }}
            </span>
          </template>
          <template #cell:actions="{ row }">
            <div v-if="auth.isAdmin" style="display: flex; gap: 10px; justify-content: flex-end">
              <button
                v-if="!row.archived"
                class="linkbtn"
                type="button"
                @click="archiveMutation.mutate(row.id)"
              >
                Archive
              </button>
              <button v-else class="linkbtn" type="button" @click="restoreMutation.mutate(row.id)">
                Restore
              </button>
              <button class="linkbtn" type="button" @click="onRemove(row)">Delete</button>
            </div>
          </template>
        </DataTable>

        <Pager
          v-if="data && data.totalPages > 1"
          :page="data.page"
          :size="data.size"
          :total-elements="data.totalElements"
          :total-pages="data.totalPages"
          noun="tax rates"
          @update:page="setPage"
        />
      </PageState>
    </div>

    <TaxRateFormModal
      v-if="formOpen"
      ref="formRef"
      :tax-rate="editingTaxRate"
      :submitting="saveMutation.isPending.value"
      @close="formOpen = false"
      @submit="(body) => saveMutation.mutate(body)"
    />
  </AppShell>
</template>

<style scoped>
.checkbox-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--ink-soft);
}
.linkbtn {
  background: none;
  border: none;
  color: var(--green);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}
.linkbtn:hover {
  text-decoration: underline;
}
</style>
