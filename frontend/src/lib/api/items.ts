import { apiFetch } from '@/lib/http'
import type { Item, Paged } from '@/lib/types'

export interface ItemListParams {
  q?: string
  includeArchived?: boolean
  page?: number
  size?: number
  sort?: string
}

export interface ItemRequestBody {
  sku?: string | null
  name: string
  description?: string | null
  defaultUnitPrice?: number | null
  defaultTaxRateId?: number | null
}

export const itemKeys = {
  all: ['items'] as const,
  list: (params: ItemListParams) => ['items', 'list', params] as const,
  detail: (id: string | number) => ['items', 'detail', String(id)] as const,
}

export function listItems(params: ItemListParams, signal?: AbortSignal) {
  return apiFetch<Paged<Item>>('/items', { params: { ...params }, signal })
}

export function getItem(id: string | number, signal?: AbortSignal) {
  return apiFetch<Item>(`/items/${id}`, { signal })
}

export function createItem(body: ItemRequestBody) {
  return apiFetch<Item>('/items', { method: 'POST', body: JSON.stringify(body) })
}

export function updateItem(id: string | number, body: ItemRequestBody) {
  return apiFetch<Item>(`/items/${id}`, { method: 'PUT', body: JSON.stringify(body) })
}

export function archiveItem(id: string | number) {
  return apiFetch<Item>(`/items/${id}/archive`, { method: 'POST' })
}

export function restoreItem(id: string | number) {
  return apiFetch<Item>(`/items/${id}/restore`, { method: 'POST' })
}

export function deleteItem(id: string | number) {
  return apiFetch<void>(`/items/${id}`, { method: 'DELETE' })
}
