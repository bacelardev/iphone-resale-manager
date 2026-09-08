import { useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { IconArrowLeft, IconInfoCircle, IconPhoto, IconUpload } from '@tabler/icons-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/error-state';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { CatalogPicker } from '@/features/devices/CatalogPicker';
import { getInitialization, listColors, listModels, registerDevice } from '@/features/devices/api';
import type { RegisterDeviceInput } from '@/types/stage-g';

const MAX_PHOTO_BYTES = 10 * 1024 * 1024;

export function NewDevicePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const importing = location.pathname.endsWith('/import');
  const initialization = useQuery({
    queryKey: ['business-initialization'],
    queryFn: getInitialization,
  });
  const models = useQuery({ queryKey: ['models', 'active'], queryFn: () => listModels(true) });
  const colors = useQuery({ queryKey: ['colors', 'active'], queryFn: () => listColors(true) });
  const [modelId, setModelId] = useState('');
  const [colorId, setColorId] = useState('');
  const [storageGb, setStorageGb] = useState('128');
  const [purchasePrice, setPurchasePrice] = useState('');
  const [purchasedAt, setPurchasedAt] = useState('');
  const [batteryHealth, setBatteryHealth] = useState('');
  const [status, setStatus] = useState<'PENDENTE_MANUTENCAO' | 'DISPONIVEL_VENDA'>(
    'PENDENTE_MANUTENCAO',
  );
  const [faceId, setFaceId] = useState(true);
  const [screen, setScreen] = useState(true);
  const [battery, setBattery] = useState(true);
  const [photos, setPhotos] = useState<File[]>([]);
  const [formError, setFormError] = useState<string>();
  const create = useMutation({
    mutationFn: (input: RegisterDeviceInput) =>
      registerDevice(input, photos, importing ? 'initial-import' : 'operational'),
    onSuccess: (device) => navigate(`/devices/${device.id}`, { replace: true }),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setFormError(undefined);
    if (!modelId || !colorId) return setFormError('Selecione o modelo e a cor.');
    if (photos.length < 2 || photos.length > 4)
      return setFormError('Selecione de duas a quatro fotos.');
    if (
      photos.some(
        (photo) =>
          !['image/jpeg', 'image/png', 'image/webp'].includes(photo.type) ||
          photo.size > MAX_PHOTO_BYTES,
      )
    )
      return setFormError('As fotos devem ser JPEG, PNG ou WebP e ter até 10 MiB.');
    if (!purchasedAt || !purchasePrice || Number(purchasePrice) <= 0)
      return setFormError('Informe data e preço de compra válidos.');
    create.mutate({
      modelId,
      colorId,
      storageGb: Number(storageGb),
      purchasePrice: Number(purchasePrice),
      purchasedAt: new Date(purchasedAt).toISOString(),
      faceIdWorking: faceId,
      originalScreen: screen,
      originalBattery: battery,
      batteryHealthPercent: batteryHealth === '' ? 0 : Number(batteryHealth),
      initialStatus: status,
    });
  }

  if (importing && initialization.data?.status !== 'PREPARING') {
    return (
      <div className="device-form-page">
        <PageHeader
          eyebrow="ESTOQUE INICIAL"
          title="Preparação necessária"
          description="Defina uma data de corte antes de importar aparelhos existentes."
        />
        <Button asChild>
          <Link to="/devices">Voltar aos aparelhos</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="device-form-page">
      <Button asChild variant="ghost" className="back-button">
        <Link to="/devices">
          <IconArrowLeft size={18} aria-hidden /> Voltar
        </Link>
      </Button>
      <PageHeader
        eyebrow={importing ? 'ESTOQUE INICIAL' : 'COMPRA OPERACIONAL'}
        title={importing ? 'Importar aparelho existente' : 'Novo aparelho'}
        description={
          importing
            ? 'Preserve os dados de um iPhone que já fazia parte do estoque.'
            : 'Registre uma nova compra e sua saída financeira de forma atômica.'
        }
      />
      {importing && (
        <div className="import-notice">
          <IconInfoCircle size={20} aria-hidden />
          <p>
            Use esta opção apenas para aparelhos que já pertenciam ao estoque antes da data de
            início do controle. O custo será preservado, mas não será criada uma nova saída de
            caixa.
            {initialization.data?.cutoffAt && (
              <strong>
                {' '}
                Data de corte: {new Date(initialization.data.cutoffAt).toLocaleString('pt-BR')}.
              </strong>
            )}
          </p>
        </div>
      )}
      <form className="device-form" onSubmit={submit}>
        <Card className="form-section">
          <div className="form-section-heading">
            <span>01</span>
            <div>
              <h2>Identificação</h2>
              <p>Modelo, acabamento e capacidade.</p>
            </div>
          </div>
          <div className="form-grid">
            <CatalogPicker
              label="Modelo"
              items={models.data?.content ?? []}
              value={modelId}
              onChange={setModelId}
              disabled={models.isLoading}
            />
            <CatalogPicker
              label="Cor"
              items={colors.data?.content ?? []}
              value={colorId}
              onChange={setColorId}
              disabled={colors.isLoading}
            />
            <FormField id="storage" label="Capacidade">
              <Select
                id="storage"
                value={storageGb}
                onChange={(event) => setStorageGb(event.target.value)}
              >
                {[64, 128, 256, 512, 1024, 2048].map((value) => (
                  <option key={value} value={value}>
                    {value >= 1024 ? `${value / 1024} TB` : `${value} GB`}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField id="status" label="Status inicial">
              <Select
                id="status"
                value={status}
                onChange={(event) => setStatus(event.target.value as typeof status)}
              >
                <option value="PENDENTE_MANUTENCAO">Pendente de manutenção</option>
                <option value="DISPONIVEL_VENDA">Disponível para venda</option>
              </Select>
            </FormField>
          </div>
        </Card>

        <Card className="form-section">
          <div className="form-section-heading">
            <span>02</span>
            <div>
              <h2>Compra e condição</h2>
              <p>Dados econômicos e avaliação do aparelho.</p>
            </div>
          </div>
          <div className="form-grid">
            <FormField id="purchase-price" label="Preço de compra (R$)">
              <Input
                id="purchase-price"
                type="number"
                min="0.01"
                step="0.01"
                value={purchasePrice}
                onChange={(event) => setPurchasePrice(event.target.value)}
                required
              />
            </FormField>
            <FormField id="purchased-at" label="Data e hora da compra">
              <Input
                id="purchased-at"
                type="datetime-local"
                value={purchasedAt}
                onChange={(event) => setPurchasedAt(event.target.value)}
                required
              />
            </FormField>
            <FormField id="battery-health" label="Saúde da bateria (%) — vazio se não aferida">
              <Input
                id="battery-health"
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
                />
                Face ID funcionando
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={screen}
                  onChange={(event) => setScreen(event.target.checked)}
                />
                Tela original
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={battery}
                  onChange={(event) => setBattery(event.target.checked)}
                />
                Bateria original
              </label>
            </fieldset>
          </div>
        </Card>

        <Card className="form-section">
          <div className="form-section-heading">
            <span>03</span>
            <div>
              <h2>Fotos</h2>
              <p>Envie de duas a quatro imagens, na ordem de exibição.</p>
            </div>
          </div>
          <label className="photo-drop" htmlFor="device-photos">
            <IconPhoto size={28} aria-hidden />
            <strong>Selecionar fotos</strong>
            <span>JPEG, PNG ou WebP · até 10 MiB cada</span>
          </label>
          <Input
            id="device-photos"
            className="visually-hidden"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            onChange={(event) => setPhotos(Array.from(event.target.files ?? []))}
          />
          {photos.length > 0 && (
            <ol className="photo-file-list">
              {photos.map((photo) => (
                <li key={`${photo.name}-${photo.lastModified}`}>{photo.name}</li>
              ))}
            </ol>
          )}
        </Card>

        {(formError || create.error) && (
          <div className="form-feedback">
            {formError && <p role="alert">{formError}</p>}
            {create.error && <ErrorState error={create.error} />}
          </div>
        )}
        <div className="form-actions">
          <Button asChild variant="secondary">
            <Link to="/devices">Cancelar</Link>
          </Button>
          <Button type="submit" disabled={create.isPending}>
            <IconUpload size={18} aria-hidden />
            {create.isPending
              ? 'Salvando…'
              : importing
                ? 'Importar aparelho existente'
                : 'Cadastrar novo aparelho'}
          </Button>
        </div>
      </form>
    </div>
  );
}
