import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Building2, CalendarDays, CheckCircle2, CircleDollarSign, Clock3, Copy, FileCheck2, Info, ShieldCheck, XCircle } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import type { ApplicationProcessing, LoanApplication } from '../../api/types'
import { ErrorState, LoadingState } from '../../components/Feedback'
import { StatusBadge, statusLabels } from '../../components/StatusBadge'
import { formatDate, formatMoney, shortId } from '../../utils/format'
import { applicationKeys, getApplication, getApplicationProcessing, transitionApplication } from './api'

/** Workflow-aware detail screen exposing only commands legal for the current server state. */
export function ApplicationDetailPage() {
  const { applicationId = '' } = useParams()
  const location = useLocation()
  const wasJustCreated = (location.state as { created?: boolean } | null)?.created === true
  const queryClient = useQueryClient()
  const result = useQuery({
    queryKey: applicationKeys.detail(applicationId),
    queryFn: () => getApplication(applicationId),
    enabled: !!applicationId,
    // Poll only while worker processing can change state without a user action.
    refetchInterval: query => query.state.data?.status === 'SUBMITTED' ? 1_500 : false,
  })
  const processing = useQuery({
    queryKey: applicationKeys.processing(applicationId),
    queryFn: () => getApplicationProcessing(applicationId),
    enabled: !!applicationId,
    // Evidence is eventually consistent with the create response; stop polling once the worker result exists.
    refetchInterval: query => query.state.data?.preprocessing ? false : 1_500,
  })
  const transition = useMutation({
    mutationFn: (action: 'approve' | 'reject') => transitionApplication(applicationId, action, result.data!.version),
    onSuccess: updated => { queryClient.setQueryData(applicationKeys.detail(applicationId), updated); void queryClient.invalidateQueries({ queryKey: applicationKeys.all }) },
  })

  if (result.isPending) return <div className="page"><LoadingState label="Načítám detail žádosti…" /></div>
  if (result.isError) return <div className="page"><ErrorState message={result.error.message} onRetry={() => void result.refetch()} /></div>
  const application = result.data
  const errorProblem = transition.error instanceof ApiError ? transition.error.problem : undefined

  return <div className="page detail-page">
    <Link to="/applications" className="back-link"><ArrowLeft size={17} /> Zpět na přehled</Link>
    {wasJustCreated && <div className="success-banner"><CheckCircle2 /><span><strong>Žádost byla úspěšně založena.</strong> Událost čeká na spolehlivé zpracování přes transactional outbox.</span></div>}
    <div className="detail-heading"><div><p className="eyebrow">Detail žádosti</p><h1>LF-{shortId(application.id)}</h1><div className="id-line">{application.id}<button onClick={() => void navigator.clipboard.writeText(application.id)} aria-label="Kopírovat ID"><Copy size={14} /></button></div></div><StatusBadge status={application.status} /></div>
    <div className="detail-grid">
      <div className="detail-main">
        <section className="panel info-panel"><div className="panel-title"><h2>Parametry žádosti</h2><span>Verze {application.version}</span></div><div className="data-grid"><Data icon={<Building2 />} label="Firemní klient" value={application.customerId} /><Data icon={<CircleDollarSign />} label="Požadovaný objem" value={formatMoney(application.amount, application.currency)} /><Data icon={<CalendarDays />} label="Založeno" value={formatDate(application.createdAt, true)} /><Data icon={<Clock3 />} label="Poslední změna" value={formatDate(application.updatedAt, true)} /></div></section>
        <section className="panel timeline-panel"><div className="panel-title"><h2>Průběh zpracování</h2></div><Workflow application={application} /></section>
        <ProcessingEvidence processing={processing.data} pending={processing.isPending} error={processing.isError} />
      </div>
      <aside className="panel action-panel"><h2>Další krok</h2><p>{actionCopy(application.status)}</p>{transition.isError && <div className="compact-error"><Info /><span>{transition.error.message}{errorProblem?.correlationId && <small>ID: {errorProblem.correlationId}</small>}</span></div>}<Actions application={application} pending={transition.isPending} onAction={action => transition.mutate(action)} /><div className="audit-note"><FileCheck2 /><span><strong>Auditovatelný workflow</strong><small>Změny jsou chráněné optimistic lockingem a publikované přes outbox.</small></span></div></aside>
    </div>
  </div>
}

