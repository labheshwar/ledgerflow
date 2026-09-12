import { apiFetch } from '@/lib/http'
import type {
  Paged,
  ReconciliationBatch,
  ReconciliationBatchSummary,
  ReconciliationStatus,
} from '@/lib/types'

export interface ReconciliationListParams {
  status?: ReconciliationStatus | null
  page?: number
  size?: number
  sort?: string
}

export const reconciliationKeys = {
  all: ['reconciliation'] as const,
  list: (params: ReconciliationListParams) => ['reconciliation', 'list', params] as const,
  detail: (id: string | number) => ['reconciliation', 'detail', String(id)] as const,
}

export function listBatches(params: ReconciliationListParams, signal?: AbortSignal) {
  return apiFetch<Paged<ReconciliationBatchSummary>>('/reconciliation', {
    params: { ...params },
    signal,
  })
}

export function getBatch(id: string | number, signal?: AbortSignal) {
  return apiFetch<ReconciliationBatch>(`/reconciliation/${id}`, { signal })
}

export function triggerReconciliation() {
  return apiFetch<ReconciliationBatch>('/reconciliation/trigger', { method: 'POST' })
}
