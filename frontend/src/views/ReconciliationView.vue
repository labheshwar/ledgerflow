<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import { ApiError, apiFetch } from '../lib/api'
import { formatDuration, formatRelativeTime, reconciliationPillClass } from '../lib/format'
import type { Paged, ReconciliationBatchSummary, ReconciliationStatus } from '../lib/types'
import { useAuthStore } from '../stores/auth'

const STATUSES: ReconciliationStatus[] = ['PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED']

const auth = useAuthStore()
const batches = ref<ReconciliationBatchSummary[]>([])
const loading = ref(true)
const errorText = ref('')
const query = ref('')
const filter = ref<ReconciliationStatus | 'ALL'>('ALL')
const triggering = ref(false)

async function load() {
  try {
    batches.value = (await apiFetch<Paged<ReconciliationBatchSummary>>('/reconciliation?size=200')).content
  } catch {
    errorText.value = 'Unable to load reconciliation batches.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function runReconciliation() {
  triggering.value = true
  errorText.value = ''
  try {
    await apiFetch('/reconciliation/trigger', { method: 'POST' })
    await load()
  } catch (e) {
    errorText.value = e instanceof ApiError ? e.message : 'Unable to trigger reconciliation.'
  } finally {
    triggering.value = false
  }
}

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  return batches.value.filter(
    (b) => (filter.value === 'ALL' || b.status === filter.value) && (q === '' || `batch-${b.id}`.includes(q)),
  )
})

function chipClass(status: ReconciliationStatus | 'ALL') {
  return 'chip' + (filter.value === status ? ' on' : '')
}
</script>

<template>
  <AppShell>
    <template #title>Reconciliation</template>
    <template #sub>Async batches compare ledger balances against the external statement feed</template>
    <template #actions>
      <button v-if="auth.isAdmin" class="btn btn-primary" :disabled="triggering" @click="runReconciliation">
        {{ triggering ? 'Starting…' : 'Run reconciliation' }}
      </button>
    </template>

    <p v-if="loading">Loading…</p>
    <p v-else-if="errorText" class="field-error">{{ errorText }}</p>
    <template v-else>
      <div class="toolbar">
        <div class="search">
          <svg viewBox="0 0 16 16">
            <circle cx="7" cy="7" r="5"></circle>
            <path d="M11 11l3.2 3.2"></path>
          </svg>
          <input v-model="query" placeholder="Search batches…" />
        </div>
        <div class="filters">
          <button :class="chipClass('ALL')" @click="filter = 'ALL'">All</button>
          <button v-for="s in STATUSES" :key="s" :class="chipClass(s)" @click="filter = s">
            {{ s.replace('_', ' ') }}
          </button>
        </div>
      </div>

      <div class="tablecard">
        <table>
          <tr>
            <th>Batch</th>
            <th>Status</th>
            <th style="text-align: right">Discrepancies</th>
            <th>Started</th>
            <th>Duration</th>
          </tr>
          <tr v-for="b in filtered" :key="b.id">
            <td class="mono">
              <RouterLink :to="`/reconciliation/${b.id}`">BATCH-{{ b.id }}</RouterLink>
            </td>
            <td>
              <span :class="reconciliationPillClass(b.status)">{{ b.status.replace('_', ' ') }}</span>
            </td>
            <td class="num">{{ b.mismatched }}</td>
            <td class="mono">{{ formatRelativeTime(b.triggeredAt) }}</td>
            <td class="mono">{{ formatDuration(b.triggeredAt, b.completedAt) }}</td>
          </tr>
        </table>
        <div v-if="filtered.length === 0" class="empty">No batches match "{{ query }}".</div>
      </div>
    </template>
  </AppShell>
</template>