/** Presents persisted operational evidence; identifiers remain visible for log and database correlation. */
function ProcessingEvidence({ processing, pending, error }: { processing?: ApplicationProcessing; pending: boolean; error: boolean }) {
  return <section className="panel evidence-panel">
    <div className="panel-title"><h2>Audit a procesní kontrola</h2><span>{auditCountLabel(processing?.statusHistory.length ?? 0)}</span></div>
    {pending ? <div className="evidence-message">Načítám auditní stopu…</div> : error ? <div className="evidence-message evidence-error">Auditní stopu se nepodařilo načíst.</div> : <>
      <div className={`preprocessing-result ${processing?.preprocessing ? 'passed' : 'waiting'}`}>
        <ShieldCheck />
        <div><strong>{processing?.preprocessing ? 'Předběžná kontrola dokončena' : 'Předběžná kontrola čeká na zpracování'}</strong><span>{processing?.preprocessing ? 'Událost odpovídá uložené žádosti a je připravena k ručnímu posouzení.' : 'Worker zatím neuložil výsledek kontroly.'}</span>{processing?.preprocessing && <small>{formatDate(processing.preprocessing.checkedAt, true)} · Event {shortId(processing.preprocessing.eventId)}</small>}</div>
      </div>
      <ol className="audit-list">{processing?.statusHistory.map(entry => <li key={entry.id}>
        <i />
        <div className="audit-entry-main"><strong>{statusLabels[entry.newStatus]}</strong><span>{entry.previousStatus ? `${statusLabels[entry.previousStatus]} → ` : ''}{statusLabels[entry.newStatus]} · verze {entry.applicationVersion}</span><small>{formatDate(entry.changedAt, true)} · {entry.changedBy === 'WORKER' ? 'Processing worker' : 'Loan API'}</small></div>
        <div className="audit-identifiers"><span>Request {shortId(entry.requestId)}</span>{entry.eventId && <span>Event {shortId(entry.eventId)}</span>}</div>
      </li>)}</ol>
    </>}
  </section>
}

function auditCountLabel(count: number) {
  if (count === 1) return '1 záznam'
  if (count >= 2 && count <= 4) return `${count} záznamy`
  return `${count} záznamů`
}

function Data({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) { return <div className="data-item"><span>{icon}</span><div><small>{label}</small><strong>{value}</strong></div></div> }

/** Maps backend workflow state to the smallest legal set of operator actions. */
function Actions({ application, pending, onAction }: { application: LoanApplication; pending: boolean; onAction: (action: 'approve' | 'reject') => void }) {
  if (application.status === 'SUBMITTED') return <div className="closed-state waiting-state"><Clock3 /><span><strong>Probíhá předběžná kontrola</strong><small>Worker ověřuje procesní konzistenci žádosti.</small></span></div>
  if (application.status === 'UNDER_REVIEW') return <div className="decision-actions"><button className="button primary wide" disabled={pending} onClick={() => onAction('approve')}><CheckCircle2 size={18} /> Schválit žádost</button><button className="button danger wide" disabled={pending} onClick={() => onAction('reject')}><XCircle size={18} /> Zamítnout žádost</button></div>
  return <div className="closed-state"><CheckCircle2 /><span><strong>Rozhodnutí je konečné</strong><small>Stav již nelze změnit.</small></span></div>
}

/** Derives presentation from aggregate state rather than inventing client-side workflow state. */
function Workflow({ application }: { application: LoanApplication }) {
  const rejected = application.status === 'REJECTED'; const reviewed = application.status !== 'SUBMITTED'; const decided = application.status === 'APPROVED' || rejected
  return <div className="workflow"><div className="workflow-step done"><i><CheckCircle2 /></i><div><strong>Žádost podána</strong><small>{formatDate(application.createdAt, true)}</small></div></div><div className={`workflow-step ${reviewed ? 'done' : 'current'}`}><i>{reviewed ? <CheckCircle2 /> : '2'}</i><div><strong>Úvěrové posouzení</strong><small>{reviewed ? 'Převzato ke zpracování' : 'Čeká na převzetí'}</small></div></div><div className={`workflow-step ${decided ? (rejected ? 'rejected' : 'done') : reviewed ? 'current' : ''}`}><i>{decided ? (rejected ? <XCircle /> : <CheckCircle2 />) : '3'}</i><div><strong>{decided ? statusLabels[application.status] : 'Rozhodnutí'}</strong><small>{decided ? formatDate(application.updatedAt, true) : 'Čeká na posouzení'}</small></div></div></div>
}

function actionCopy(status: LoanApplication['status']) { return status === 'SUBMITTED' ? 'Žádost čeká na automatickou předběžnou validační a procesní kontrolu.' : status === 'UNDER_REVIEW' ? 'Předběžná kontrola proběhla. Zaznamenejte konečné rozhodnutí.' : 'Žádost byla uzavřena konečným rozhodnutím.' }
