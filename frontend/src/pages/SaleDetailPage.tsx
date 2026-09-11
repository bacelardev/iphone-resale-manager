import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { IconArrowLeft, IconBan, IconCash } from '@tabler/icons-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/error-state';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { getDevice } from '@/features/devices/api';
import { cancelSale, getSale } from '@/features/sales/api';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const percent = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});
const date = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeStyle: 'short' });

export function SaleDetailPage() {
  const { deviceId = '' } = useParams();
  const navigate = useNavigate();
  const client = useQueryClient();
  const sale = useQuery({ queryKey: ['sale', deviceId], queryFn: () => getSale(deviceId) });
  const device = useQuery({ queryKey: ['device', deviceId], queryFn: () => getDevice(deviceId) });
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const cancellation = useMutation({
    mutationFn: () => cancelSale(sale.data!, device.data!.version, reason),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['sale', deviceId], refetchType: 'none' }),
        client.invalidateQueries({ queryKey: ['device', deviceId] }),
        client.invalidateQueries({ queryKey: ['devices'] }),
        client.invalidateQueries({ queryKey: ['financial'] }),
      ]);
      navigate(`/devices/${deviceId}`, { replace: true, state: { saleCancelled: true } });
    },
  });

  if (sale.isLoading || device.isLoading) return <p className="loading-copy">Carregando venda…</p>;
  if (sale.error) return <ErrorState error={sale.error} retry={() => void sale.refetch()} />;
  if (device.error) return <ErrorState error={device.error} retry={() => void device.refetch()} />;
  if (!sale.data || !device.data) return null;
  const value = sale.data;
  const loss = value.profit < 0;

  function submit(event: FormEvent) {
    event.preventDefault();
    if (reason.trim() && !cancellation.isPending) cancellation.mutate();
  }

  return (
    <div className="sale-detail-page">
      <Button asChild variant="ghost" className="back-button">
        <Link to={`/devices/${deviceId}`}>
          <IconArrowLeft size={18} aria-hidden /> Voltar ao aparelho
        </Link>
      </Button>
      <PageHeader
        eyebrow={device.data.internalCode}
        title="Detalhes da venda"
        description={date.format(new Date(value.soldAt))}
        action={<Badge positive>Ativa</Badge>}
      />
      <div className="sale-detail-layout">
        <Card className="detail-card">
          <p className="eyebrow">VENDA</p>
          <dl className="detail-list">
            <Detail label="Aparelho" value={device.data.internalCode} />
            <Detail label="Preço de venda" value={money.format(value.salePrice)} strong />
            <Detail label="Vendido em" value={date.format(new Date(value.soldAt))} />
            <Detail label="Registrado em" value={date.format(new Date(value.createdAt))} />
            <Detail label="Responsável" value={value.responsibleUser.name} />
            <Detail label="Entrada financeira" value="Entrada financeira gerada" />
          </dl>
        </Card>
        <Card className="detail-card">
          <p className="eyebrow">RESULTADO</p>
          <dl className="detail-list">
            <Detail label="Compra" value={money.format(value.purchasePrice)} />
            <Detail label="Manutenções" value={money.format(value.maintenanceTotal)} />
            <Detail label="Investimento" value={money.format(value.investmentTotal)} />
            <Detail
              label={loss ? 'Prejuízo' : 'Lucro'}
              value={money.format(value.profit)}
              strong
              tone={loss ? 'negative' : 'positive'}
            />
            <Detail
              label="Margem"
              value={`${percent.format(value.marginPercent)}%`}
              tone={loss ? 'negative' : 'positive'}
            />
          </dl>
        </Card>
      </div>
      <Dialog open={open} onOpenChange={setOpen}>
        <Card className="device-actions-card">
          <div>
            <p className="eyebrow">CORREÇÃO</p>
            <h2>Cancelar venda</h2>
          </div>
          <DialogTrigger>
            <Button variant="ghost" className="sale-cancel-button">
              <IconBan size={18} aria-hidden /> Cancelar venda
            </Button>
          </DialogTrigger>
        </Card>
        <DialogContent
          title="Cancelar venda"
          description={`${device.data.internalCode} · ${money.format(value.salePrice)}`}
          placement="bottom-sheet"
        >
          <div className="sale-cancel-summary">
            <IconCash size={22} aria-hidden />
            <p>
              O cancelamento estornará a entrada financeira da venda e devolverá o aparelho ao
              status Disponível para venda.
            </p>
          </div>
          <dl className="sale-cancel-values">
            <Detail label="Lucro registrado" value={money.format(value.profit)} />
            <Detail label="Data da venda" value={date.format(new Date(value.soldAt))} />
            <Detail label="Responsável" value={value.responsibleUser.name} />
          </dl>
          <form className="modal-form" onSubmit={submit}>
            <FormField id="sale-cancel-reason" label="Motivo">
              <Input
                id="sale-cancel-reason"
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

function Detail({
  label,
  value,
  strong,
  tone,
}: {
  label: string;
  value: string;
  strong?: boolean;
  tone?: 'positive' | 'negative';
}) {
  return (
    <div className={tone ? `sale-value-${tone}` : undefined}>
      <dt>{label}</dt>
      <dd>{strong ? <strong>{value}</strong> : value}</dd>
    </div>
  );
}
