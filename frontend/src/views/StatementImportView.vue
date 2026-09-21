<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import {
  bankAccountKeys,
  commitImport,
  getImport,
  getImportHeaders,
  listPreviewLines,
  requestPreview,
  type ColumnMappingRequestBody,
} from '@/lib/api/bankAccounts'
import { formatDate, formatMoney } from '@/lib/format'
import { ApiError } from '@/lib/http'
import { useToastStore } from '@/stores/toast'

const route = useRoute()
const toasts = useToastStore()
const queryClient = useQueryClient()
const bankAccountId = route.params.id as string
const importId = route.params.importId as string

const showMappingForm = ref(false)

const {
  data: statementImport,
  isPending,
  error,
} = useQuery({
  queryKey: computed(() => bankAccountKeys.importDetail(bankAccountId, importId)),
  queryFn: ({ signal }) => getImport(bankAccountId, importId, signal),
  refetchInterval: (query) => (query.state.data?.status === 'PROCESSING' ? 800 : false),
})

const needsMapping = computed(
  () =>
    showMappingForm.value ||
    statementImport.value?.status === 'UPLOADED' ||
    statementImport.value?.status === 'FAILED',
)

const { data: headers } = useQuery({
  queryKey: computed(() => [...bankAccountKeys.importDetail(bankAccountId, importId), 'headers']),
  queryFn: ({ signal }) => getImportHeaders(bankAccountId, importId, signal),
  enabled: needsMapping,
})

const mapping = reactive({
  dateColumn: '',
  descriptionColumn: '',
  amountColumn: '',
  externalIdColumn: '',
})

const mappingError = ref('')
const previewMutation = useMutation({
  mutationFn: () =>
    requestPreview(bankAccountId, importId, {
      dateColumn: mapping.dateColumn,
      descriptionColumn: mapping.descriptionColumn,
      amountColumn: mapping.amountColumn,
      externalIdColumn: mapping.externalIdColumn || null,
    } satisfies ColumnMappingRequestBody),
  onSuccess: async () => {
    showMappingForm.value = false
    await queryClient.invalidateQueries({ queryKey: bankAccountKeys.importDetail(bankAccountId, importId) })
  },
  onError: (err) => {
    mappingError.value = err instanceof ApiError ? err.message : 'Unable to start the preview.'
  },
})

const { data: previewLines } = useQuery({
  queryKey: computed(() => bankAccountKeys.importLines(bankAccountId, importId)),
  queryFn: ({ signal }) => listPreviewLines(bankAccountId, importId, { size: 50 }, signal),
  enabled: computed(() => statementImport.value?.status === 'PREVIEWED'),
})

const commitMutation = useMutation({
  mutationFn: () => commitImport(bankAccountId, importId),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: bankAccountKeys.importDetail(bankAccountId, importId) })
    await queryClient.invalidateQueries({ queryKey: bankAccountKeys.lines(bankAccountId) })
    await queryClient.invalidateQueries({ queryKey: bankAccountKeys.imports(bankAccountId) })
    toasts.success('Statement committed')
  },
  onError: (err) => toasts.error(err instanceof ApiError ? err.message : 'Unable to commit this import.'),
})
</script>

