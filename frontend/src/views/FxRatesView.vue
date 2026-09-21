<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import DataTable, { type Column } from '@/components/data/DataTable.vue'
import Pager from '@/components/data/Pager.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PageState from '@/components/feedback/PageState.vue'
import { useListQuery } from '@/composables/useListQuery'
import AppShell from '@/layouts/AppShell.vue'
import { fxRateKeys, listFxRates, recordFxRate, type FxRateRequestBody } from '@/lib/api/fxRates'
import { formatDate } from '@/lib/format'
import { ApiError } from '@/lib/http'
import type { FxRate } from '@/lib/types'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'

const COLUMNS: Column[] = [
  { key: 'currency', label: 'Currency', sortBy: 'currency' },
  { key: 'rate', label: 'Rate', sortBy: 'rate', align: 'right' },
  { key: 'asOfDate', label: 'As of', sortBy: 'asOfDate' },
]

const auth = useAuthStore()
const toasts = useToastStore()
const queryClient = useQueryClient()

const { state, params, setPage, setSort } = useListQuery({ defaultSort: 'currency,asc' })

const { data, isPending, error } = useQuery({
  queryKey: computed(() => fxRateKeys.list(params.value)),
  queryFn: ({ signal }) => listFxRates(params.value, signal),
})

const currency = ref('')
const rate = ref('')
const asOfDate = ref(new Date().toISOString().slice(0, 10))
const formError = ref('')

const recordMutation = useMutation({
  mutationFn: (body: FxRateRequestBody) => recordFxRate(body),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: fxRateKeys.all })
    toasts.success(`Rate recorded for ${currency.value.trim().toUpperCase()}`)
    rate.value = ''
    formError.value = ''
  },
  onError: (err) => {
    formError.value = err instanceof ApiError ? err.message : 'Unable to record this rate.'
  },
})

function submit() {
  const parsedRate = parseFloat(rate.value)
  if (!currency.value.trim() || !(parsedRate > 0) || !asOfDate.value) {
    formError.value = 'A currency, a positive rate and a date are all required.'
    return
  }
  recordMutation.mutate({
    currency: currency.value.trim().toUpperCase(),
    rate: parsedRate,
    asOfDate: asOfDate.value,
  })
}
</script>

<template>
  <AppShell>
    <template #title>Exchange rates</template>
    <template #sub>
      A posting in a foreign currency uses the latest rate on or before its own date -- recording today's rate
      again corrects it rather than adding a duplicate.
    </template>

    <div v-if="auth.isAdmin" class="card formcard">
      <div class="fieldrow">
        <div class="field" style="flex: 0 0 100px">
          <label>Currency</label>
          <input v-model="currency" class="input" placeholder="EUR" maxlength="3" />
        </div>
        <div class="field" style="flex: 0 0 140px">
          <label>Rate (per 1 unit)</label>
          <input v-model="rate" class="input amt" placeholder="1.10" />
        </div>
        <div class="field" style="flex: 0 0 150px">
          <label>As of</label>
          <input v-model="asOfDate" type="date" class="input" />
        </div>
        <div class="field" style="flex: 0 0 auto; align-self: flex-end">
          <button
            type="button"
            class="btn btn-primary"
            :disabled="recordMutation.isPending.value"
            @click="submit"
          >
            {{ recordMutation.isPending.value ? 'Recording…' : 'Record rate' }}
          </button>
        </div>
      </div>
      <div v-if="formError" class="field-error">{{ formError }}</div>
    </div>

    <div class="tablecard">
      <PageState
        :loading="isPending"
        :error="error"
        :empty="data?.content.length === 0"
        error-text="Unable to load exchange rates."
        :skeleton-rows="5"
      >
        <template #empty>
          <EmptyState
            title="No exchange rates recorded yet."
            hint="Record one above before invoicing or settling in a foreign currency."
          />
        </template>

        <DataTable
          :columns="COLUMNS"
          :rows="data?.content ?? []"
          :row-key="(row: FxRate) => row.id"
          :sort="String(state.sort)"
          @update:sort="setSort"
        >
          <template #cell:rate="{ row }">{{ row.rate }}</template>
          <template #cell:asOfDate="{ row }">{{ formatDate(row.asOfDate) }}</template>
        </DataTable>

        <Pager
          v-if="data && data.totalPages > 1"
          :page="data.page"
          :size="data.size"
          :total-elements="data.totalElements"
          :total-pages="data.totalPages"
          noun="rates"
          @update:page="setPage"
        />
      </PageState>
    </div>
  </AppShell>
</template>

<style scoped>
.formcard {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--raised);
  padding: 16px 20px;
  margin-bottom: 16px;
}
.fieldrow {
  display: flex;
  gap: 12px;
}
.amt {
  font-family: 'IBM Plex Mono', monospace;
}
</style>
