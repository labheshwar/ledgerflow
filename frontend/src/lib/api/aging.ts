import { apiFetch } from '@/lib/http'
import type { ApAgingRow, ArAgingRow } from '@/lib/types'

export const agingKeys = {
  ar: () => ['ar-aging'] as const,
  ap: () => ['ap-aging'] as const,
}

export function listArAging(signal?: AbortSignal) {
  return apiFetch<ArAgingRow[]>('/ar-aging', { signal })
}

export function listApAging(signal?: AbortSignal) {
  return apiFetch<ApAgingRow[]>('/ap-aging', { signal })
}
