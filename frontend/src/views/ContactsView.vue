<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import ContactFormModal from '@/components/contacts/ContactFormModal.vue'
import DataTable, { type Column } from '@/components/data/DataTable.vue'
import Pager from '@/components/data/Pager.vue'
import SearchInput from '@/components/data/SearchInput.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import { useListQuery } from '@/composables/useListQuery'
import AppShell from '@/layouts/AppShell.vue'
import {
  archiveContact,
  contactKeys,
  createContact,
  deleteContact,
  listContacts,
  restoreContact,
  updateContact,
  type ContactRequestBody,
} from '@/lib/api/contacts'
import { confirmDialog } from '@/lib/dialogs'
import { ApiError } from '@/lib/http'
import type { Contact } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const COLUMNS: Column[] = [
  { key: 'name', label: 'Name', sortBy: 'name' },
  { key: 'type', label: 'Type' },
  { key: 'contact', label: 'Contact' },
  { key: 'archived', label: 'Status' },
  { key: 'actions', label: '' },
]

const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()

const { state, params, qInput, setFilter, setPage, setSort } = useListQuery({
  defaultSort: 'name,asc',
  filters: { type: '', includeArchived: 'false' },
})

const listParams = computed(() => ({
  q: params.value.q,
  type: (params.value.type as Contact['type'] | '') || null,
  includeArchived: params.value.includeArchived === 'true',
  page: params.value.page,
  size: params.value.size,
  sort: params.value.sort,
}))

const { data, isPending, error } = useQuery({
  queryKey: computed(() => contactKeys.list(listParams.value)),
  queryFn: ({ signal }) => listContacts(listParams.value, signal),
})

const typeFilter = computed({
  get: () => String(state.value.type ?? ''),
  set: (value: string) => setFilter('type', value),
})
const includeArchivedFilter = computed({
  get: () => state.value.includeArchived === 'true',
  set: (value: boolean) => setFilter('includeArchived', value ? 'true' : 'false'),
})

function invalidate() {
  return queryClient.invalidateQueries({ queryKey: contactKeys.all })
}

// --- create / edit modal ---
const formOpen = ref(false)
const editingContact = ref<Contact | null>(null)
const formRef = ref<InstanceType<typeof ContactFormModal> | null>(null)

function openCreate() {
  editingContact.value = null
  formOpen.value = true
}
function openEdit(contact: Contact) {
  editingContact.value = contact
  formOpen.value = true
}

const saveMutation = useMutation({
  mutationFn: (body: ContactRequestBody) =>
    editingContact.value ? updateContact(editingContact.value.id, body) : createContact(body),
  onSuccess: async () => {
    await invalidate()
    toasts.success(editingContact.value ? 'Contact updated' : 'Contact created')
    formOpen.value = false
  },
  onError: (err) => formRef.value?.showServerError(err),
})

// --- archive / restore / delete ---
const archiveMutation = useMutation({
  mutationFn: (id: number) => archiveContact(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Contact archived')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not archive that contact.'),
})

const restoreMutation = useMutation({
  mutationFn: (id: number) => restoreContact(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Contact restored')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not restore that contact.'),
})

const deleteMutation = useMutation({
  mutationFn: (id: number) => deleteContact(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Contact deleted')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Could not delete that contact.'),
})

function onRemove(contact: Contact) {
  if (!confirmDialog(`Delete ${contact.name}? This only works if it has never been used.`)) return
  deleteMutation.mutate(contact.id)
}
</script>

<template>
  <AppShell>
    <template #title>Contacts</template>
    <template #sub>
      <span v-if="data">{{ data.totalElements }} contacts</span>
    </template>
    <template #actions>
      <button v-if="auth.isAdmin" type="button" class="btn btn-primary" @click="openCreate()">
        + New contact
      </button>
    </template>

    <div class="toolbar">
      <SearchInput v-model="qInput" placeholder="Search name or email…" />
      <select v-model="typeFilter" class="input" style="width: 160px">
        <option value="">All types</option>
        <option value="CUSTOMER">Customers</option>
        <option value="VENDOR">Vendors</option>
        <option value="BOTH">Both</option>
      </select>
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
        error-text="Unable to load contacts."
        :skeleton-rows="6"
      >
        <template #empty>
          <EmptyState title="No contacts match that search." hint="Try a different term or filter." />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: Contact) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:name="{ row }">
            <a href="#" @click.prevent="openEdit(row)">{{ row.name }}</a>
          </template>
          <template #cell:type="{ row }">{{ row.type }}</template>
          <template #cell:contact="{ row }">
            <span style="color: var(--ink-soft)">{{ row.email || row.phone || '—' }}</span>
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
          noun="contacts"
          @update:page="setPage"
        />
      </PageState>
    </div>

    <ContactFormModal
      v-if="formOpen"
      ref="formRef"
      :contact="editingContact"
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
