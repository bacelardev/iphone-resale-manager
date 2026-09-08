import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  IconBox,
  IconDeviceMobilePlus,
  IconFilter,
  IconSearch,
  IconUpload,
} from '@tabler/icons-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Drawer, DrawerContent, DrawerTrigger } from '@/components/ui/drawer';
import { EmptyState } from '@/components/ui/empty-state';
import { ErrorState } from '@/components/ui/error-state';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import {
  getInitialization,
  listColors,
  listDevices,
  listModels,
  startInitialization,
  type DeviceFilters,
} from '@/features/devices/api';
import { apiAssetUrl } from '@/lib/api/client';
import type { CatalogItem, DeviceStatus, DeviceSummary } from '@/types/stage-g';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const date = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium' });
const statusLabel: Record<DeviceStatus, string> = {
  PENDENTE_MANUTENCAO: 'Pendente de manutenção',
  DISPONIVEL_VENDA: 'Disponível para venda',
  VENDIDO: 'Vendido',
};

export function DevicesPage() {
  const client = useQueryClient();
  const [filters, setFilters] = useState<DeviceFilters>({ archived: false, page: 0 });
  const [cutoff, setCutoff] = useState('');
  const initialization = useQuery({
    queryKey: ['business-initialization'],
    queryFn: getInitialization,
  });
  const models = useQuery({ queryKey: ['models', 'all'], queryFn: () => listModels() });
  const colors = useQuery({ queryKey: ['colors', 'all'], queryFn: () => listColors() });
  const devices = useQuery({
    queryKey: ['devices', filters],
    queryFn: () => listDevices(filters),
  });
  const start = useMutation({
    mutationFn: () => startInitialization(new Date(cutoff).toISOString()),
    onSuccess: async () => {
      setCutoff('');
      await client.invalidateQueries({ queryKey: ['business-initialization'] });
    },
  });
  const preparing = initialization.data?.status === 'PREPARING';

  return (
    <div className="devices-page">
      <PageHeader
        eyebrow="ESTOQUE"
        title="Aparelhos"
        description="Acompanhe cada iPhone da entrada à próxima decisão."
        action={
          <div className="page-actions">
            {preparing && (
              <Button asChild variant="secondary">
                <Link to="/devices/import">
                  <IconUpload size={18} aria-hidden /> Importar aparelho existente
                </Link>
              </Button>
            )}
            <Button asChild>
              <Link to="/devices/new">
                <IconDeviceMobilePlus size={18} aria-hidden /> Novo aparelho
              </Link>
            </Button>
          </div>
        }
      />

      {initialization.data?.status === 'NOT_STARTED' && (
        <Card className="setup-card">
          <div>
            <p className="eyebrow">IMPLANTAÇÃO INICIAL</p>
            <h2>Já existe estoque no negócio?</h2>
            <p>
              Defina a data de corte para importar aparelhos existentes sem criar uma nova saída de
              caixa.
            </p>
          </div>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (cutoff) start.mutate();
            }}
          >
            <label htmlFor="cutoff">Data e hora de corte</label>
            <Input
              id="cutoff"
              type="datetime-local"
              max={new Date().toISOString().slice(0, 16)}
              value={cutoff}
              onChange={(event) => setCutoff(event.target.value)}
              required
            />
            <Button type="submit" disabled={start.isPending}>
              {start.isPending ? 'Iniciando…' : 'Iniciar preparação'}
            </Button>
          </form>
          {start.error && <ErrorState error={start.error} />}
        </Card>
      )}

      <div className="device-toolbar">
        <div className="device-search">
          <IconSearch size={18} aria-hidden />
          <Input
            aria-label="Buscar aparelhos"
            placeholder="Código, modelo ou cor"
            value={filters.search ?? ''}
            onChange={(event) =>
              setFilters((current) => ({ ...current, search: event.target.value, page: 0 }))
            }
          />
        </div>
        <div className="desktop-device-filters">
          <FilterFields
            filters={filters}
            models={models.data?.content ?? []}
            colors={colors.data?.content ?? []}
            onChange={setFilters}
          />
        </div>
        <div className="mobile-filter-trigger">
          <Drawer>
            <DrawerTrigger>
              <Button variant="secondary">
                <IconFilter size={18} aria-hidden /> Filtros
              </Button>
            </DrawerTrigger>
            <DrawerContent
              title="Filtrar aparelhos"
              description="Refine os itens exibidos no estoque."
            >
              <div className="drawer-filter-fields">
                <FilterFields
                  filters={filters}
                  models={models.data?.content ?? []}
                  colors={colors.data?.content ?? []}
                  onChange={setFilters}
                />
              </div>
            </DrawerContent>
          </Drawer>
        </div>
      </div>

      {devices.isLoading && <p className="loading-copy">Carregando aparelhos…</p>}
      {devices.error && <ErrorState error={devices.error} retry={() => void devices.refetch()} />}
      {devices.data?.content.length === 0 && (
        <Card>
          <EmptyState
            icon={<IconBox size={34} />}
            title="Nenhum aparelho encontrado"
            description="Ajuste os filtros ou cadastre o primeiro aparelho da operação."
          />
        </Card>
      )}
      {!!devices.data?.content.length && (
        <div className="device-grid">
          {devices.data.content.map((device) => (
            <DeviceCard key={device.id} device={device} />
          ))}
        </div>
      )}
      {devices.data && devices.data.totalPages > 1 && (
        <nav className="pagination" aria-label="Paginação de aparelhos">
          <Button
            variant="secondary"
            disabled={devices.data.first}
            onClick={() => setFilters((current) => ({ ...current, page: (current.page ?? 0) - 1 }))}
          >
            Anterior
          </Button>
          <span>
            Página {devices.data.page + 1} de {devices.data.totalPages}
          </span>
          <Button
            variant="secondary"
            disabled={devices.data.last}
            onClick={() => setFilters((current) => ({ ...current, page: (current.page ?? 0) + 1 }))}
          >
            Próxima
          </Button>
        </nav>
      )}
    </div>
  );
}

