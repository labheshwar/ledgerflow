import { apiFetch } from '@/lib/http'
import type { DashboardSummary } from '@/lib/types'

export const dashboardKeys = {
  summary: () => ['dashboard', 'summary'] as const,
}

export function getDashboardSummary(signal?: AbortSignal) {
  return apiFetch<DashboardSummary>('/dashboard/summary', { signal })
}
