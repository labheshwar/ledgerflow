import { apiFetch } from '@/lib/http'
import type { FxRate, Paged } from '@/lib/types'

export interface FxRateListParams {
  page?: number
  size?: number
  sort?: string
}

export interface FxRateRequestBody {
  currency: string
  rate: number
  asOfDate: string
}

export const fxRateKeys = {
  all: ['fx-rates'] as const,
  list: (params: FxRateListParams) => ['fx-rates', 'list', params] as const,
}

export function listFxRates(params: FxRateListParams, signal?: AbortSignal) {
  return apiFetch<Paged<FxRate>>('/fx-rates', { params: { ...params }, signal })
}

/** Recording the same currency and date again corrects that rate rather than creating a duplicate. */
export function recordFxRate(body: FxRateRequestBody) {
  return apiFetch<FxRate>('/fx-rates', { method: 'POST', body: JSON.stringify(body) })
}
