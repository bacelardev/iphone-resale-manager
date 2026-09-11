import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { IconArrowLeft, IconReceipt } from '@tabler/icons-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/error-state';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { getDevice } from '@/features/devices/api';
import { calculateSalePreview } from '@/features/sales/calculations';
import { registerSale } from '@/features/sales/api';
import { nowAsLocalDateTimeValue, toLocalDateTimeValue } from '@/lib/date/local-datetime';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const percent = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function SaleFormPage() {
  const { deviceId = '' } = useParams();
  const navigate = useNavigate();
  const client = useQueryClient();
  const device = useQuery({
    queryKey: ['device', deviceId],
    queryFn: () => getDevice(deviceId),
    enabled: !!deviceId,
  });
  const [salePrice, setSalePrice] = useState('');
  const [soldAt, setSoldAt] = useState(nowAsLocalDateTimeValue());
  const numericPrice = Number(salePrice || 0);
  const preview = useMemo(
    () =>
      calculateSalePreview(
        device.data?.purchasePrice ?? 0,
        device.data?.maintenanceTotal ?? 0,
        numericPrice,
      ),
    [device.data?.purchasePrice, device.data?.maintenanceTotal, numericPrice],
  );
  const sale = useMutation({
    mutationFn: () =>
      registerSale(deviceId, {
        deviceVersion: device.data!.version,
        salePrice: numericPrice,
        soldAt: new Date(soldAt).toISOString(),
      }),
    onSuccess: async (value) => {
      client.setQueryData(['sale', deviceId], value);
      await Promise.all([
        client.invalidateQueries({ queryKey: ['device', deviceId] }),
        client.invalidateQueries({ queryKey: ['devices'] }),
        client.invalidateQueries({ queryKey: ['financial'] }),
      ]);
      navigate(`/devices/${deviceId}/sale`);
    },
  });

  if (device.isLoading) return <p className="loading-copy">Carregando aparelho…</p>;
  if (device.error) return <ErrorState error={device.error} retry={() => void device.refetch()} />;
  if (!device.data) return null;
  const value = device.data;
  const loss = preview.profit < 0;

  function submit(event: FormEvent) {
    event.preventDefault();
    if (numericPrice > 0 && soldAt && !sale.isPending) sale.mutate();
  }

  return (
    <div className="sale-form-page">
      <Button asChild variant="ghost" className="back-button">
        <Link to={`/devices/${deviceId}`}>
          <IconArrowLeft size={18} aria-hidden /> Voltar ao aparelho
        </Link>
      </Button>
      <PageHeader
        eyebrow={`${value.internalCode} · VENDA`}
        title="Registrar venda"
        description={`${value.model.name} · ${value.storageGb} GB · ${value.color.name}`}
      />
      <form className="sale-layout" onSubmit={submit}>
        <Card className="sale-form-card">
          <div className="detail-section-heading">
            <div>
              <p className="eyebrow">CONFIRMAÇÃO</p>
              <h2>Dados da venda</h2>
            </div>
            <IconReceipt size={24} aria-hidden />
          </div>
          <FormField id="sale-price" label="Preço de venda (R$)">
            <Input
              id="sale-price"
              type="number"
              min="0.01"
              step="0.01"
              value={salePrice}
              onChange={(event) => setSalePrice(event.target.value)}
              required
            />
          </FormField>
          <FormField id="sold-at" label="Data e hora da venda">
            <Input
              id="sold-at"
              type="datetime-local"
              min={toLocalDateTimeValue(new Date(value.purchasedAt))}
              value={soldAt}
              onChange={(event) => setSoldAt(event.target.value)}
              required
            />
          </FormField>
          {sale.error && <ErrorState error={sale.error} />}
          <Button type="submit" disabled={sale.isPending || numericPrice <= 0}>
            {sale.isPending ? 'Registrando…' : 'Confirmar venda'}
          </Button>
        </Card>
        <Card className="sale-summary-card" aria-live="polite">
          <p className="eyebrow">RESUMO FINANCEIRO</p>
          <dl className="sale-summary-list">
            <Summary label="Compra" value={money.format(value.purchasePrice)} />
            <Summary label="Manutenções" value={money.format(value.maintenanceTotal)} />
            <Summary label="Investimento" value={money.format(preview.investmentTotal)} strong />
            <Summary label="Venda" value={money.format(numericPrice)} />
            <Summary
              label={loss ? 'Prejuízo estimado' : 'Lucro estimado'}
              value={money.format(preview.profit)}
              tone={loss ? 'negative' : 'positive'}
              strong
            />
            <Summary
              label="Margem estimada"
              value={
                preview.marginPercent === null ? '—' : `${percent.format(preview.marginPercent)}%`
              }
              tone={loss ? 'negative' : 'positive'}
            />
          </dl>
          {loss && (
            <p className="sale-loss-warning">
              A venda com prejuízo é permitida. Revise os valores antes de confirmar.
            </p>
          )}
        </Card>
      </form>
    </div>
  );
}

function Summary({
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
