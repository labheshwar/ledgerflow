import { apiFetch } from '@/lib/http'
import type { DocumentType, MatchSuggestion, Paged, ReconciliationSummary, StatementLine } from '@/lib/types'

export const reconciliationKeys = {
  all: (bankAccountId: string | number) =>
    ['bank-accounts', String(bankAccountId), 'reconciliation'] as const,
  summary: (bankAccountId: string | number) =>
    ['bank-accounts', String(bankAccountId), 'reconciliation', 'summary'] as const,
  lines: (bankAccountId: string | number, matched: boolean | undefined) =>
    ['bank-accounts', String(bankAccountId), 'reconciliation', 'lines', matched ?? null] as const,
  suggestions: (bankAccountId: string | number, lineId: string | number) =>
    [
      'bank-accounts',
      String(bankAccountId),
      'reconciliation',
      'lines',
      String(lineId),
      'suggestions',
    ] as const,
}

export function getReconciliationSummary(bankAccountId: string | number, signal?: AbortSignal) {
  return apiFetch<ReconciliationSummary>(`/bank-accounts/${bankAccountId}/reconciliation/summary`, { signal })
}

export function listReconciliationLines(
  bankAccountId: string | number,
  params: { matched?: boolean; page?: number; size?: number },
  signal?: AbortSignal,
) {
  return apiFetch<Paged<StatementLine>>(`/bank-accounts/${bankAccountId}/reconciliation/lines`, {
    params,
    signal,
  })
}

export function getMatchSuggestions(
  bankAccountId: string | number,
  lineId: string | number,
  signal?: AbortSignal,
) {
  return apiFetch<MatchSuggestion[]>(
    `/bank-accounts/${bankAccountId}/reconciliation/lines/${lineId}/suggestions`,
    {
      signal,
    },
  )
}

export function matchToEntry(bankAccountId: string | number, lineId: string | number, entryId: number) {
  return apiFetch<StatementLine>(`/bank-accounts/${bankAccountId}/reconciliation/lines/${lineId}/match`, {
    method: 'POST',
    body: JSON.stringify({ entryId }),
  })
}

export function settleDocument(
  bankAccountId: string | number,
  lineId: string | number,
  documentType: DocumentType,
  documentId: number,
) {
  return apiFetch<StatementLine>(`/bank-accounts/${bankAccountId}/reconciliation/lines/${lineId}/settle`, {
    method: 'POST',
    body: JSON.stringify({ documentType, documentId }),
  })
}

export function categorizeLine(
  bankAccountId: string | number,
  lineId: string | number,
  accountId: number,
  description?: string,
) {
  return apiFetch<StatementLine>(
    `/bank-accounts/${bankAccountId}/reconciliation/lines/${lineId}/categorize`,
    {
      method: 'POST',
      body: JSON.stringify({ accountId, description: description || null }),
    },
  )
}

export function unmatchLine(bankAccountId: string | number, lineId: string | number) {
  return apiFetch<StatementLine>(`/bank-accounts/${bankAccountId}/reconciliation/lines/${lineId}/unmatch`, {
    method: 'POST',
  })
}
