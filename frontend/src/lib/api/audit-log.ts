import { apiFetch } from '@/lib/http'
import type { AuditLogEntry, Paged } from '@/lib/types'

export interface AuditLogListParams {
  q?: string
  entityType?: string | null
  page?: number
  size?: number
  sort?: string
}

export const auditLogKeys = {
  all: ['audit-log'] as const,
  list: (params: AuditLogListParams) => ['audit-log', 'list', params] as const,
}

export function listAuditLog(params: AuditLogListParams, signal?: AbortSignal) {
  return apiFetch<Paged<AuditLogEntry>>('/audit-log', { params: { ...params }, signal })
}
