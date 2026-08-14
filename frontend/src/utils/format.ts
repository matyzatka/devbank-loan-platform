/** Locale-aware presentation helpers; API values remain locale-neutral. */
export function formatMoney(amount: number, currency: string) {
  const wholeCurrency = currency === 'CZK'
  return new Intl.NumberFormat('cs-CZ', {
    style: 'currency',
    currency,
    minimumFractionDigits: wholeCurrency ? 0 : 2,
    maximumFractionDigits: wholeCurrency ? 0 : 2,
  }).format(amount)
}

/** Formats a whole-number amount for editing without changing its numeric form value. */
export function formatAmountInput(amount?: number) {
  return amount === undefined || Number.isNaN(amount)
    ? ''
    : new Intl.NumberFormat('cs-CZ', { maximumFractionDigits: 0 }).format(amount)
}

/** Accepts pasted or typed Czech thousand separators and returns a transport-safe number. */
export function parseAmountInput(value: string) {
  const digits = value.replace(/\D/g, '')
  return digits ? Number(digits) : undefined
}

export function formatDate(value: string, withTime = false) {
  return new Intl.DateTimeFormat('cs-CZ', withTime
    ? { dateStyle: 'medium', timeStyle: 'short' }
    : { dateStyle: 'medium' }).format(new Date(value))
}

export function shortId(id: string) {
  // Short IDs are display-only; navigation and API calls always retain the full UUID.
  return id.slice(0, 8).toUpperCase()
}
