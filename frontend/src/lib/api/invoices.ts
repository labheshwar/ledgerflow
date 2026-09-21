import { apiFetch } from '@/lib/http'
import type { InvoiceDetail, InvoiceListItem, InvoiceStatus, Paged } from '@/lib/types'

export interface InvoiceListParams {
  q?: string
  status?: InvoiceStatus | null
  page?: number
  size?: number
  sort?: string
}

export interface InvoiceLineRequestBody {
  itemId?: number | null
  description: string
  quantity: number
  unitPrice: number
  taxRateId?: number | null
}

export interface InvoiceRequestBody {
  contactId: number
  issueDate: string
  dueDate: string
  notes?: string | null
  lines: InvoiceLineRequestBody[]
}

export const invoiceKeys = {
  all: ['invoices'] as const,
  list: (params: InvoiceListParams) => ['invoices', 'list', params] as const,
  detail: (id: string | number) => ['invoices', 'detail', String(id)] as const,
}

export function listInvoices(params: InvoiceListParams, signal?: AbortSignal) {
  return apiFetch<Paged<InvoiceListItem>>('/invoices', { params: { ...params }, signal })
}

export function getInvoice(id: string | number, signal?: AbortSignal) {
  return apiFetch<InvoiceDetail>(`/invoices/${id}`, { signal })
}

export function createInvoice(body: InvoiceRequestBody) {
  return apiFetch<InvoiceDetail>('/invoices', { method: 'POST', body: JSON.stringify(body) })
}

export function updateInvoice(id: string | number, body: InvoiceRequestBody) {
  return apiFetch<InvoiceDetail>(`/invoices/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function deleteInvoice(id: string | number) {
  return apiFetch<void>(`/invoices/${id}`, { method: 'DELETE' })
}

/** Draws a document number and posts DR Accounts Receivable / CR Sales Revenue / CR Tax Payable. */
export function sendInvoice(id: string | number) {
  return apiFetch<InvoiceDetail>(`/invoices/${id}/send`, { method: 'POST' })
}

/** Reverses the posting and marks the invoice VOID -- never an edit or a delete. */
export function voidInvoice(id: string | number, reason?: string | null) {
  return apiFetch<InvoiceDetail>(`/invoices/${id}/void`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason || null }),
  })
}
