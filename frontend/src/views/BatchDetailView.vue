<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import { apiFetch } from '../lib/api'
import {
  formatDateTime,
  formatDuration,
  formatMoney,
  reconciliationPillClass,
  reconciliationResultPillClass,
} from '../lib/format'
import type { ReconciliationBatch } from '../lib/types'

const route = useRoute()
const batchId = computed(() => route.params.id as string)

const batch = ref<ReconciliationBatch | null>(null)
const loading = ref(true)
const errorText = ref('')

onMounted(async () => {
  try {
    batch.value = await apiFetch<ReconciliationBatch>(`/reconciliation/${batchId.value}`)
  } catch {
    errorText.value = 'Unable to load this reconciliation batch.'
  } finally {
    loading.value = false
  }
})

const matchedCount = computed(() => batch.value?.results.filter((r) => r.status === 'MATCHED').length ?? 0)
const mismatchedCount = computed(
  () => batch.value?.results.filter((r) => r.status === 'MISMATCHED').length ?? 0,
)
</script>

<template>
  <AppShell>
    <template #title>
      <template v-if="batch"
        >BATCH-{{ batch.id }}
        <span :class="reconciliationPillClass(batch.status)">{{
          batch.status.replace('_', ' ')
        }}</span></template
      >
      <template v-else>Reconciliation batch</template>
    </template>
    <template #sub><RouterLink to="/reconciliation">← Back to reconciliation</RouterLink></template>

    <p v-if="loading">Loading…</p>
    <p v-else-if="errorText" class="field-error">{{ errorText }}</p>
    <template v-else-if="batch">
      <div class="stat-row">
        <div class="stat-card">
          <div class="label">Accounts compared</div>
          <div class="value mono">{{ batch.results.length }}</div>
        </div>
        <div class="stat-card">
          <div class="label">Matched</div>
          <div class="value mono">{{ matchedCount }}</div>
        </div>
        <div class="stat-card">
          <div class="label">Mismatched</div>
          <div class="value mono">{{ mismatchedCount }}</div>
        </div>
        <div class="stat-card">
          <div class="label">Ran</div>
          <div class="value mono" style="font-size: 15px">
            {{ formatDuration(batch.triggeredAt, batch.completedAt) }}
          </div>
          <div class="delta">{{ formatDateTime(batch.triggeredAt) }}</div>
        </div>
      </div>

      <div class="sectionhead"><h2>Results</h2></div>
      <div class="tablecard">
        <table>
          <tr>
            <th>Account</th>
            <th style="text-align: right">Ledger balance</th>
            <th style="text-align: right">External balance</th>
            <th style="text-align: right">Difference</th>
            <th>Status</th>
          </tr>
          <tr v-for="r in batch.results" :key="r.accountId">
            <td>
              <RouterLink :to="`/accounts/${r.accountId}`">{{ r.accountName }}</RouterLink>
            </td>
            <td class="num">{{ formatMoney(r.ledgerBalance) }}</td>
            <td class="num">{{ formatMoney(r.externalBalance) }}</td>
            <td class="num">{{ formatMoney(r.ledgerBalance - r.externalBalance) }}</td>
            <td>
              <span :class="reconciliationResultPillClass(r.status)">{{ r.status }}</span>
            </td>
          </tr>
        </table>
        <div v-if="batch.results.length === 0" class="empty">
          No results yet — this batch hasn't completed.
        </div>
      </div>
    </template>
  </AppShell>
</template>
