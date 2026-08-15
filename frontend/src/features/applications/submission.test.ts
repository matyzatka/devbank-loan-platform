import { describe, expect, it, vi } from 'vitest'
import { prepareSubmission } from './submission'

const input = { customerId: 'Labe Engineering s.r.o.', amount: 2_500_000, currency: 'CZK' }

describe('prepareSubmission', () => {
  it('reuses the idempotency key when the same submission is retried', () => {
    const generateKey = vi.fn()
      .mockReturnValueOnce('first-key')
      .mockReturnValueOnce('second-key')

    const first = prepareSubmission(input, undefined, generateKey)
    const retry = prepareSubmission({ ...input }, first.pending, generateKey)

    expect(first.command.idempotencyKey).toBe('first-key')
    expect(retry.command.idempotencyKey).toBe('first-key')
    expect(generateKey).toHaveBeenCalledOnce()
  })

  it('creates a new key for a materially different submission', () => {
    const first = prepareSubmission(input, undefined, () => 'first-key')
    const changed = prepareSubmission({ ...input, amount: 3_000_000 }, first.pending, () => 'second-key')

    expect(changed.command.idempotencyKey).toBe('second-key')
  })
})
