<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import { flattenPostableAccounts } from '@/lib/accounts'
import { getAccountTree } from '@/lib/api/accounts'
import { bankAccountKeys, getBankAccount } from '@/lib/api/bankAccounts'
import {
  categorizeLine,
  getMatchSuggestions,
  getReconciliationSummary,
  listReconciliationLines,
  matchToEntry,
  reconciliationKeys,
  settleDocument,
  unmatchLine,
} from '@/lib/api/reconciliation'
import { formatDate, formatMoney } from '@/lib/format'
import { ApiError } from '@/lib/http'
import type { MatchSuggestion } from '@/lib/types'
import { useToastStore } from '@/stores/toast'

const route = useRoute()
const toasts = useToastStore()
const queryClient = useQueryClient()
const bankAccountId = computed(() => route.params.id as string)

const tab = ref<'unmatched' | 'matched'>('unmatched')
const selectedLineId = ref<number | null>(null)
const categorizeAccountId = ref('')
const categorizeDescription = ref('')

const { data: bankAccount } = useQuery({
  queryKey: computed(() => bankAccountKeys.detail(bankAccountId.value)),
  queryFn: ({ signal }) => getBankAccount(bankAccountId.value, signal),
})

const { data: summary } = useQuery({
  queryKey: computed(() => reconciliationKeys.summary(bankAccountId.value)),
  queryFn: ({ signal }) => getReconciliationSummary(bankAccountId.value, signal),
})

const {
  data: lines,
  isPending: linesPending,
  error: linesError,
} = useQuery({
  queryKey: computed(() => reconciliationKeys.lines(bankAccountId.value, tab.value === 'matched')),
  queryFn: ({ signal }) =>
    listReconciliationLines(bankAccountId.value, { matched: tab.value === 'matched', size: 30 }, signal),
})

// The two-pane workspace always has something selected once there is
// anything to select -- picking the first unmatched line automatically is
// what makes working through a whole statement feel like a queue rather
// than a list the user has to click into one row at a time.
watch(lines, (value) => {
  const stillThere = value?.content.some((line) => line.id === selectedLineId.value)
  if (!stillThere) {
    selectedLineId.value = value?.content[0]?.id ?? null
  }
})

watch(tab, () => {
  selectedLineId.value = null
})

const selectedLine = computed(
  () => lines.value?.content.find((line) => line.id === selectedLineId.value) ?? null,
)

const { data: suggestions } = useQuery({
  queryKey: computed(() => reconciliationKeys.suggestions(bankAccountId.value, selectedLineId.value ?? 0)),
  queryFn: ({ signal }) => getMatchSuggestions(bankAccountId.value, selectedLineId.value as number, signal),
  enabled: computed(() => tab.value === 'unmatched' && selectedLineId.value != null),
})

const { data: accountTree } = useQuery({
  queryKey: ['accounts', 'tree', false],
  queryFn: ({ signal }) => getAccountTree(false, signal),
})
const categoryAccounts = computed(() =>
  flattenPostableAccounts(accountTree.value ?? []).filter(
    (account) => account.id !== bankAccount.value?.accountId,
  ),
)

async function afterAction(message: string) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: reconciliationKeys.all(bankAccountId.value) }),
    // The bank account's own committed statement (a different page, a
    // different query key) shows a matched/unmatched pill per line too --
    // stale otherwise the next time it's opened, the same reason
    // StatementImportView invalidates this same key on commit.
    queryClient.invalidateQueries({ queryKey: bankAccountKeys.lines(bankAccountId.value) }),
  ])
  categorizeAccountId.value = ''
  categorizeDescription.value = ''
  toasts.success(message)
}

function onActionError(err: unknown, fallback: string) {
  toasts.error(err instanceof ApiError ? err.message : fallback)
}

const matchMutation = useMutation({
  mutationFn: (suggestion: MatchSuggestion) =>
    matchToEntry(bankAccountId.value, selectedLineId.value as number, suggestion.id),
  onSuccess: () => afterAction('Line matched'),
  onError: (err) => onActionError(err, 'Unable to match this line.'),
})

