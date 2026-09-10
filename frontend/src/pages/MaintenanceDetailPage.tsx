import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { IconArrowLeft, IconBan } from '@tabler/icons-react';
import { Link, useParams } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/error-state';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { cancelMaintenance, getMaintenance } from '@/features/maintenances/api';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const date = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeStyle: 'short' });

export function MaintenanceDetailPage() {
  const { deviceId = '', maintenanceId = '' } = useParams();
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['maintenance', deviceId, maintenanceId],
    queryFn: () => getMaintenance(deviceId, maintenanceId),
  });
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const cancellation = useMutation({
    mutationFn: () => cancelMaintenance(query.data!, reason),
    onSuccess: async (value) => {
      client.setQueryData(['maintenance', deviceId, maintenanceId], value);
      await Promise.all([
        client.invalidateQueries({ queryKey: ['device', deviceId] }),
        client.invalidateQueries({ queryKey: ['maintenances', deviceId] }),
        client.invalidateQueries({ queryKey: ['business-initialization-preview'] }),
      ]);
      setOpen(false);
      setReason('');
    },
  });
  if (query.isLoading) return <p className="loading-copy">Carregando manutenção…</p>;
  if (query.error) return <ErrorState error={query.error} retry={() => void query.refetch()} />;
  if (!query.data) return null;
  const maintenance = query.data;
  const impact =
    maintenance.financialImpact === 'OUTFLOW_CREATED'
      ? 'Saída financeira gerada'
      : maintenance.financialImpact === 'HISTORICAL_COST_ONLY'
        ? 'Custo histórico — sem nova saída de caixa'
        : 'Manutenção sem custo financeiro';

  function submit(event: FormEvent) {
    event.preventDefault();
    if (reason.trim()) cancellation.mutate();
  }

  return (
    <div className="maintenance-detail-page">
      <Button asChild variant="ghost" className="back-button">
        <Link to={`/devices/${deviceId}`}>
          <IconArrowLeft size={18} aria-hidden /> Voltar ao aparelho
        </Link>
      </Button>
      <PageHeader
        eyebrow={maintenance.internalCode}
        title="Detalhes da manutenção"
        description={date.format(new Date(maintenance.performedAt))}
        action={
          <Badge positive={maintenance.status === 'ACTIVE'}>
            {maintenance.status === 'ACTIVE' ? 'Ativa' : 'Cancelada'}
          </Badge>
        }
      />
      <div className="maintenance-detail-layout">
        <Card className="detail-card">
          <p className="eyebrow">RESUMO</p>
          <dl className="detail-list">
            <Detail label="Aparelho" value={maintenance.internalCode} />
            <Detail label="Responsável" value={maintenance.responsibleUser.name} />
            <Detail
              label="Origem"
              value={
                maintenance.registrationOrigin === 'INITIAL_IMPORT'
                  ? 'Importação histórica'
                  : 'Operacional'
              }
            />
            <Detail label="Total" value={money.format(maintenance.total)} strong />
            <Detail label="Impacto financeiro" value={impact} />
          </dl>
        </Card>
        <Card className="maintenance-detail-items">
          <p className="eyebrow">ITENS</p>
          <ul>
            {maintenance.items.map((item) => (
              <li key={item.id}>
                <span>
                  <strong>{item.part.name}</strong>
                  <small>{item.details || item.part.code}</small>
                </span>
                <strong>{money.format(item.cost)}</strong>
              </li>
            ))}
          </ul>
        </Card>
      </div>
      {maintenance.status === 'CANCELLED' && (
        <div className="archived-notice">
          Cancelada em {date.format(new Date(maintenance.cancelledAt!))}:{' '}
          {maintenance.cancellationReason}
        </div>
      )}
      {maintenance.status === 'ACTIVE' && (
        <Card className="device-actions-card">
          <div>
            <p className="eyebrow">CORREÇÃO</p>
            <h2>Cancelar manutenção</h2>
          </div>
          <Button variant="ghost" onClick={() => setOpen(true)}>
            <IconBan size={18} aria-hidden /> Cancelar lançamento
          </Button>
        </Card>
      )}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          title="Cancelar manutenção"
          description={`${maintenance.internalCode} · ${money.format(maintenance.total)} · ${impact}`}
          placement="bottom-sheet"
        >
          <form className="modal-form" onSubmit={submit}>
            <FormField id="maintenance-cancel-reason" label="Motivo">
              <Input
                id="maintenance-cancel-reason"
                value={reason}
                maxLength={500}
                onChange={(event) => setReason(event.target.value)}
                required
              />
            </FormField>
            {cancellation.error && <ErrorState error={cancellation.error} />}
            <Button type="submit" disabled={cancellation.isPending || !reason.trim()}>
              {cancellation.isPending ? 'Cancelando…' : 'Confirmar cancelamento'}
            </Button>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Detail({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{strong ? <strong>{value}</strong> : value}</dd>
    </div>
  );
}
