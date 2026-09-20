import { apiFetch } from '@/lib/http'
import type { Paged, TaxRate } from '@/lib/types'

export interface TaxRateListParams {
  q?: string
  includeArchived?: boolean
  page?: number
  size?: number
  sort?: string
}

export interface TaxRateRequestBody {
  name: string
  rate: number
}

export const taxRateKeys = {
  all: ['tax-rates'] as const,
  list: (params: TaxRateListParams) => ['tax-rates', 'list', params] as const,
  detail: (id: string | number) => ['tax-rates', 'detail', String(id)] as const,
}

export function listTaxRates(params: TaxRateListParams, signal?: AbortSignal) {
  return apiFetch<Paged<TaxRate>>('/tax-rates', { params: { ...params }, signal })
}

export function getTaxRate(id: string | number, signal?: AbortSignal) {
  return apiFetch<TaxRate>(`/tax-rates/${id}`, { signal })
}

export function createTaxRate(body: TaxRateRequestBody) {
  return apiFetch<TaxRate>('/tax-rates', { method: 'POST', body: JSON.stringify(body) })
}

export function updateTaxRate(id: string | number, body: TaxRateRequestBody) {
  return apiFetch<TaxRate>(`/tax-rates/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function archiveTaxRate(id: string | number) {
  return apiFetch<TaxRate>(`/tax-rates/${id}/archive`, { method: 'POST' })
}

export function restoreTaxRate(id: string | number) {
  return apiFetch<TaxRate>(`/tax-rates/${id}/restore`, { method: 'POST' })
}

export function deleteTaxRate(id: string | number) {
  return apiFetch<void>(`/tax-rates/${id}`, { method: 'DELETE' })
}
