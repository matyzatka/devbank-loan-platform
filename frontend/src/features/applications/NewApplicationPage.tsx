import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Check, Info, ShieldCheck } from 'lucide-react'
import { Controller, useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { ApiError } from '../../api/client'
import { formatAmountInput, parseAmountInput } from '../../utils/format'
import { applicationKeys, createApplication } from './api'

// Client validation improves feedback; the backend remains the authoritative boundary.
const schema = z.object({
  customerId: z.string().trim().min(1, 'Zadejte název firemního klienta.').max(100, 'Název může obsahovat nejvýše 100 znaků.'),
  amount: z.number({ error: 'Zadejte požadovanou částku.' }).positive('Částka musí být vyšší než nula.').max(Number.MAX_SAFE_INTEGER),
  currency: z.enum(['CZK', 'EUR', 'USD']),
})
type FormValues = z.infer<typeof schema>

/** Creation command screen with schema-driven form state and explicit cache invalidation. */
export function NewApplicationPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { customerId: '', currency: 'CZK' } })
  const mutation = useMutation({
    mutationFn: createApplication,
    onSuccess: application => { void queryClient.invalidateQueries({ queryKey: applicationKeys.all }); void navigate(`/applications/${application.id}`, { state: { created: true } }) },
  })
  const problem = mutation.error instanceof ApiError ? mutation.error.problem : undefined

  return <div className="page form-page">
    <Link to="/applications" className="back-link"><ArrowLeft size={17} /> Zpět na přehled</Link>
    <div className="form-layout">
      <section className="panel form-card">
        <div className="step-label"><span>1</span> Nová žádost</div>
        <h1>Základní údaje o úvěru</h1><p className="lead">Založte žádost pro firemního klienta. Po uložení proběhne předběžná automatická kontrola.</p>
        {mutation.isError && <div className="inline-alert"><Info size={19} /><div><strong>Žádost se nepodařilo uložit</strong><span>{mutation.error.message}</span>{problem?.correlationId && <small>Referenční ID: {problem.correlationId}</small>}</div></div>}
        <form onSubmit={event => void form.handleSubmit(values => mutation.mutate(values))(event)} noValidate>
          <label className="field"><span>Název firemního klienta <i>*</i></span><input {...form.register('customerId')} placeholder="např. Labe Engineering s.r.o." aria-invalid={!!form.formState.errors.customerId} />{form.formState.errors.customerId && <small>{form.formState.errors.customerId.message}</small>}</label>
          <div className="field-row"><label className="field amount-field"><span>Požadovaná částka <i>*</i></span><Controller control={form.control} name="amount" render={({ field }) => <input ref={field.ref} name={field.name} value={formatAmountInput(field.value)} onBlur={field.onBlur} onChange={event => field.onChange(parseAmountInput(event.target.value))} inputMode="numeric" placeholder="2 500 000" aria-invalid={!!form.formState.errors.amount} />} />{form.formState.errors.amount && <small>{form.formState.errors.amount.message}</small>}</label><label className="field currency-field"><span>Měna <i>*</i></span><select {...form.register('currency')}><option>CZK</option><option>EUR</option><option>USD</option></select></label></div>
          <div className="form-info"><ShieldCheck size={20} /><span><strong>Ochrana proti duplicitnímu založení</strong><small>Opakované odeslání stejného požadavku nevytvoří další žádost.</small></span></div>
          <div className="form-actions"><Link to="/applications" className="button secondary">Zrušit</Link><button className="button primary" disabled={mutation.isPending}>{mutation.isPending ? 'Ukládám…' : <>Založit žádost <ArrowRight size={18} /></>}</button></div>
        </form>
      </section>
      <aside className="process-card"><p className="eyebrow">Proces zpracování</p><h2>Co bude následovat</h2><ol><li className="active"><span><Check size={15} /></span><div><strong>Založení žádosti</strong><small>Základní parametry úvěru</small></div></li><li><span>2</span><div><strong>Předběžná automatická kontrola</strong><small>Validační a procesní kontrola</small></div></li><li><span>3</span><div><strong>Posouzení specialistou</strong><small>Kontrola úvěrovým poradcem</small></div></li><li><span>4</span><div><strong>Rozhodnutí</strong><small>Schválení nebo zamítnutí</small></div></li></ol><div className="process-note"><Info size={18} /><span>Jde o demo prostředí bez reálných klientských dat.</span></div></aside>
    </div>
  </div>
}
