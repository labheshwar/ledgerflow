import { apiFetch } from '@/lib/http'
import type { Paged, TransactionDetail, TransactionListItem } from '@/lib/types'

export interface TransactionListParams {
  q?: string
  page?: number
  size?: number
  sort?: string
}

export interface PostTransactionRequest {
  idempotencyKey: string
  description: string | null
  entries: { accountId: number | null; entryType: string; amount: number }[]
}

export const transactionKeys = {
  all: ['transactions'] as const,
  list: (params: TransactionListParams) => ['transactions', 'list', params] as const,
  detail: (id: string | number) => ['transactions', 'detail', String(id)] as const,
}

export function listTransactions(params: TransactionListParams, signal?: AbortSignal) {
  return apiFetch<Paged<TransactionListItem>>('/transactions', { params: { ...params }, signal })
}

export function getTransaction(id: string | number, signal?: AbortSignal) {
  return apiFetch<TransactionDetail>(`/transactions/${id}`, { signal })
}

export function postTransaction(body: PostTransactionRequest) {
  return apiFetch<{ id: number }>('/transactions', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}
