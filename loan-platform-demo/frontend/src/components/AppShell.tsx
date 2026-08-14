import { Bell, ChevronDown, Landmark, LogOut, Menu, Search } from 'lucide-react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'

/** Persistent operations workspace shared by all loan-management routes. */
export function AppShell() {
  const navigate = useNavigate()
  return (
    <div className="app-shell">
      <header className="topbar">
        <button className="brand" onClick={() => void navigate('/applications')} aria-label="LoanFlow domů">
          <span className="brand-mark"><Landmark size={23} strokeWidth={2.4} /></span>
          <span><strong>LoanFlow</strong><small>Firemní úvěry</small></span>
        </button>
        <div className="topbar-context"><span className="environment-dot" /> Interní pracovní prostředí</div>
        <div className="topbar-actions">
          <button className="icon-button" aria-label="Vyhledat"><Search size={18} /></button>
          <button className="icon-button has-notice" aria-label="Oznámení"><Bell size={18} /></button>
          <button className="user-menu"><span className="avatar">MK</span><span className="user-copy"><strong>Martin K.</strong><small>Úvěrový specialista</small></span><ChevronDown size={15} /></button>
          <button className="icon-button desktop-only" aria-label="Odhlásit"><LogOut size={18} /></button>
        </div>
      </header>
      <nav className="primary-nav" aria-label="Hlavní navigace">
        <button className="mobile-menu" aria-label="Otevřít navigaci"><Menu size={20} /></button>
        <NavLink to="/applications" end>Žádosti</NavLink>
        <NavLink to="/applications/new">Nová žádost</NavLink>
        <span className="nav-spacer" />
        <span className="system-state"><span /> Systémy dostupné</span>
      </nav>
      <main className="workspace"><Outlet /></main>
      <footer><span>LoanFlow • Portfolio demonstrátor</span><span>API v1 · Doručení alespoň jednou</span></footer>
    </div>
  )
}
