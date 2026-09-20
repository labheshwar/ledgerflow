<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ref } from 'vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import AppShell from '@/layouts/AppShell.vue'
import {
  closeFiscalYear,
  closePeriod,
  createPeriod,
  listPeriods,
  periodKeys,
  reopenPeriod,
} from '@/lib/api/periods'
import { formatDate } from '@/lib/format'
import { ApiError } from '@/lib/http'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()

const { data, isPending, error } = useQuery({
  queryKey: periodKeys.list,
  queryFn: ({ signal }) => listPeriods(signal),
})

function invalidate() {
  return queryClient.invalidateQueries({ queryKey: periodKeys.all })
}

// --- create a period ---
const newStart = ref('')
const newEnd = ref('')
const createError = ref('')

const create = useMutation({
  mutationFn: () => createPeriod({ startDate: newStart.value, endDate: newEnd.value }),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Period created')
    newStart.value = ''
    newEnd.value = ''
    createError.value = ''
  },
  onError: (e) => {
    createError.value = e instanceof ApiError ? e.message : 'Could not create that period.'
  },
})

// --- close / reopen ---
const close = useMutation({
  mutationFn: (id: number) => closePeriod(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Period closed — new postings dated inside it will be refused')
  },
  onError: (e) => toasts.error(e instanceof ApiError ? e.message : 'Could not close that period.'),
})

const reopen = useMutation({
  mutationFn: (id: number) => reopenPeriod(id),
  onSuccess: async () => {
    await invalidate()
    toasts.success('Period reopened')
  },
  onError: (e) => toasts.error(e instanceof ApiError ? e.message : 'Could not reopen that period.'),
})

// --- year-end close ---
const asOfDate = ref('')
const closeYearError = ref('')

const closeYear = useMutation({
  mutationFn: () => closeFiscalYear(asOfDate.value),
  onSuccess: async (transaction) => {
    await invalidate()
    toasts.success(`Year-end close posted as TXN-${transaction.id}`)
    closeYearError.value = ''
  },
  onError: (e) => {
    closeYearError.value = e instanceof ApiError ? e.message : 'Could not close the fiscal year.'
  },
})
</script>

<template>
  <AppShell>
    <template #title>Accounting periods</template>
    <template #sub>Closing a period refuses any new posting dated inside it</template>

    <div v-if="auth.isAdmin" class="grid">
      <div class="card">
        <h2>New period</h2>
        <div class="row">
          <div class="field">
            <label>Start date</label>
            <input v-model="newStart" type="date" class="input" />
          </div>
          <div class="field">
            <label>End date</label>
            <input v-model="newEnd" type="date" class="input" />
          </div>
        </div>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="!newStart || !newEnd || create.isPending.value"
          @click="create.mutate()"
        >
          {{ create.isPending.value ? 'Creating…' : 'Create period' }}
        </button>
        <div v-if="createError" class="field-error" style="margin-top: 10px">{{ createError }}</div>
      </div>

      <div class="card">
        <h2>Year-end close</h2>
        <p class="hint">
          Zeroes every revenue and expense account as of the date below and moves the net result into Retained
          Earnings.
        </p>
        <div class="field">
          <label>As of date</label>
          <input v-model="asOfDate" type="date" class="input" />
        </div>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="!asOfDate || closeYear.isPending.value"
          @click="closeYear.mutate()"
        >
          {{ closeYear.isPending.value ? 'Closing…' : 'Post closing journal' }}
        </button>
        <div v-if="closeYearError" class="field-error" style="margin-top: 10px">{{ closeYearError }}</div>
      </div>
    </div>

    <div class="sectionhead"><h2>Periods</h2></div>
    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.length === 0"
        error-text="Unable to load periods."
      >
        <template #empty>
          <EmptyState
            title="No periods defined yet."
            hint="Without one, postings are never locked out by date."
          />
        </template>

        <table v-if="data">
          <tr>
            <th>Start</th>
            <th>End</th>
            <th>Status</th>
            <th>Closed</th>
            <th v-if="auth.isAdmin"></th>
          </tr>
          <tr v-for="p in data" :key="p.id">
            <td class="mono">{{ formatDate(p.startDate) }}</td>
            <td class="mono">{{ formatDate(p.endDate) }}</td>
            <td>
              <span :class="p.status === 'CLOSED' ? 'pill pill-amber' : 'pill pill-green'">{{
                p.status
              }}</span>
            </td>
            <td class="mono" style="color: var(--ink-soft)">
              {{ p.closedAt ? formatDate(p.closedAt) : '—' }}
            </td>
            <td v-if="auth.isAdmin" style="text-align: right">
              <button
                v-if="p.status === 'OPEN'"
                class="linkbtn"
                type="button"
                :disabled="close.isPending.value"
                @click="close.mutate(p.id)"
              >
                Close
              </button>
              <button
                v-else
                class="linkbtn"
                type="button"
                :disabled="reopen.isPending.value"
                @click="reopen.mutate(p.id)"
              >
                Reopen
              </button>
            </td>
          </tr>
        </table>
      </PageState>
    </div>
  </AppShell>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin: 16px 0 28px;
}
.card {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 20px;
}
.card h2 {
  font-family: 'Source Serif 4', serif;
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 14px;
}
.card .hint {
  font-size: 12px;
  color: var(--ink-soft);
  margin: 0 0 14px;
  line-height: 1.5;
}
.row {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}
.row .field {
  flex: 1;
}
table {
  width: 100%;
  border-collapse: collapse;
}
th {
  text-align: left;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--ink-soft);
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
  vertical-align: middle;
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
