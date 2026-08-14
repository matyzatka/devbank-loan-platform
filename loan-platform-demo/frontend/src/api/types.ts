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

export interface ApiProblem {
  title?: string
  detail?: string
  code?: string
  correlationId?: string
  violations?: Array<{ field: string; message: string }>
}
