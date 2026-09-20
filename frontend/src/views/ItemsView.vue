<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import DataTable, { type Column } from '@/components/data/DataTable.vue'
import Pager from '@/components/data/Pager.vue'
import SearchInput from '@/components/data/SearchInput.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import ItemFormModal from '@/components/items/ItemFormModal.vue'
import { useListQuery } from '@/composables/useListQuery'
import AppShell from '@/layouts/AppShell.vue'
import {
  archiveItem,
  createItem,
  deleteItem,
  itemKeys,
  listItems,
  restoreItem,
  updateItem,
  type ItemRequestBody,
} from '@/lib/api/items'
import { listTaxRates, taxRateKeys } from '@/lib/api/taxRates'
import { confirmDialog } from '@/lib/dialogs'
import { formatMoney } from '@/lib/format'
import { ApiError } from '@/lib/http'
import type { Item } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const COLUMNS: Column[] = [
  { key: 'name', label: 'Name', sortBy: 'name' },
  { key: 'sku', label: 'SKU', sortBy: 'sku', class: 'mono' },
  { key: 'defaultUnitPrice', label: 'Default price', align: 'right' },
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
  queryKey: computed(() => itemKeys.list(listParams.value)),
  queryFn: ({ signal }) => listItems(listParams.value, signal),
})

/** Active tax rates only, for the item form's default-tax picker. */
const { data: taxRates } = useQuery({
  queryKey: taxRateKeys.list({ includeArchived: false, size: 200 }),
  queryFn: ({ signal }) => listTaxRates({ includeArchived: false, size: 200 }, signal),
})

const includeArchivedFilter = computed({
  get: () => state.value.includeArchived === 'true',
  set: (value: boolean) => setFilter('includeArchived', value ? 'true' : 'false'),
})

function invalidate() {
  return queryClient.invalidateQueries({ queryKey: itemKeys.all })
}

const formOpen = ref(false)
const editingItem = ref<Item | null>(null)
const formRef = ref<InstanceType<typeof ItemFormModal> | null>(null)

function openCreate() {
  editingItem.value = null
  formOpen.value = true
}
function openEdit(item: Item) {
  editingItem.value = item
  formOpen.value = true
}

const saveMutation = useMutation({
  mutationFn: (body: ItemRequestBody) =>
    editingItem.value ? updateItem(editingItem.value.id, body) : createItem(body),
  onSuccess: async () => {
    await invalidate()
    toasts.success(editingItem.value ? 'Item updated' : 'Item created')
    formOpen.value = false
  },
  onError: (err) => formRef.value?.showServerError(err),
})

const archiveMutation = useMutation({
  mutationFn: (id: number) => archiveItem(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Item archived')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not archive that item.'),
})

const restoreMutation = useMutation({
  mutationFn: (id: number) => restoreItem(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Item restored')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not restore that item.'),
})

const deleteMutation = useMutation({
  mutationFn: (id: number) => deleteItem(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Item deleted')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not delete that item.'),
})

function onRemove(item: Item) {
  if (!confirmDialog(`Delete ${item.name}? This only works if it has never been used.`)) return
  deleteMutation.mutate(item.id)
}
</script>

<template>
  <AppShell>
    <template #title>Items</template>
    <template #sub>
      <span v-if="data">{{ data.totalElements }} items</span>
    </template>
    <template #actions>
      <button v-if="auth.isAdmin" type="button" class="btn btn-primary" @click="openCreate()">
        + New item
      </button>
    </template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Search name or SKU…" />
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
        error-text="Unable to load items."
        :skeleton-rows="6"
      >
        <template #empty>
          <EmptyState title="No items yet." hint="Create one to reuse it on invoices and bills." />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: Item) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:name="{ row }">
            <a href="#" @click.prevent="openEdit(row)">{{ row.name }}</a>
          </template>
          <template #cell:sku="{ row }">{{ row.sku || '—' }}</template>
          <template #cell:defaultUnitPrice="{ row }">
            {{ row.defaultUnitPrice != null ? formatMoney(row.defaultUnitPrice) : '—' }}
          </template>
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
          noun="items"
          @update:page="setPage"
        />
      </PageState>
    </div>

    <ItemFormModal
      v-if="formOpen"
      ref="formRef"
      :item="editingItem"
      :tax-rates="taxRates?.content ?? []"
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
