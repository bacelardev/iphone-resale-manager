import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import {
  IconArchive,
  IconArrowLeft,
  IconCheck,
  IconEdit,
  IconPhotoPlus,
  IconTrash,
  IconX,
} from '@tabler/icons-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/error-state';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { CatalogPicker } from '@/features/devices/CatalogPicker';
import { MaintenancesSection } from '@/features/maintenances/MaintenancesSection';
import {
  addDevicePhoto,
  archiveDevice,
  changeDeviceStatus,
  getDevice,
  listColors,
  listModels,
  removeDevicePhoto,
  updateDevice,
} from '@/features/devices/api';
import { apiAssetUrl } from '@/lib/api/client';
import type { DeviceDetail, DevicePhoto, DeviceStatus } from '@/types/stage-g';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' });
const statusLabel: Record<DeviceStatus, string> = {
  PENDENTE_MANUTENCAO: 'Pendente de manutenção',
  DISPONIVEL_VENDA: 'Disponível para venda',
  VENDIDO: 'Vendido',
};

export function DeviceDetailPage() {
  const { id = '' } = useParams();
  const client = useQueryClient();
  const device = useQuery({
    queryKey: ['device', id],
    queryFn: () => getDevice(id),
    enabled: !!id,
  });
  const [editing, setEditing] = useState(false);
  const [archiving, setArchiving] = useState(false);
  const [lightbox, setLightbox] = useState<DevicePhoto>();
  const [reason, setReason] = useState('');
  const status = useMutation({
    mutationFn: ({
      value,
      action,
    }: {
      value: DeviceDetail;
      action: 'mark-pending-maintenance' | 'mark-available';
    }) => changeDeviceStatus(value, action),
    onSuccess: (value) => client.setQueryData(['device', id], value),
  });
  const archive = useMutation({
    mutationFn: (value: DeviceDetail) => archiveDevice(value, reason),
    onSuccess: (value) => {
      client.setQueryData(['device', id], value);
      setArchiving(false);
      setReason('');
    },
  });

  if (device.isLoading) return <p className="loading-copy">Carregando aparelho…</p>;
  if (device.error) return <ErrorState error={device.error} retry={() => void device.refetch()} />;
  if (!device.data) return null;
  const value = device.data;

  return (
    <div className="device-detail-page">
      <Button asChild variant="ghost" className="back-button">
        <Link to="/devices">
          <IconArrowLeft size={18} aria-hidden /> Voltar
        </Link>
      </Button>
      <PageHeader
        eyebrow={value.internalCode}
        title={value.model.name}
        description={`${value.storageGb} GB · ${value.color.name}`}
        action={
          <div className="page-actions">
            <Badge>{statusLabel[value.status]}</Badge>
            {!value.archived && (
              <Button variant="secondary" onClick={() => setEditing((current) => !current)}>
                {editing ? <IconX size={18} aria-hidden /> : <IconEdit size={18} aria-hidden />}
                {editing ? 'Fechar edição' : 'Editar'}
              </Button>
            )}
          </div>
        }
      />

      {value.archived && (
        <div className="archived-notice">Este aparelho está arquivado e permanece imutável.</div>
      )}
      {editing && (
        <DeviceEditForm
          device={value}
          onCancel={() => setEditing(false)}
          onSaved={(updated) => {
            client.setQueryData(['device', id], updated);
            setEditing(false);
          }}
        />
      )}

      <div className="device-detail-layout">
        <Card className="photo-gallery">
          <div className="detail-section-heading">
            <div>
              <p className="eyebrow">FOTOS</p>
              <h2>Visão do aparelho</h2>
            </div>
            {!value.archived && value.photos.length < 4 && <AddPhoto device={value} />}
          </div>
          <div className="photo-grid">
            {value.photos.map((photo) => (
              <figure key={photo.id}>
                <button
                  type="button"
                  onClick={() => setLightbox(photo)}
                  aria-label={`Ampliar foto ${photo.position}`}
                >
                  <img
                    src={apiAssetUrl(photo.url)}
                    alt={`Foto ${photo.position} do ${value.model.name}`}
                  />
                </button>
                {!value.archived && value.photos.length > 2 && (
                  <RemovePhoto device={value} photo={photo} />
                )}
              </figure>
            ))}
          </div>
        </Card>

        <div className="device-detail-stack">
          <Card className="detail-card">
            <p className="eyebrow">INVESTIMENTO</p>
            <dl className="detail-list">
              <Detail label="Compra" value={money.format(value.purchasePrice)} />
              <Detail label="Manutenção" value={money.format(value.maintenanceTotal)} />
              <Detail label="Total investido" value={money.format(value.investmentTotal)} strong />
              <Detail label="Comprado em" value={dateTime.format(new Date(value.purchasedAt))} />
            </dl>
          </Card>
          <Card className="detail-card">
            <p className="eyebrow">CONDIÇÃO</p>
            <dl className="detail-list">
              <Detail label="Face ID" value={yesNo(value.faceIdWorking)} />
              <Detail label="Tela original" value={yesNo(value.originalScreen)} />
              <Detail label="Bateria original" value={yesNo(value.originalBattery)} />
              <Detail
                label="Saúde da bateria"
                value={
                  value.batteryHealthPercent === null
                    ? 'Não aferida'
                    : `${value.batteryHealthPercent}%`
                }
              />
            </dl>
          </Card>
          <Card className="detail-card">
            <p className="eyebrow">RASTREABILIDADE</p>
            <dl className="detail-list">
              <Detail
                label="Origem"
                value={
                  value.registrationOrigin === 'INITIAL_IMPORT'
                    ? 'Estoque inicial'
                    : 'Compra operacional'
                }
              />
              <Detail label="Criado por" value={value.createdBy.name} />
              <Detail label="Atualizado por" value={value.updatedBy.name} />
              <Detail label="Versão" value={String(value.version)} />
            </dl>
          </Card>
        </div>
      </div>

      <MaintenancesSection device={value} />

      {!value.archived && (
        <Card className="device-actions-card">
          <div>
            <p className="eyebrow">AÇÕES DO APARELHO</p>
            <h2>Próximas decisões</h2>
          </div>
          <div className="page-actions">
            {value.status === 'PENDENTE_MANUTENCAO' && (
              <Button
                variant="secondary"
                disabled={status.isPending}
                onClick={() => status.mutate({ value, action: 'mark-available' })}
              >
                <IconCheck size={18} aria-hidden /> Marcar disponível
              </Button>
            )}
            {value.status === 'DISPONIVEL_VENDA' && (
              <Button
                variant="secondary"
                disabled={status.isPending}
                onClick={() => status.mutate({ value, action: 'mark-pending-maintenance' })}
              >
                Marcar pendente
              </Button>
            )}
            {value.status !== 'VENDIDO' && (
              <Button variant="ghost" onClick={() => setArchiving(true)}>
                <IconArchive size={18} aria-hidden /> Arquivar
              </Button>
            )}
          </div>
          {status.error && <ErrorState error={status.error} />}
        </Card>
      )}

      <Dialog open={archiving} onOpenChange={setArchiving}>
        <DialogContent
          title="Arquivar aparelho"
          description="Esta ação é terminal no MVP. O aparelho continuará disponível apenas no histórico."
        >
          <form
            className="modal-form"
            onSubmit={(event) => {
              event.preventDefault();
              if (reason.trim()) archive.mutate(value);
            }}
          >
            <FormField id="archive-reason" label="Motivo">
              <Input
                id="archive-reason"
                value={reason}
                maxLength={500}
                onChange={(event) => setReason(event.target.value)}
                required
              />
            </FormField>
            {archive.error && <ErrorState error={archive.error} />}
            <Button type="submit" disabled={archive.isPending || !reason.trim()}>
              Confirmar arquivamento
            </Button>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!lightbox} onOpenChange={(open) => !open && setLightbox(undefined)}>
        <DialogContent title="Foto do aparelho" description={`Posição ${lightbox?.position ?? ''}`}>
          {lightbox && (
            <img
              className="lightbox-image"
              src={apiAssetUrl(lightbox.url)}
              alt={`Foto ampliada ${lightbox.position}`}
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function DeviceEditForm({
  device,
  onSaved,
  onCancel,
}: {
  device: DeviceDetail;
  onSaved: (device: DeviceDetail) => void;
  onCancel: () => void;
}) {
  const models = useQuery({ queryKey: ['models', 'active'], queryFn: () => listModels(true) });
  const colors = useQuery({ queryKey: ['colors', 'active'], queryFn: () => listColors(true) });
  const [modelId, setModelId] = useState(device.model.id);
  const [colorId, setColorId] = useState(device.color.id);
  const [storage, setStorage] = useState(String(device.storageGb));
  const [price, setPrice] = useState(String(device.purchasePrice));
  const [purchasedAt, setPurchasedAt] = useState(toLocalInput(device.purchasedAt));
  const [batteryHealth, setBatteryHealth] = useState(device.batteryHealthPercent?.toString() ?? '');
  const [faceId, setFaceId] = useState(device.faceIdWorking);
  const [screen, setScreen] = useState(device.originalScreen);
  const [battery, setBattery] = useState(device.originalBattery);
  const update = useMutation({
    mutationFn: () =>
      updateDevice(device.id, {
        expectedVersion: device.version,
        modelId,
        colorId,
        storageGb: Number(storage),
        purchasePrice: Number(price),
        purchasedAt: new Date(purchasedAt).toISOString(),
        faceIdWorking: faceId,
        originalScreen: screen,
        originalBattery: battery,
        batteryHealthPercent: batteryHealth ? Number(batteryHealth) : 0,
      }),
    onSuccess: onSaved,
  });
  return (
    <Card className="edit-device-card">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          update.mutate();
        }}
      >
        <div className="form-grid">
          <CatalogPicker
            label="Modelo"
            items={models.data?.content ?? []}
            value={modelId}
            onChange={setModelId}
          />
          <CatalogPicker
            label="Cor"
            items={colors.data?.content ?? []}
            value={colorId}
            onChange={setColorId}
          />
          <FormField id="edit-storage" label="Capacidade">
            <Select
              id="edit-storage"
              value={storage}
              onChange={(event) => setStorage(event.target.value)}
            >
              {[64, 128, 256, 512, 1024, 2048].map((value) => (
                <option key={value} value={value}>
                  {value} GB
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="edit-price" label="Preço de compra">
            <Input
              id="edit-price"
              type="number"
              min="0.01"
              step="0.01"
              value={price}
              onChange={(event) => setPrice(event.target.value)}
              required
            />
          </FormField>
          <FormField id="edit-purchased-at" label="Data de compra">
            <Input
              id="edit-purchased-at"
              type="datetime-local"
              value={purchasedAt}
              onChange={(event) => setPurchasedAt(event.target.value)}
              required
            />
          </FormField>
          <FormField id="edit-battery-health" label="Saúde da bateria">
            <Input
              id="edit-battery-health"
              type="number"
              min="1"
              max="100"
              value={batteryHealth}
              onChange={(event) => setBatteryHealth(event.target.value)}
            />
          </FormField>
          <fieldset className="condition-options">
            <legend>Itens verificados</legend>
            <label>
              <input
                type="checkbox"
                checked={faceId}
                onChange={(event) => setFaceId(event.target.checked)}
              />{' '}
              Face ID funcionando
            </label>
            <label>
              <input
                type="checkbox"
                checked={screen}
                onChange={(event) => setScreen(event.target.checked)}
              />{' '}
              Tela original
            </label>
            <label>
              <input
                type="checkbox"
                checked={battery}
                onChange={(event) => setBattery(event.target.checked)}
              />{' '}
              Bateria original
            </label>
          </fieldset>
        </div>
        {update.error && <ErrorState error={update.error} />}
        <div className="form-actions">
          <Button variant="secondary" onClick={onCancel}>
            Cancelar
          </Button>
          <Button type="submit" disabled={update.isPending}>
            {update.isPending ? 'Salvando…' : 'Salvar alterações'}
          </Button>
        </div>
      </form>
    </Card>
  );
}

function AddPhoto({ device }: { device: DeviceDetail }) {
  const client = useQueryClient();
  const occupied = new Set(device.photos.map((photo) => photo.position));
  const position = [1, 2, 3, 4].find((value) => !occupied.has(value)) ?? 4;
  const add = useMutation({
    mutationFn: (file: File) => addDevicePhoto(device.id, file, position),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['device', device.id] });
    },
  });
  return (
    <>
      <label className="button button-secondary" htmlFor="add-device-photo">
        <IconPhotoPlus size={18} aria-hidden /> {add.isPending ? 'Enviando…' : 'Adicionar foto'}
      </label>
      <input
        id="add-device-photo"
        className="visually-hidden"
        type="file"
        accept="image/jpeg,image/png,image/webp"
        disabled={add.isPending}
        onChange={(event) => {
          const selected = event.target.files?.[0];
          if (selected) add.mutate(selected);
          event.target.value = '';
        }}
      />
      {add.error && <ErrorState error={add.error} />}
    </>
  );
}

function RemovePhoto({ device, photo }: { device: DeviceDetail; photo: DevicePhoto }) {
  const client = useQueryClient();
  const remove = useMutation({
    mutationFn: () => removeDevicePhoto(device.id, photo.id),
    onSuccess: async () => client.invalidateQueries({ queryKey: ['device', device.id] }),
  });
  return (
    <Button
      className="remove-photo"
      variant="secondary"
      size="icon"
      disabled={remove.isPending}
      onClick={() => remove.mutate()}
      aria-label={`Remover foto ${photo.position}`}
    >
      <IconTrash size={17} aria-hidden />
    </Button>
  );
}

function Detail({
  label,
  value,
  strong = false,
}: {
  label: string;
  value: string;
  strong?: boolean;
}) {
  return (
    <div>
      <dt>{label}</dt>
      <dd className={strong ? 'detail-strong' : undefined}>{value}</dd>
    </div>
  );
}

function yesNo(value: boolean) {
  return value ? 'Sim' : 'Não';
}

function toLocalInput(value: string) {
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}