const settleMutation = useMutation({
  mutationFn: (suggestion: MatchSuggestion) =>
    settleDocument(
      bankAccountId.value,
      selectedLineId.value as number,
      suggestion.kind === 'INVOICE' ? 'INVOICE' : 'BILL',
      suggestion.id,
    ),
  onSuccess: () => afterAction('Payment recorded and line matched'),
  onError: (err) => onActionError(err, 'Unable to settle against that document.'),
})

const categorizeMutation = useMutation({
  mutationFn: () =>
    categorizeLine(
      bankAccountId.value,
      selectedLineId.value as number,
      Number(categorizeAccountId.value),
      categorizeDescription.value,
    ),
  onSuccess: () => afterAction('Line categorized and posted'),
  onError: (err) => onActionError(err, 'Unable to categorize this line.'),
})

const unmatchMutation = useMutation({
  mutationFn: (lineId: number) => unmatchLine(bankAccountId.value, lineId),
  onSuccess: () => afterAction('Match undone'),
  onError: (err) => onActionError(err, 'Unable to undo this match.'),
})

function actOn(suggestion: MatchSuggestion) {
  if (suggestion.kind === 'ENTRY') {
    matchMutation.mutate(suggestion)
  } else {
    settleMutation.mutate(suggestion)
  }
}

const anyActionPending = computed(
  () => matchMutation.isPending.value || settleMutation.isPending.value || categorizeMutation.isPending.value,
)

function suggestionKindLabel(kind: MatchSuggestion['kind']): string {
  if (kind === 'ENTRY') return 'Ledger entry'
  if (kind === 'INVOICE') return 'Invoice'
  return 'Bill'
}

function suggestionActionLabel(kind: MatchSuggestion['kind']): string {
  return kind === 'ENTRY' ? 'Match' : 'Settle'
}
</script>

