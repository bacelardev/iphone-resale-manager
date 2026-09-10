import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { IconArrowLeft, IconPlus, IconTrash } from '@tabler/icons-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/error-state';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { getDevice, getInitialization, listParts } from '@/features/devices/api';
import { registerMaintenance } from '@/features/maintenances/api';
import { PartPicker } from '@/features/maintenances/PartPicker';
import { nowAsLocalDateTimeValue, toLocalDateTimeValue } from '@/lib/date/local-datetime';

type FormItem = { key: number; partId: string; details: string; cost: string };
const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' });

export function MaintenanceFormPage({ origin }: { origin: 'operational' | 'initial-import' }) {
  const { deviceId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const device = useQuery({ queryKey: ['device', deviceId], queryFn: () => getDevice(deviceId) });
  const parts = useQuery({ queryKey: ['parts', 'active'], queryFn: () => listParts(true) });
  const initialization = useQuery({
    queryKey: ['business-initialization'],
    queryFn: getInitialization,
  });
  const [performedAt, setPerformedAt] = useState(nowAsLocalDateTimeValue());
  const [nextKey, setNextKey] = useState(2);
  const [items, setItems] = useState<FormItem[]>([
    { key: 1, partId: '', details: '', cost: '0.00' },
  ]);
  const total = useMemo(
    () => items.reduce((sum, item) => sum + (Number(item.cost) || 0), 0),
    [items],
  );
  const mutation = useMutation({
    mutationFn: () =>
      registerMaintenance(
        deviceId,
        {
          performedAt: new Date(performedAt).toISOString(),
          items: items.map((item) => ({
            partId: item.partId,
            details: item.details.trim() || null,
            cost: Number(item.cost),
          })),
        },
        origin,
      ),
    onSuccess: async (maintenance) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['device', deviceId] }),
        queryClient.invalidateQueries({ queryKey: ['maintenances', deviceId] }),
        queryClient.invalidateQueries({ queryKey: ['business-initialization-preview'] }),
      ]);
      navigate(`/devices/${deviceId}/maintenances/${maintenance.id}`);
    },
  });
  const availableParts = parts.data?.content ?? [];
  const historical = origin === 'initial-import';
  const eligibilityError = historical
    ? device.data?.registrationOrigin !== 'INITIAL_IMPORT'
      ? 'Somente aparelhos do estoque inicial aceitam importação histórica.'
      : initialization.data?.status !== 'PREPARING'
        ? 'A importação histórica exige uma implantação em preparação.'
        : null
    : null;

  function updateItem(key: number, values: Partial<FormItem>) {
    setItems((current) =>
      current.map((item) => (item.key === key ? { ...item, ...values } : item)),
    );
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!eligibilityError && items.every((item) => item.partId)) mutation.mutate();
  }

  if (device.isLoading || parts.isLoading || initialization.isLoading) {
    return <p className="loading-copy">Preparando formulário…</p>;
  }
  if (device.error || parts.error || initialization.error) {
    return <ErrorState error={device.error ?? parts.error ?? initialization.error} />;
  }
  if (!device.data) return null;

  return (
    <div className="maintenance-form-page">
      <Button asChild variant="ghost" className="back-button">
        <Link to={`/devices/${deviceId}`}>
          <IconArrowLeft size={18} aria-hidden /> Voltar ao aparelho
        </Link>
      </Button>
      <PageHeader
        eyebrow={device.data.internalCode}
        title={historical ? 'Importar manutenção histórica' : 'Registrar manutenção'}
        description={`${device.data.model.name} · ${device.data.storageGb} GB`}
      />
      {historical && (
        <div className="historical-warning" role="note">
          <strong>Importação histórica</strong>
          <p>
            Use esta opção apenas para manutenções que já tinham ocorrido até a data de corte. O
            custo será incorporado ao investimento histórico do aparelho, mas não será criada uma
            nova saída de caixa.
          </p>
          <dl>
            <div>
              <dt>Data de compra</dt>
              <dd>{dateTime.format(new Date(device.data.purchasedAt))}</dd>
            </div>
            <div>
              <dt>Data de corte</dt>
              <dd>
                {initialization.data?.cutoffAt
                  ? dateTime.format(new Date(initialization.data.cutoffAt))
                  : 'Não definida'}
              </dd>
            </div>
          </dl>
        </div>
      )}
      {eligibilityError && <div className="archived-notice">{eligibilityError}</div>}
      {availableParts.length === 0 ? (
        <Card className="maintenance-empty catalog-empty-cta">
          <p>Cadastre ao menos uma peça antes de registrar a manutenção.</p>
          <Button asChild>
            <Link to="/settings/catalogs">Gerenciar peças</Link>
          </Button>
        </Card>
      ) : (
        <form className="maintenance-form" onSubmit={submit}>
          <Card className="maintenance-meta-card">
            <FormField id="maintenance-performed-at" label="Data da manutenção">
              <Input
                id="maintenance-performed-at"
                type="datetime-local"
                value={performedAt}
                min={toLocalDateTimeValue(new Date(device.data.purchasedAt))}
                max={
                  historical && initialization.data?.cutoffAt
                    ? toLocalDateTimeValue(new Date(initialization.data.cutoffAt))
                    : undefined
                }
                onChange={(event) => setPerformedAt(event.target.value)}
                required
              />
            </FormField>
            <div className="maintenance-total-preview">
              <span>Total calculado</span>
              <strong>{money.format(total)}</strong>
              <small>O backend recalcula e valida este valor.</small>
            </div>
          </Card>
          <div className="maintenance-items">
            {items.map((item, index) => {
              const selected = availableParts.find((part) => part.id === item.partId);
              return (
                <Card className="maintenance-item-card" key={item.key}>
                  <div className="maintenance-item-heading">
                    <h2>Item {index + 1}</h2>
                    {items.length > 1 && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label={`Remover item ${index + 1}`}
                        onClick={() =>
                          setItems((current) => current.filter((value) => value.key !== item.key))
                        }
                      >
                        <IconTrash size={18} aria-hidden />
                      </Button>
                    )}
                  </div>
                  <div className="form-grid">
                    <PartPicker
                      items={availableParts}
                      value={item.partId}
                      onChange={(partId) => updateItem(item.key, { partId })}
                    />
                    <FormField id={`maintenance-cost-${item.key}`} label="Custo">
                      <Input
                        id={`maintenance-cost-${item.key}`}
                        type="number"
                        min="0"
                        step="0.01"
                        value={item.cost}
                        onChange={(event) => updateItem(item.key, { cost: event.target.value })}
                        required
                      />
                    </FormField>
                    <FormField
                      id={`maintenance-details-${item.key}`}
                      label={selected?.code === 'OTHER' ? 'Detalhes obrigatórios' : 'Detalhes'}
                    >
                      <Input
                        id={`maintenance-details-${item.key}`}
                        value={item.details}
                        maxLength={255}
                        onChange={(event) => updateItem(item.key, { details: event.target.value })}
                        required={selected?.code === 'OTHER'}
                      />
                    </FormField>
                  </div>
                </Card>
              );
            })}
          </div>
          <div className="maintenance-form-actions">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setItems((current) => [
                  ...current,
                  { key: nextKey, partId: '', details: '', cost: '0.00' },
                ]);
                setNextKey((value) => value + 1);
              }}
            >
              <IconPlus size={18} aria-hidden /> Adicionar item
            </Button>
            <Button
              type="submit"
              disabled={
                mutation.isPending || !!eligibilityError || items.some((item) => !item.partId)
              }
            >
              {mutation.isPending
                ? 'Registrando…'
                : historical
                  ? 'Importar manutenção'
                  : 'Registrar manutenção'}
            </Button>
          </div>
          {mutation.error && <ErrorState error={mutation.error} />}
        </form>
      )}
    </div>
  );
}
