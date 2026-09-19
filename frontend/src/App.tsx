import { useEffect } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppShell } from '@/layouts/AppShell';
import { LoginPage } from '@/pages/LoginPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { PlaceholderPage } from '@/pages/PlaceholderPage';
import { FinancialPage } from '@/pages/FinancialPage';
import { DevicesPage } from '@/pages/DevicesPage';
import { NewDevicePage } from '@/pages/NewDevicePage';
import { DeviceDetailPage } from '@/pages/DeviceDetailPage';
import { CatalogsPage } from '@/pages/CatalogsPage';
import { MaintenanceFormPage } from '@/pages/MaintenanceFormPage';
import { MaintenanceDetailPage } from '@/pages/MaintenanceDetailPage';
import { SaleFormPage } from '@/pages/SaleFormPage';
import { SaleDetailPage } from '@/pages/SaleDetailPage';
import { ProtectedRoute, PublicRoute } from '@/routes/guards';
import { useAuth } from '@/features/auth/context';

export function App() {
  const location = useLocation();
  const { status } = useAuth();
  useEffect(() => {
    const heading = document.querySelector<HTMLElement>('#main-content h1');
    heading?.focus();
    document.title = `${heading?.textContent ?? 'Acesso'} · Delarte Control`;
  }, [location.pathname, status]);
  return (
    <Routes>
      <Route element={<PublicRoute />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/devices" element={<DevicesPage />} />
          <Route path="/devices/new" element={<NewDevicePage />} />
          <Route path="/devices/import" element={<NewDevicePage />} />
          <Route path="/devices/:id" element={<DeviceDetailPage />} />
          <Route
            path="/devices/:deviceId/maintenances/new"
            element={<MaintenanceFormPage origin="operational" />}
          />
          <Route
            path="/devices/:deviceId/maintenances/import"
            element={<MaintenanceFormPage origin="initial-import" />}
          />
          <Route
            path="/devices/:deviceId/maintenances/:maintenanceId"
            element={<MaintenanceDetailPage />}
          />
          <Route path="/devices/:deviceId/sale/new" element={<SaleFormPage />} />
          <Route path="/devices/:deviceId/sale" element={<SaleDetailPage />} />
          <Route path="/settings/catalogs" element={<CatalogsPage />} />
          <Route path="/financial" element={<FinancialPage />} />
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
