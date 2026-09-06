import { useEffect } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from '@/layouts/AppShell';
import { LoginPage } from '@/pages/LoginPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { PlaceholderPage } from '@/pages/PlaceholderPage';
import { ProtectedRoute, PublicRoute } from '@/routes/guards';

export function App() {
  const location = useLocation();
  useEffect(() => {
    const heading = document.querySelector<HTMLElement>('#main-content h1');
    heading?.focus();
    document.title = `${heading?.textContent ?? 'Acesso'} · iPhone Resale`;
  }, [location.pathname]);
  return (
    <Routes>
      <Route element={<PublicRoute />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route
            path="/devices"
            element={
              <PlaceholderPage
                title="Aparelhos"
                description="Um lugar para acompanhar cada iPhone da sua operação."
              />
            }
          />
          <Route
            path="/devices/new"
            element={
              <PlaceholderPage
                title="Novo aparelho"
                description="O início de uma nova jornada para cada iPhone."
              />
            }
          />
          <Route
            path="/devices/:id"
            element={
              <PlaceholderPage
                title="Detalhes do aparelho"
                description="Todos os detalhes, em um só lugar."
              />
            }
          />
          <Route
            path="/financial"
            element={
              <PlaceholderPage
                title="Financeiro"
                description="Clareza sobre os próximos passos da sua operação."
              />
            }
          />
          <Route
            path="/history"
            element={
              <PlaceholderPage title="Histórico" description="Cada movimento tem uma história." />
            }
          />
          <Route
            path="/settings/users"
            element={
              <PlaceholderPage
                title="Usuários"
                description="As pessoas que fazem a operação acontecer."
              />
            }
          />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
