import { apiFetch } from '@/lib/http'
import type { AccountingPeriod } from '@/lib/types'

export interface PeriodRequest {
  startDate: string
  endDate: string
}

export const periodKeys = {
  all: ['periods'] as const,
  list: ['periods', 'list'] as const,
}

/** Not paged: an organization has a handful of periods, not pages of them. */
export function listPeriods(signal?: AbortSignal) {
  return apiFetch<AccountingPeriod[]>('/periods', { signal })
}

export function createPeriod(body: PeriodRequest) {
  return apiFetch<AccountingPeriod>('/periods', { method: 'POST', body: JSON.stringify(body) })
}

export function closePeriod(id: number) {
  return apiFetch<AccountingPeriod>(`/periods/${id}/close`, { method: 'POST' })
}

export function reopenPeriod(id: number) {
  return apiFetch<AccountingPeriod>(`/periods/${id}/reopen`, { method: 'POST' })
}

/** Posts the year-end closing journal, zeroing revenue and expense into Retained Earnings. */
export function closeFiscalYear(asOfDate: string) {
  return apiFetch<{ id: number }>('/periods/close-year', {
    method: 'POST',
    body: JSON.stringify({ asOfDate }),
  })
}
