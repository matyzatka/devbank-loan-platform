/** Wire contracts intentionally mirror the versioned public API rather than backend implementation types. */
export type ApplicationStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED'

export interface LoanApplication {
  id: string
  customerId: string
  amount: number
  currency: string
  status: ApplicationStatus
  version: number
  createdAt: string
  updatedAt: string
}

export interface ApplicationPage {
  items: LoanApplication[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CreateApplicationInput {
  customerId: string
  amount: number
  currency: string
}

export interface PreprocessingResult {
  eventId: string
  result: 'PASSED'
  details: string
  checkedAt: string
}

export interface StatusHistoryEntry {
  id: string
  previousStatus: ApplicationStatus | null
  newStatus: ApplicationStatus
  applicationVersion: number
  changedAt: string
  changedBy: 'API' | 'WORKER'
  requestId: string
  eventId: string | null
}

export interface ApplicationProcessing {
  preprocessing: PreprocessingResult | null
  statusHistory: StatusHistoryEntry[]
}

export interface ApiProblem {
  title?: string
  detail?: string
  code?: string
  correlationId?: string
  violations?: Array<{ field: string; message: string }>
}
