import { apiFetch } from '@/lib/http'
import type { Contact, ContactType, Paged } from '@/lib/types'

export interface ContactListParams {
  q?: string
  type?: ContactType | null
  includeArchived?: boolean
  page?: number
  size?: number
  sort?: string
}

export interface ContactRequestBody {
  type: ContactType
  name: string
  email?: string | null
  phone?: string | null
  taxId?: string | null
  addressLine1?: string | null
  addressLine2?: string | null
  city?: string | null
  state?: string | null
  postalCode?: string | null
  country?: string | null
  notes?: string | null
}

export const contactKeys = {
  all: ['contacts'] as const,
  list: (params: ContactListParams) => ['contacts', 'list', params] as const,
  detail: (id: string | number) => ['contacts', 'detail', String(id)] as const,
}

export function listContacts(params: ContactListParams, signal?: AbortSignal) {
  return apiFetch<Paged<Contact>>('/contacts', { params: { ...params }, signal })
}

export function getContact(id: string | number, signal?: AbortSignal) {
  return apiFetch<Contact>(`/contacts/${id}`, { signal })
}

export function createContact(body: ContactRequestBody) {
  return apiFetch<Contact>('/contacts', { method: 'POST', body: JSON.stringify(body) })
}

export function updateContact(id: string | number, body: ContactRequestBody) {
  return apiFetch<Contact>(`/contacts/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function archiveContact(id: string | number) {
  return apiFetch<Contact>(`/contacts/${id}/archive`, { method: 'POST' })
}

export function restoreContact(id: string | number) {
  return apiFetch<Contact>(`/contacts/${id}/restore`, { method: 'POST' })
}

export function deleteContact(id: string | number) {
  return apiFetch<void>(`/contacts/${id}`, { method: 'DELETE' })
}
