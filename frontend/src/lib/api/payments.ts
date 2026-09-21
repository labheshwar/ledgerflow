import { apiFetch } from '@/lib/http'
import type { DocumentType, OpenDocument, Paged, Payment, PaymentDirection } from '@/lib/types'

export interface PaymentListParams {
  q?: string
  direction?: PaymentDirection | null
  page?: number
  size?: number
  sort?: string
}

export interface PaymentAllocationRequestBody {
  documentType: DocumentType
  documentId: number
  amount: number
}

export interface PaymentRequestBody {
  contactId: number
  direction: PaymentDirection
  paymentDate: string
  amount: number
  notes?: string | null
  allocations: PaymentAllocationRequestBody[]
}

export const paymentKeys = {
  all: ['payments'] as const,
  list: (params: PaymentListParams) => ['payments', 'list', params] as const,
  detail: (id: string | number) => ['payments', 'detail', String(id)] as const,
  openDocuments: (contactId: number, direction: PaymentDirection) =>
    ['payments', 'open-documents', contactId, direction] as const,
}

export function listPayments(params: PaymentListParams, signal?: AbortSignal) {
  return apiFetch<Paged<Payment>>('/payments', { params: { ...params }, signal })
}

export function getPayment(id: string | number, signal?: AbortSignal) {
  return apiFetch<Payment>(`/payments/${id}`, { signal })
}

export function createPayment(body: PaymentRequestBody) {
  return apiFetch<Payment>('/payments', { method: 'POST', body: JSON.stringify(body) })
}

/** Reverses the posting and marks the payment VOID -- never an edit or a delete. */
export function voidPayment(id: string | number, reason?: string | null) {
  return apiFetch<Payment>(`/payments/${id}/void`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason || null }),
  })
}

/** What a payment in this direction, for this contact, could still be allocated against. */
export function listOpenDocuments(contactId: number, direction: PaymentDirection, signal?: AbortSignal) {
  return apiFetch<OpenDocument[]>('/payments/open-documents', { params: { contactId, direction }, signal })
}
