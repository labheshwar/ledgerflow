import { apiFetch, apiFetchBlob } from '@/lib/http'
import type { Attachment, InvoiceDetail, InvoiceListItem, InvoiceStatus, Paged } from '@/lib/types'

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

export function downloadInvoicePdf(id: string | number) {
  return apiFetchBlob(`/invoices/${id}/pdf`)
}

/** Idempotent: the same invoice always resolves to the same link once one exists. */
export function getOrCreatePublicLink(id: string | number) {
  return apiFetch<{ url: string }>(`/invoices/${id}/public-link`, { method: 'POST' })
}

/** Queued -- the worker renders the PDF and sends the mail, so this returns before either happens. */
export function emailInvoice(id: string | number, recipientEmail: string) {
  return apiFetch<void>(`/invoices/${id}/email`, { method: 'POST', body: JSON.stringify({ recipientEmail }) })
}

export function remindInvoice(id: string | number, recipientEmail: string) {
  return apiFetch<void>(`/invoices/${id}/remind`, {
    method: 'POST',
    body: JSON.stringify({ recipientEmail }),
  })
}

export function listAttachments(id: string | number) {
  return apiFetch<Attachment[]>(`/invoices/${id}/attachments`)
}

export function uploadAttachment(id: string | number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch<Attachment>(`/invoices/${id}/attachments`, { method: 'POST', body: formData })
}

export function downloadAttachment(id: string | number, attachmentId: number) {
  return apiFetchBlob(`/invoices/${id}/attachments/${attachmentId}`)
}

export function deleteAttachment(id: string | number, attachmentId: number) {
  return apiFetch<void>(`/invoices/${id}/attachments/${attachmentId}`, { method: 'DELETE' })
}
