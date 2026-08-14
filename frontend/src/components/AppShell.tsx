import { Landmark, Menu } from 'lucide-react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'

/** Persistent operations workspace shared by all loan-management routes. */
export function AppShell() {
  const navigate = useNavigate()
  return (
    <div className="app-shell">
      <header className="topbar">
        <button className="brand" onClick={() => void navigate('/applications')} aria-label="DevBank domů">
          <span className="brand-mark"><Landmark size={23} strokeWidth={2.4} /></span>
          <span><strong>DevBank</strong><small>KORPORÁTNÍ ÚVĚRY</small></span>
        </button>
        <div className="topbar-context"><span className="environment-dot" /> DEMO PROSTŘEDÍ</div>
        <div className="topbar-actions">
          <div className="user-menu"><span className="avatar">MZ</span><span className="user-copy"><strong>Matouš Zátka</strong><small>Úvěrový poradce</small></span></div>
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
    </div>
  )
}
