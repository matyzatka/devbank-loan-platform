import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApplicationDetailPage } from './ApplicationDetailPage'
import { NewApplicationPage } from './NewApplicationPage'

const applicationId = '11111111-1111-4111-8111-111111111111'
const application = {
  id: applicationId,
  customerId: 'Morava Precision s.r.o.',
  amount: 18_500_000,
  currency: 'CZK',
  status: 'UNDER_REVIEW',
  rejectionReason: null,
  version: 1,
  createdAt: '2026-08-14T10:00:00Z',
  updatedAt: '2026-08-14T11:00:00Z',
}

const processing = {
  preprocessing: {
    eventId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    result: 'PASSED',
    details: 'Kontrola dokončena.',
    checkedAt: '2026-08-14T11:00:00Z',
  },
  statusHistory: [],
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('application workflow UI', () => {
  it('formats the amount while keeping the complete four-step process visible', () => {
    renderWithClient(
      <MemoryRouter initialEntries={['/applications/new']}>
        <Routes><Route path="/applications/new" element={<NewApplicationPage />} /></Routes>
      </MemoryRouter>,
    )

    const amount = screen.getByRole('textbox', { name: /požadovaná částka/i })
    fireEvent.change(amount, { target: { value: '2500000' } })

    expect(amount).toHaveValue('2 500 000')
    expect(screen.getByText('Založení žádosti')).toBeVisible()
    expect(screen.getByText('Předběžná automatická kontrola')).toBeVisible()
    expect(screen.getByText('Posouzení specialistou')).toBeVisible()
    expect(screen.getByText('Rozhodnutí')).toBeVisible()
  })

  it('requires a rejection reason before enabling the irreversible decision', async () => {
    vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      return Promise.resolve(jsonResponse(url.endsWith('/processing') ? processing : application))
    }))

    renderWithClient(
      <MemoryRouter initialEntries={[`/applications/${applicationId}`]}>
        <Routes><Route path="/applications/:applicationId" element={<ApplicationDetailPage />} /></Routes>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Zamítnout žádost' }))

    const confirm = screen.getByRole('button', { name: 'Potvrdit zamítnutí' })
    expect(confirm).toBeDisabled()

    fireEvent.change(screen.getByRole('textbox', { name: /důvod zamítnutí/i }), {
      target: { value: 'Nedoložené finanční výkazy.' },
    })

    expect(confirm).toBeEnabled()
  })
})

function renderWithClient(ui: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}