<template>
  <AppShell>
    <template #title>Reconcile{{ bankAccount ? ` — ${bankAccount.name}` : '' }}</template>
    <template #sub
      ><RouterLink :to="`/bank-accounts/${bankAccountId}`">← Back to bank account</RouterLink></template
    >

    <div v-if="summary" class="stat-row">
      <div class="stat">
        <div class="stat-value">{{ summary.totalLines }}</div>
        <div class="stat-label">Total lines</div>
      </div>
      <div class="stat">
        <div class="stat-value">{{ summary.matchedLines }}</div>
        <div class="stat-label">Matched</div>
      </div>
      <div class="stat">
        <div class="stat-value">{{ summary.unmatchedLines }}</div>
        <div class="stat-label">Unmatched</div>
      </div>
    </div>

    <div class="tabs">
      <button type="button" :class="{ active: tab === 'unmatched' }" @click="tab = 'unmatched'">
        Unmatched
      </button>
      <button type="button" :class="{ active: tab === 'matched' }" @click="tab = 'matched'">Matched</button>
    </div>

    <div class="workspace">
      <div class="pane lines-pane">
        <PageState
          :loading="linesPending"
          :error="linesError"
          :empty="lines?.content.length === 0"
          error-text="Unable to load statement lines."
        >
          <template #empty>
            <p class="notes">
              {{ tab === 'unmatched' ? 'Nothing left to reconcile.' : 'Nothing matched yet.' }}
            </p>
          </template>

          <div
            v-for="line in lines?.content ?? []"
            :key="line.id"
            class="line-row"
            :class="{ selected: line.id === selectedLineId }"
            @click="selectedLineId = line.id"
          >
            <div class="meta">{{ formatDate(line.txnDate) }}</div>
            <div class="desc">{{ line.description }}</div>
            <div class="amt" :class="{ negative: line.amount < 0 }">{{ formatMoney(line.amount) }}</div>
            <button
              v-if="tab === 'matched'"
              type="button"
              class="btn"
              :disabled="unmatchMutation.isPending.value"
              @click.stop="unmatchMutation.mutate(line.id)"
            >
              Undo
            </button>
          </div>
        </PageState>
      </div>

      <div class="pane action-pane">
        <template v-if="tab === 'matched'">
          <p class="notes">Select the Unmatched tab to work through the rest of this statement.</p>
        </template>
        <template v-else-if="!selectedLine">
          <p class="notes">Select a line on the left to see what it could match.</p>
        </template>
        <template v-else>
          <h2>{{ selectedLine.description }}</h2>
          <p class="hint">{{ formatDate(selectedLine.txnDate) }} · {{ formatMoney(selectedLine.amount) }}</p>

          <div v-if="suggestions && suggestions.length > 0" class="suggestions">
            <div v-for="s in suggestions" :key="`${s.kind}-${s.id}`" class="suggestion-card">
              <div class="suggestion-main">
                <span class="kind-badge">{{ suggestionKindLabel(s.kind) }}</span>
                <div class="suggestion-label">{{ s.label }}</div>
                <div class="meta">
                  {{ formatDate(s.date) }} · {{ formatMoney(s.amount) }} · {{ Math.round(s.score * 100) }}%
                  match
                </div>
              </div>
              <button type="button" class="btn btn-primary" :disabled="anyActionPending" @click="actOn(s)">
                {{ suggestionActionLabel(s.kind) }}
              </button>
            </div>
          </div>
          <p v-else class="notes">No obvious candidates. Categorize it below instead.</p>

          <div class="categorize card">
            <h3>Categorize instead</h3>
            <p class="hint">Posts a new journal entry directly between this account and the one you pick.</p>
            <div class="field">
              <label>Account</label>
              <select v-model="categorizeAccountId" class="input">
                <option value="">— Select —</option>
                <option v-for="a in categoryAccounts" :key="a.id" :value="a.id">
                  {{ a.code }} · {{ a.name }}
                </option>
              </select>
            </div>
            <div class="field">
              <label>Description (optional)</label>
              <input
                v-model="categorizeDescription"
                type="text"
                class="input"
                :placeholder="selectedLine.description"
              />
            </div>
            <button
              type="button"
              class="btn"
              :disabled="!categorizeAccountId || anyActionPending"
              @click="categorizeMutation.mutate()"
            >
              {{ categorizeMutation.isPending.value ? 'Posting…' : 'Categorize & post' }}
            </button>
          </div>
        </template>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.stat-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  max-width: 500px;
}
.stat {
  flex: 1;
  text-align: center;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
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
.tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}
.tabs button {
  border: 1px solid var(--line);
  background: var(--raised);
  border-radius: 6px;
  padding: 6px 14px;
  font-size: 12.5px;
  cursor: pointer;
}
.tabs button.active {
  background: var(--ink);
  color: var(--paper);
  border-color: var(--ink);
}
.workspace {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  align-items: start;
}
.pane {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 16px;
  min-height: 200px;
}
.line-row {
  display: grid;
  grid-template-columns: 62px 1fr 68px 52px;
  gap: 6px;
  align-items: center;
  padding: 8px 6px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
  cursor: pointer;
  border-radius: 4px;
}
.line-row:hover {
  background: var(--line-soft);
}
.line-row.selected {
  background: var(--line-soft);
  outline: 1px solid var(--ink-soft);
}
.line-row .btn {
  padding: 3px 8px;
  font-size: 11px;
}
.meta {
  color: var(--ink-soft);
  font-size: 11px;
  font-family: 'IBM Plex Mono', monospace;
}
.desc {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.amt {
  font-family: 'IBM Plex Mono', monospace;
  text-align: right;
}
.amt.negative {
  color: var(--red);
}
.action-pane h2 {
  font-family: 'Source Serif 4', serif;
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px;
}
.hint {
  color: var(--ink-soft);
  font-size: 12.5px;
  margin: 0 0 14px;
}
.suggestions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
}
.suggestion-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 10px 12px;
}
.kind-badge {
  display: inline-block;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--ink-soft);
  border: 1px solid var(--line);
  border-radius: 4px;
  padding: 1px 6px;
  margin-bottom: 4px;
}
.suggestion-label {
  font-size: 13px;
  font-weight: 500;
}
.categorize {
  border-top: 1px solid var(--line);
  padding-top: 16px;
  margin-top: 4px;
}
.categorize h3 {
  font-size: 13.5px;
  font-weight: 600;
  margin: 0 0 4px;
}
.field {
  margin-bottom: 10px;
}
.field label {
  display: block;
  font-size: 11.5px;
  color: var(--ink-soft);
  margin-bottom: 3px;
}
.notes {
  color: var(--ink-soft);
  font-size: 12.5px;
}
</style>