function FilterFields({
  filters,
  models,
  colors,
  onChange,
}: {
  filters: DeviceFilters;
  models: CatalogItem[];
  colors: CatalogItem[];
  onChange: (filters: DeviceFilters) => void;
}) {
  return (
    <>
      <Select
        aria-label="Filtrar por status"
        value={filters.status ?? ''}
        onChange={(event) =>
          onChange({
            ...filters,
            status: (event.target.value || undefined) as DeviceStatus | undefined,
            page: 0,
          })
        }
      >
        <option value="">Todos os status</option>
        {Object.entries(statusLabel).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </Select>
      <Select
        aria-label="Filtrar por modelo"
        value={filters.modelId ?? ''}
        onChange={(event) =>
          onChange({ ...filters, modelId: event.target.value || undefined, page: 0 })
        }
      >
        <option value="">Todos os modelos</option>
        {models.map((model) => (
          <option key={model.id} value={model.id}>
            {model.name}
          </option>
        ))}
      </Select>
      <Select
        aria-label="Filtrar por cor"
        value={filters.colorId ?? ''}
        onChange={(event) =>
          onChange({ ...filters, colorId: event.target.value || undefined, page: 0 })
        }
      >
        <option value="">Todas as cores</option>
        {colors.map((color) => (
          <option key={color.id} value={color.id}>
            {color.name}
          </option>
        ))}
      </Select>
      <label className="archived-toggle">
        <input
          type="checkbox"
          checked={filters.archived ?? false}
          onChange={(event) => onChange({ ...filters, archived: event.target.checked, page: 0 })}
        />
        Arquivados
      </label>
    </>
  );
}

function DeviceCard({ device }: { device: DeviceSummary }) {
  return (
    <Link to={`/devices/${device.id}`} className="device-card">
      <div className="device-cover">
        {device.coverPhotoUrl ? (
          <img
            src={apiAssetUrl(device.coverPhotoUrl)}
            alt={`${device.model.name} na cor ${device.color.name}`}
          />
        ) : (
          <IconBox size={34} aria-hidden />
        )}
      </div>
      <div className="device-card-body">
        <div className="device-card-top">
          <span>{device.internalCode}</span>
          <Badge>{statusLabel[device.status]}</Badge>
        </div>
        <h2>{device.model.name}</h2>
        <p>
          {device.storageGb} GB · {device.color.name}
        </p>
        <dl>
          <div>
            <dt>Investimento</dt>
            <dd>{money.format(device.investmentTotal)}</dd>
          </div>
          <div>
            <dt>Compra</dt>
            <dd>{date.format(new Date(device.purchasedAt))}</dd>
          </div>
        </dl>
        {device.registrationOrigin === 'INITIAL_IMPORT' && <small>Estoque inicial</small>}
      </div>
    </Link>
  );
}
