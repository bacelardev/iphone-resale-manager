import { useQuery } from '@tanstack/react-query';
import { IconHistory, IconPlus, IconTool } from '@tabler/icons-react';
import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/error-state';
import { getInitialization } from '@/features/devices/api';
import { listMaintenances } from '@/features/maintenances/api';
import type { DeviceDetail } from '@/types/stage-g';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const date = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' });

export function MaintenancesSection({ device }: { device: DeviceDetail }) {
  const query = useQuery({
    queryKey: ['maintenances', device.id],
    queryFn: () => listMaintenances(device.id),
  });
  const activeSummary = useQuery({
    queryKey: ['maintenances', device.id, 'active-summary'],
    queryFn: () => listMaintenances(device.id, { status: 'ACTIVE', size: 1 }),
  });
  const initialization = useQuery({
    queryKey: ['business-initialization'],
    queryFn: getInitialization,
  });
  const canRegister = !device.archived && device.status !== 'VENDIDO';
  const canImport =
    canRegister &&
    device.registrationOrigin === 'INITIAL_IMPORT' &&
    initialization.data?.status === 'PREPARING';
  const activeCount = activeSummary.data?.totalElements;
  const activeLabel =
    activeCount === undefined
      ? 'Contando manutenções ativas…'
      : activeCount === 1
        ? '1 manutenção ativa'
        : `${activeCount} manutenções ativas`;
  return (
    <Card className="maintenances-card">
      <div className="detail-section-heading">
        <div>
          <p className="eyebrow">MANUTENÇÕES</p>
          <h2>Histórico técnico</h2>
          <p className="page-description">
            {activeLabel} · Total ativo: {money.format(device.maintenanceTotal)}
          </p>
        </div>
        {canRegister && (
          <div className="page-actions">
            {canImport && (
              <Button asChild variant="secondary">
                <Link to={`/devices/${device.id}/maintenances/import`}>
                  <IconHistory size={18} aria-hidden /> Importar histórica
                </Link>
              </Button>
            )}
            <Button asChild>
              <Link to={`/devices/${device.id}/maintenances/new`}>
                <IconPlus size={18} aria-hidden /> Nova manutenção
              </Link>
            </Button>
          </div>
        )}
      </div>
      {query.error && <ErrorState error={query.error} retry={() => void query.refetch()} />}
      {query.isLoading && <p className="loading-copy">Carregando manutenções…</p>}
      {query.data?.content.length === 0 && (
        <div className="maintenance-empty">
          <IconTool size={28} aria-hidden />
          <p>Nenhuma manutenção registrada para este aparelho.</p>
        </div>
      )}
      <ul className="maintenance-list">
        {query.data?.content.map((maintenance) => (
          <li key={maintenance.id}>
            <Link to={`/devices/${device.id}/maintenances/${maintenance.id}`}>
              <span>
                <strong>{date.format(new Date(maintenance.performedAt))}</strong>
                <small>
                  {maintenance.registrationOrigin === 'INITIAL_IMPORT'
                    ? 'Custo histórico'
                    : 'Manutenção operacional'}
                </small>
                <small>Responsável: {maintenance.responsibleUser.name}</small>
              </span>
              <strong>{money.format(maintenance.total)}</strong>
              <Badge positive={maintenance.status === 'ACTIVE'}>
                {maintenance.status === 'ACTIVE' ? 'Ativa' : 'Cancelada'}
              </Badge>
            </Link>
          </li>
        ))}
      </ul>
    </Card>
  );
}
