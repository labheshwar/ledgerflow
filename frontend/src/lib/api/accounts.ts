import { apiFetch } from '@/lib/http'
import type { Account, AccountNode, AccountType, LedgerEntry, Paged, SystemAccountRole } from '@/lib/types'

export interface AccountListParams {
  q?: string
  type?: AccountType | null
  page?: number
  size?: number
  sort?: string
}

/**
 * The editable shape of an account, for both create and update.
 *
 * @param currency omit for the organization's reporting currency
 * @param postable defaults true; false makes this a heading
 */
export interface AccountRequestBody {
  code: string
  name: string
  description?: string | null
  type: AccountType
  currency?: string | null
  parentId?: number | null
  systemRole?: SystemAccountRole | null
  postable?: boolean
}

export const accountKeys = {
  all: ['accounts'] as const,
  list: (params: AccountListParams) => ['accounts', 'list', params] as const,
  tree: (includeArchived: boolean) => ['accounts', 'tree', includeArchived] as const,
  detail: (id: string | number) => ['accounts', 'detail', String(id)] as const,
  entries: (id: string | number) => ['accounts', 'entries', String(id)] as const,
}

export function listAccounts(params: AccountListParams, signal?: AbortSignal) {
  return apiFetch<Paged<Account>>('/accounts', { params: { ...params }, signal })
}

/** The whole chart as a tree. Not paged -- a page of a tree is orphaned branches. */
export function getAccountTree(includeArchived: boolean, signal?: AbortSignal) {
  return apiFetch<AccountNode[]>('/accounts/tree', { params: { includeArchived }, signal })
}

export function getAccount(id: string | number, signal?: AbortSignal) {
  return apiFetch<Account>(`/accounts/${id}`, { signal })
}

export function getAccountEntries(id: string | number, signal?: AbortSignal) {
  return apiFetch<LedgerEntry[]>(`/accounts/${id}/entries`, { signal })
}

export function createAccount(body: AccountRequestBody) {
  return apiFetch<Account>('/accounts', { method: 'POST', body: JSON.stringify(body) })
}

export function updateAccount(id: string | number, body: AccountRequestBody) {
  return apiFetch<Account>(`/accounts/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

/** Archiving, never deleting -- the account and its history stay exactly where they were. */
export function archiveAccount(id: string | number) {
  return apiFetch<Account>(`/accounts/${id}/archive`, { method: 'POST' })
}

export function restoreAccount(id: string | number) {
  return apiFetch<Account>(`/accounts/${id}/restore`, { method: 'POST' })
}

/** Only ever succeeds for an account that has never been posted to. */
export function deleteAccount(id: string | number) {
  return apiFetch<void>(`/accounts/${id}`, { method: 'DELETE' })
}
