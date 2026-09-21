import { apiFetch, apiFetchBlob } from '@/lib/http'
import type { InvoiceDetail } from '@/lib/types'

/** No auth header is sent, but apiFetch adds one only when a token exists, so this composes with it unchanged. */
export function getPublicInvoice(token: string, signal?: AbortSignal) {
  return apiFetch<InvoiceDetail>(`/public/invoices/${token}`, { signal })
}

export function downloadPublicInvoicePdf(token: string) {
  return apiFetchBlob(`/public/invoices/${token}/pdf`)
}
