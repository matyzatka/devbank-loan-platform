import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ChevronLeft, ChevronRight, Filter, Plus, Search } from 'lucide-react'
import { useDeferredValue, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { ApplicationStatus } from '../../api/types'
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDate, formatMoney, shortId } from '../../utils/format'
import { applicationKeys, listApplications } from './api'

const statuses: Array<{ value: '' | ApplicationStatus; label: string }> = [
  { value: '', label: 'Všechny stavy' }, { value: 'SUBMITTED', label: 'Nové' },
  { value: 'UNDER_REVIEW', label: 'V posouzení' }, { value: 'APPROVED', label: 'Schválené' },
  { value: 'REJECTED', label: 'Zamítnuté' },
]

export function ApplicationsPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<'' | ApplicationStatus>('')
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query)
  const filters = { page, size: 10, status: status || undefined, query: deferredQuery || undefined }
  const result = useQuery({ queryKey: applicationKeys.list(filters), queryFn: () => listApplications(filters) })

  return (
    <div className="page applications-page">
      <div className="page-heading">
        <div><p className="eyebrow">Úvěrové operace</p><h1>Přehled žádostí</h1><p>Správa firemních úvěrů od podání po rozhodnutí.</p></div>
        <Link className="button primary" to="/applications/new"><Plus size={18} /> Nová žádost</Link>
      </div>
      <section className="summary-strip" aria-label="Souhrn portfolia">
        <div><span>Celkem ve výběru</span><strong>{result.data?.totalElements ?? '—'}</strong></div>
        <div><span>Aktivní fronta</span><strong>{result.data?.items.filter(x => x.status === 'SUBMITTED' || x.status === 'UNDER_REVIEW').length ?? '—'}</strong></div>
        <div className="summary-note"><i /><span><strong>Aktuální data</strong><small>Automatická synchronizace API</small></span></div>
      </section>
      <section className="panel list-panel">
        <div className="toolbar">
          <label className="search-field"><Search size={18} /><input value={query} onChange={event => { setQuery(event.target.value); setPage(0) }} placeholder="Hledat klienta nebo ID žádosti" aria-label="Hledat žádost" /></label>
          <label className="select-field"><Filter size={17} /><select value={status} onChange={event => { setStatus(event.target.value as '' | ApplicationStatus); setPage(0) }}>{statuses.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
        </div>
        {result.isPending ? <LoadingState label="Načítám žádosti…" /> : result.isError ? <ErrorState message={result.error.message} onRetry={() => void result.refetch()} /> : result.data.items.length === 0 ? <EmptyState /> : (
          <div className="table-wrap"><table><thead><tr><th>Žádost</th><th>Klient</th><th>Objem</th><th>Stav</th><th>Aktualizováno</th><th><span className="sr-only">Otevřít</span></th></tr></thead>
            <tbody>{result.data.items.map(item => <tr key={item.id} onClick={() => void navigate(`/applications/${item.id}`)}>
              <td><strong>LF-{shortId(item.id)}</strong><small>v{item.version}</small></td><td><strong>{item.customerId}</strong><small>Firemní klient</small></td><td className="money">{formatMoney(item.amount, item.currency)}</td><td><StatusBadge status={item.status} /></td><td>{formatDate(item.updatedAt)}</td><td><ArrowRight size={18} /></td>
            </tr>)}</tbody></table></div>
        )}
        {result.data && result.data.totalPages > 1 && <div className="pagination"><span>Strana {result.data.page + 1} z {result.data.totalPages}</span><div><button disabled={page === 0} onClick={() => setPage(value => value - 1)}><ChevronLeft /></button><button disabled={page + 1 >= result.data.totalPages} onClick={() => setPage(value => value + 1)}><ChevronRight /></button></div></div>}
      </section>
    </div>
  )
}
