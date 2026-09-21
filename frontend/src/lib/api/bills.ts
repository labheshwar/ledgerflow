import { apiFetch, apiFetchBlob } from '@/lib/http'
import type { Attachment, BillDetail, BillListItem, BillStatus, Paged } from '@/lib/types'

export interface BillListParams {
  q?: string
  status?: BillStatus | null
  page?: number
  size?: number
  sort?: string
}

export interface BillLineRequestBody {
  accountId: number
  itemId?: number | null
  description: string
  quantity: number
  unitPrice: number
  taxRateId?: number | null
}

export interface BillRequestBody {
  contactId: number
  vendorReference: string
  billDate: string
  dueDate: string
  notes?: string | null
  lines: BillLineRequestBody[]
}

export const billKeys = {
  all: ['bills'] as const,
  list: (params: BillListParams) => ['bills', 'list', params] as const,
  detail: (id: string | number) => ['bills', 'detail', String(id)] as const,
}

export function listBills(params: BillListParams, signal?: AbortSignal) {
  return apiFetch<Paged<BillListItem>>('/bills', { params: { ...params }, signal })
}

export function getBill(id: string | number, signal?: AbortSignal) {
  return apiFetch<BillDetail>(`/bills/${id}`, { signal })
}

export function createBill(body: BillRequestBody) {
  return apiFetch<BillDetail>('/bills', { method: 'POST', body: JSON.stringify(body) })
}

export function updateBill(id: string | number, body: BillRequestBody) {
  return apiFetch<BillDetail>(`/bills/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function deleteBill(id: string | number) {
  return apiFetch<void>(`/bills/${id}`, { method: 'DELETE' })
}

/** Draws a document number and posts DR each line's account / DR Tax Receivable / CR Accounts Payable. */
export function postBill(id: string | number) {
  return apiFetch<BillDetail>(`/bills/${id}/post`, { method: 'POST' })
}

/** Reverses the posting and marks the bill VOID -- never an edit or a delete. */
export function voidBill(id: string | number, reason?: string | null) {
  return apiFetch<BillDetail>(`/bills/${id}/void`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason || null }),
  })
}

export function listBillAttachments(id: string | number) {
  return apiFetch<Attachment[]>(`/bills/${id}/attachments`)
}

export function uploadBillAttachment(id: string | number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch<Attachment>(`/bills/${id}/attachments`, { method: 'POST', body: formData })
}

export function downloadBillAttachment(id: string | number, attachmentId: number) {
  return apiFetchBlob(`/bills/${id}/attachments/${attachmentId}`)
}

export function deleteBillAttachment(id: string | number, attachmentId: number) {
  return apiFetch<void>(`/bills/${id}/attachments/${attachmentId}`, { method: 'DELETE' })
}
