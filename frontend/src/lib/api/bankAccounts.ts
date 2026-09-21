import { apiFetch } from '@/lib/http'
import type { BankAccount, Paged, StatementImport, StatementLine } from '@/lib/types'

export interface BankAccountListParams {
  q?: string
  includeArchived?: boolean
  page?: number
  size?: number
  sort?: string
}

export interface BankAccountRequestBody {
  accountId: number
  name: string
  accountNumberLast4?: string | null
}

export interface ColumnMappingRequestBody {
  dateColumn: string
  descriptionColumn: string
  amountColumn: string
  externalIdColumn?: string | null
}

export const bankAccountKeys = {
  all: ['bank-accounts'] as const,
  list: (params: BankAccountListParams) => ['bank-accounts', 'list', params] as const,
  detail: (id: string | number) => ['bank-accounts', 'detail', String(id)] as const,
  imports: (bankAccountId: string | number) => ['bank-accounts', String(bankAccountId), 'imports'] as const,
  importDetail: (bankAccountId: string | number, importId: string | number) =>
    ['bank-accounts', String(bankAccountId), 'imports', String(importId)] as const,
  importLines: (bankAccountId: string | number, importId: string | number) =>
    ['bank-accounts', String(bankAccountId), 'imports', String(importId), 'lines'] as const,
  lines: (bankAccountId: string | number) => ['bank-accounts', String(bankAccountId), 'lines'] as const,
}

export function listBankAccounts(params: BankAccountListParams, signal?: AbortSignal) {
  return apiFetch<Paged<BankAccount>>('/bank-accounts', { params: { ...params }, signal })
}

export function getBankAccount(id: string | number, signal?: AbortSignal) {
  return apiFetch<BankAccount>(`/bank-accounts/${id}`, { signal })
}

export function createBankAccount(body: BankAccountRequestBody) {
  return apiFetch<BankAccount>('/bank-accounts', { method: 'POST', body: JSON.stringify(body) })
}

export function updateBankAccount(id: string | number, body: BankAccountRequestBody) {
  return apiFetch<BankAccount>(`/bank-accounts/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function archiveBankAccount(id: string | number) {
  return apiFetch<BankAccount>(`/bank-accounts/${id}/archive`, { method: 'POST' })
}

export function restoreBankAccount(id: string | number) {
  return apiFetch<BankAccount>(`/bank-accounts/${id}/restore`, { method: 'POST' })
}

/** Step 1: stores the file and hands back its own header row for the mapping step. */
export function uploadStatement(bankAccountId: string | number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch<{ importId: number; headers: string[] }>(`/bank-accounts/${bankAccountId}/imports`, {
    method: 'POST',
    body: formData,
  })
}

export function listImports(
  bankAccountId: string | number,
  params: { page?: number; size?: number },
  signal?: AbortSignal,
) {
  return apiFetch<Paged<StatementImport>>(`/bank-accounts/${bankAccountId}/imports`, { params, signal })
}

export function getImport(bankAccountId: string | number, importId: string | number, signal?: AbortSignal) {
  return apiFetch<StatementImport>(`/bank-accounts/${bankAccountId}/imports/${importId}`, { signal })
}

/** Re-read from the stored file on demand, so a page refresh on the mapping step never loses them. */
export function getImportHeaders(
  bankAccountId: string | number,
  importId: string | number,
  signal?: AbortSignal,
) {
  return apiFetch<string[]>(`/bank-accounts/${bankAccountId}/imports/${importId}/headers`, { signal })
}

/** Step 2: saves the mapping and queues the worker job that computes the preview. */
export function requestPreview(
  bankAccountId: string | number,
  importId: string | number,
  mapping: ColumnMappingRequestBody,
) {
  return apiFetch<StatementImport>(`/bank-accounts/${bankAccountId}/imports/${importId}/preview`, {
    method: 'POST',
    body: JSON.stringify(mapping),
  })
}

/** Step 3: the rows the worker staged, not yet real until committed. */
export function listPreviewLines(
  bankAccountId: string | number,
  importId: string | number,
  params: { page?: number; size?: number },
  signal?: AbortSignal,
) {
  return apiFetch<Paged<StatementLine>>(`/bank-accounts/${bankAccountId}/imports/${importId}/lines`, {
    params,
    signal,
  })
}

/** Step 4: makes the staged rows real. */
export function commitImport(bankAccountId: string | number, importId: string | number) {
  return apiFetch<StatementImport>(`/bank-accounts/${bankAccountId}/imports/${importId}/commit`, {
    method: 'POST',
  })
}

/** The statement itself, once at least one import has been committed. */
export function listCommittedLines(
  bankAccountId: string | number,
  params: { page?: number; size?: number },
  signal?: AbortSignal,
) {
  return apiFetch<Paged<StatementLine>>(`/bank-accounts/${bankAccountId}/lines`, { params, signal })
}
