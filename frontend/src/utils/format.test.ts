import { describe, expect, it } from 'vitest'
import { formatAmountInput, formatMoney, parseAmountInput, shortId } from './format'

describe('format helpers', () => {
  it('formats a Czech koruna amount for the Czech locale', () => {
    expect(formatMoney(2_500_000, 'CZK')).toMatch(/^2\s500\s000\sKč$/)
    expect(formatMoney(2_500_000, 'CZK')).not.toContain(',00')
  })

  it('keeps a formatted amount input numeric at the form boundary', () => {
    expect(formatAmountInput(2_500_000)).toMatch(/^2\s500\s000$/)
    expect(parseAmountInput('2 500 000 Kč')).toBe(2_500_000)
    expect(parseAmountInput('')).toBeUndefined()
  })

  it('uses a compact stable application identifier', () => {
    expect(shortId('a1b2c3d4-0000-0000-0000-000000000000')).toBe('A1B2C3D4')
  })
})
