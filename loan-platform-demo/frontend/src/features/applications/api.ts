import { apiRequest } from '../../api/client'
import type { ApplicationPage, ApplicationStatus, CreateApplicationInput, LoanApplication } from '../../api/types'

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
  return apiRequest<LoanApplication>('/api/v1/applications', {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify(input),
  })
}

export function transitionApplication(id: string, action: 'review' | 'approve' | 'reject') {
  return apiRequest<LoanApplication>(`/api/v1/applications/${id}/${action}`, { method: 'POST' })
}
