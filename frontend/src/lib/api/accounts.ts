import { apiFetch } from '@/lib/http'
import type { Account, AccountType, LedgerEntry, Paged } from '@/lib/types'

export interface AccountListParams {
  q?: string
  type?: AccountType | null
  page?: number
  size?: number
  sort?: string
}

export const accountKeys = {
  all: ['accounts'] as const,
  list: (params: AccountListParams) => ['accounts', 'list', params] as const,
  detail: (id: string | number) => ['accounts', 'detail', String(id)] as const,
  entries: (id: string | number) => ['accounts', 'entries', String(id)] as const,
}

export function listAccounts(params: AccountListParams, signal?: AbortSignal) {
  return apiFetch<Paged<Account>>('/accounts', { params: { ...params }, signal })
}

export function getAccount(id: string | number, signal?: AbortSignal) {
  return apiFetch<Account>(`/accounts/${id}`, { signal })
}

export function getAccountEntries(id: string | number, signal?: AbortSignal) {
  return apiFetch<LedgerEntry[]>(`/accounts/${id}/entries`, { signal })
}
