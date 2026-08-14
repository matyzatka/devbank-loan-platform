export function formatMoney(amount: number, currency: string) {
  return new Intl.NumberFormat('cs-CZ', { style: 'currency', currency, maximumFractionDigits: 2 }).format(amount)
}

export function formatDate(value: string, withTime = false) {
  return new Intl.DateTimeFormat('cs-CZ', withTime
    ? { dateStyle: 'medium', timeStyle: 'short' }
    : { dateStyle: 'medium' }).format(new Date(value))
}

export function shortId(id: string) {
  return id.slice(0, 8).toUpperCase()
}
