import type { ApplicationStatus } from '../api/types'

// Labels live beside the badge so every workflow view uses the same Czech domain vocabulary.
// eslint-disable-next-line react-refresh/only-export-components
export const statusLabels: Record<ApplicationStatus, string> = {
  SUBMITTED: 'Nová',
  UNDER_REVIEW: 'V posouzení',
  APPROVED: 'Schválená',
  REJECTED: 'Zamítnutá',
}

export function StatusBadge({ status }: { status: ApplicationStatus }) {
  return <span className={`status status-${status.toLowerCase()}`}><i />{statusLabels[status]}</span>
}
