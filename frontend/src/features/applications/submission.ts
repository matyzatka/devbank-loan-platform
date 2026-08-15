import type { CreateApplicationInput } from '../../api/types'
import type { CreateApplicationCommand } from './api'

export interface PendingSubmission {
  fingerprint: string
  idempotencyKey: string
}

/** Keeps one idempotency key for retries of the same logical form submission. */
export function prepareSubmission(
  input: CreateApplicationInput,
  pending: PendingSubmission | undefined,
  generateKey: () => string = () => crypto.randomUUID(),
): { command: CreateApplicationCommand; pending: PendingSubmission } {
  const fingerprint = JSON.stringify(input)
  const current = pending?.fingerprint === fingerprint
    ? pending
    : { fingerprint, idempotencyKey: generateKey() }

  return {
    command: { input, idempotencyKey: current.idempotencyKey },
    pending: current,
  }
}
