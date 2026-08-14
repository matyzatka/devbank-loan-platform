import { AlertCircle, FileSearch, LoaderCircle } from 'lucide-react'

/** Consistent asynchronous states keep layout and recovery behaviour predictable across features. */
export function LoadingState({ label = 'Načítám data…' }: { label?: string }) {
  return <div className="feedback"><LoaderCircle className="spin" /><strong>{label}</strong><span>Chvilku strpení.</span></div>
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return <div className="feedback error"><AlertCircle /><strong>Něco se nepovedlo</strong><span>{message}</span>{onRetry && <button className="button secondary" onClick={onRetry}>Zkusit znovu</button>}</div>
}

export function EmptyState() {
  return <div className="feedback"><FileSearch /><strong>Žádné žádosti neodpovídají filtru</strong><span>Upravte hledání nebo založte novou žádost.</span></div>
}
