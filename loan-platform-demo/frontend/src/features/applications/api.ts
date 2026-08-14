import { apiRequest } from '../../api/client'
import type { ApplicationPage, ApplicationStatus, CreateApplicationInput, LoanApplication } from '../../api/types'

/** Hierarchical keys make list-wide invalidation possible without coupling pages to cache internals. */
export const applicationKeys = {
  all: ['applications'] as const,
  list: (filters: ListFilters) => [...applicationKeys.all, 'list', filters] as const,
  detail: (id: string) => [...applicationKeys.all, 'detail', id] as const,
}

export interface ListFilters {
  page: number
  size: number
  status?: ApplicationStatus
  query?: string
}

export function listApplications(filters: ListFilters) {
  const params = new URLSearchParams({ page: String(filters.page), size: String(filters.size) })
  if (filters.status) params.set('status', filters.status)
  if (filters.query) params.set('query', filters.query)
  return apiRequest<ApplicationPage>(`/api/v1/applications?${params}`)
}

export function getApplication(id: string) {
  return apiRequest<LoanApplication>(`/api/v1/applications/${id}`)
}

export function createApplication(input: CreateApplicationInput) {
  // A new key belongs to one user submission; TanStack retries of this request remain safe server-side.
  return apiRequest<LoanApplication>('/api/v1/applications', {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify(input),
  })
}

export function transitionApplication(id: string, action: 'approve' | 'reject', expectedVersion: number) {
  return apiRequest<LoanApplication>(`/api/v1/applications/${id}/${action}`, {
    method: 'POST',
    body: JSON.stringify({ expectedVersion }),
  })
}
