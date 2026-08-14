import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { ApplicationDetailPage } from './features/applications/ApplicationDetailPage'
import { ApplicationsPage } from './features/applications/ApplicationsPage'
import { NewApplicationPage } from './features/applications/NewApplicationPage'

/** Declarative route boundary; feature pages own data loading while the shell owns persistent chrome. */
export function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/applications" replace />} />
        <Route path="applications" element={<ApplicationsPage />} />
        <Route path="applications/new" element={<NewApplicationPage />} />
        <Route path="applications/:applicationId" element={<ApplicationDetailPage />} />
        <Route path="*" element={<Navigate to="/applications" replace />} />
      </Route>
    </Routes>
  )
}
