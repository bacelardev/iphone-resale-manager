import { Navigate, Outlet } from 'react-router-dom';
import { Brand } from '@/components/Brand';
import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/ui/error-state';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/features/auth/context';

function SessionGate() {
  const { status, error, retry, logout } = useAuth();
  return (
    <main className="session-gate">
      <Brand />
      <div className="session-gate-card">
        {status === 'checking' ? (
          <div role="status" aria-label="Verificando sessão">
            <p>Preparando seu espaço…</p>
            <Skeleton />
            <Skeleton className="w-2/3" />
          </div>
        ) : (
          <>
            <h1>Vamos reconectar?</h1>
            <ErrorState error={error} retry={retry} />
            <Button
              variant="ghost"
              onClick={() => {
                void logout();
              }}
            >
              Sair desta aba
            </Button>
          </>
        )}
      </div>
    </main>
  );
}
export function ProtectedRoute() {
  const { status } = useAuth();
  if (status === 'checking' || status === 'error') return <SessionGate />;
  return status === 'authenticated' ? <Outlet /> : <Navigate to="/login" replace />;
}
export function PublicRoute() {
  const { status } = useAuth();
  if (status === 'checking' || status === 'error') return <SessionGate />;
  return status === 'authenticated' ? <Navigate to="/dashboard" replace /> : <Outlet />;
}