<template>
  <AppShell>
    <template #title>{{ statementImport?.originalFilename ?? 'Import statement' }}</template>
    <template #sub
      ><RouterLink :to="`/bank-accounts/${bankAccountId}`">← Back to bank account</RouterLink></template
    >

    <PageState :loading="isPending" :error="error" error-text="Unable to load this import.">
      <template v-if="statementImport">
        <div v-if="statementImport.status === 'FAILED' && !needsMapping" class="card error-card">
          <h2>Step 2 of 4 — Mapping failed</h2>
          <p>{{ statementImport.errorMessage || 'Something went wrong while processing this file.' }}</p>
          <button type="button" class="btn btn-primary" @click="showMappingForm = true">
            Try a different mapping
          </button>
        </div>

        <div v-if="needsMapping" class="card">
          <h2>Step 2 of 4 — Map columns</h2>
          <p class="hint">Tell LedgerFlow which of the file's own columns holds what.</p>
          <div class="mapping-grid">
            <div class="field">
              <label>Date</label>
              <select v-model="mapping.dateColumn" class="input">
                <option value="">— Select —</option>
                <option v-for="h in headers ?? []" :key="h" :value="h">{{ h }}</option>
              </select>
            </div>
            <div class="field">
              <label>Description</label>
              <select v-model="mapping.descriptionColumn" class="input">
                <option value="">— Select —</option>
                <option v-for="h in headers ?? []" :key="h" :value="h">{{ h }}</option>
              </select>
            </div>
            <div class="field">
              <label>Amount</label>
              <select v-model="mapping.amountColumn" class="input">
                <option value="">— Select —</option>
                <option v-for="h in headers ?? []" :key="h" :value="h">{{ h }}</option>
              </select>
            </div>
            <div class="field">
              <label>External ID (optional)</label>
              <select v-model="mapping.externalIdColumn" class="input">
                <option value="">— None, compute one —</option>
                <option v-for="h in headers ?? []" :key="h" :value="h">{{ h }}</option>
              </select>
            </div>
          </div>
          <div v-if="mappingError" class="field-error" style="margin-top: 12px">{{ mappingError }}</div>
          <button
            type="button"
            class="btn btn-primary"
            style="margin-top: 16px"
            :disabled="
              !mapping.dateColumn ||
              !mapping.descriptionColumn ||
              !mapping.amountColumn ||
              previewMutation.isPending.value
            "
            @click="previewMutation.mutate()"
          >
            {{ previewMutation.isPending.value ? 'Starting…' : 'Preview' }}
          </button>
        </div>

        <div v-else-if="statementImport.status === 'PROCESSING'" class="card">
          <h2>Step 3 of 4 — Processing</h2>
          <div class="progress-track">
            <div
              class="progress-fill"
              :style="{
                width: `${statementImport.totalRows > 0 ? (statementImport.processedRows / statementImport.totalRows) * 100 : 0}%`,
              }"
            />
          </div>
          <p class="hint">
            {{ statementImport.processedRows }} / {{ statementImport.totalRows || '…' }} rows checked
          </p>
        </div>

        <div v-else-if="statementImport.status === 'PREVIEWED'" class="card">
          <h2>Step 3 of 4 — Preview</h2>
          <div class="stat-row">
            <div class="stat">
              <div class="stat-value">{{ statementImport.totalRows }}</div>
              <div class="stat-label">Total rows</div>
            </div>
            <div class="stat">
              <div class="stat-value">{{ statementImport.newRows }}</div>
              <div class="stat-label">New</div>
            </div>
            <div class="stat">
              <div class="stat-value">{{ statementImport.duplicateRows }}</div>
              <div class="stat-label">Duplicates</div>
            </div>
            <div class="stat">
              <div class="stat-value">{{ statementImport.errorRows }}</div>
              <div class="stat-label">Errors</div>
            </div>
          </div>

          <div v-if="previewLines && previewLines.content.length > 0" class="row-head">
            <div>Date</div>
            <div>Description</div>
            <div style="text-align: right">Amount</div>
          </div>
          <div v-for="line in previewLines?.content ?? []" :key="line.id" class="line-row">
            <div class="meta">{{ formatDate(line.txnDate) }}</div>
            <div>{{ line.description }}</div>
            <div class="amt" :class="{ negative: line.amount < 0 }">{{ formatMoney(line.amount) }}</div>
          </div>

          <div style="display: flex; gap: 8px; margin-top: 16px">
            <button
              type="button"
              class="btn btn-primary"
              :disabled="statementImport.newRows === 0 || commitMutation.isPending.value"
              @click="commitMutation.mutate()"
            >
              {{ commitMutation.isPending.value ? 'Committing…' : `Commit ${statementImport.newRows} rows` }}
            </button>
            <button type="button" class="btn" @click="showMappingForm = true">Change mapping</button>
          </div>
        </div>

        <div v-else-if="statementImport.status === 'COMMITTED'" class="card">
          <h2>Step 4 of 4 — Committed</h2>
          <p>
            {{ statementImport.newRows }} rows are now part of this bank account's statement.
            {{ statementImport.duplicateRows }} duplicate rows were skipped.
          </p>
          <RouterLink class="btn btn-primary" :to="`/bank-accounts/${bankAccountId}`">
            Back to bank account
          </RouterLink>
        </div>
      </template>
    </PageState>
  </AppShell>
</template>

<style scoped>
.card {
  max-width: 700px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 24px;
}
.error-card {
  border-color: var(--red-line);
  margin-bottom: 16px;
}
.card h2 {
  font-family: 'Source Serif 4', serif;
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px;
}
.hint {
  color: var(--ink-soft);
  font-size: 12.5px;
  margin: 0 0 16px;
}
.mapping-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.progress-track {
  height: 10px;
  border-radius: 5px;
  background: var(--line-soft);
  overflow: hidden;
  margin-bottom: 10px;
}
.progress-fill {
  height: 100%;
  background: var(--green);
  transition: width 0.3s ease;
}
.stat-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}
.stat {
  flex: 1;
  text-align: center;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
}
.stat-value {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 18px;
  font-weight: 600;
}
.stat-label {
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--ink-soft);
  margin-top: 2px;
}
.row-head {
  display: grid;
  grid-template-columns: 100px 1fr 110px;
  gap: 10px;
  padding: 8px 2px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--ink-soft);
  border-top: 1px solid var(--line);
}
.line-row {
  display: grid;
  grid-template-columns: 100px 1fr 110px;
  gap: 10px;
  align-items: center;
  padding: 6px 2px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
}
.meta {
  color: var(--ink-soft);
  font-size: 11.5px;
  font-family: 'IBM Plex Mono', monospace;
}
.amt {
  font-family: 'IBM Plex Mono', monospace;
  text-align: right;
}
.amt.negative {
  color: var(--red);
}
</style>
