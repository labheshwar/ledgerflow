import { apiFetch } from '@/lib/http'
import type { Role } from '@/lib/types'

export interface Membership {
  orgId: number
  organizationName: string
  role: Role
}

export interface CurrentUser {
  username: string
  currentOrgId: number
  currentOrgName: string
  role: Role
  memberships: Membership[]
}

export const authKeys = {
  me: () => ['auth', 'me'] as const,
}

export function getCurrentUser(signal?: AbortSignal) {
  return apiFetch<CurrentUser>('/auth/me', { signal })
}

export function signUp(body: { username: string; password: string; organizationName: string }) {
  return apiFetch<{ token: string }>('/auth/signup', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function switchOrganization(orgId: number) {
  return apiFetch<{ token: string }>(`/auth/switch-org/${orgId}`, { method: 'POST' })
}
