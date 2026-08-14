import { describe, expect, it } from 'vitest'
import { formatMoney, shortId } from './format'

describe('format helpers', () => {
  it('formats a Czech koruna amount for the Czech locale', () => {
    expect(formatMoney(2_500_000, 'CZK')).toContain('2 500 000')
  })

  it('uses a compact stable application identifier', () => {
    expect(shortId('a1b2c3d4-0000-0000-0000-000000000000')).toBe('A1B2C3D4')
  })
})
