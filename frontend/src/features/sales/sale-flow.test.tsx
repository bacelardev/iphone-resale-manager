import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { DeviceDetailPage } from '@/pages/DeviceDetailPage';
import { SaleFormPage } from '@/pages/SaleFormPage';
import { SaleDetailPage } from '@/pages/SaleDetailPage';
import { getDevice } from '@/features/devices/api';
import { registerSale, getSale, cancelSale } from './api';
import { ApiRequestError, errorMessage } from '@/lib/api/errors';
import type { DeviceDetail } from '@/types/stage-g';
import type { Sale } from '@/types/stage-i';
vi.mock('@/features/devices/api', () => ({ getDevice: vi.fn() }));
vi.mock('./api', () => ({ registerSale: vi.fn(), getSale: vi.fn(), cancelSale: vi.fn() }));
vi.mock('@/features/maintenances/MaintenancesSection', () => ({ MaintenancesSection: () => null }));
const actor = { id: 'actor', name: 'Sócio Teste' };
const device: DeviceDetail = {
  id: 'device',
  internalCode: 'IPH-000001',
  model: { id: 'model', code: 'IPHONE', name: 'iPhone' },
  color: { id: 'color', code: 'BLACK', name: 'Preto' },
  storageGb: 256,
  purchasePrice: 1800,
  maintenanceTotal: 250,
  investmentTotal: 2050,
  purchasedAt: '2026-09-10T12:00:00Z',
  status: 'DISPONIVEL_VENDA',
  registrationOrigin: 'OPERATIONAL',
  archived: false,
  coverPhotoUrl: null,
  updatedAt: '2026-09-10T12:00:00Z',
  version: 3,
  faceIdWorking: true,
  originalScreen: true,
  originalBattery: true,
  batteryHealthPercent: 90,
  photos: [],
  createdAt: '2026-09-10T12:00:00Z',
  createdBy: actor,
  updatedBy: actor,
  archivedAt: null,
  archivedBy: null,
};
const sale: Sale = {
  id: 'sale',
  deviceId: 'device',
  salePrice: 3200,
  soldAt: '2026-09-10T18:00:00Z',
  responsibleUser: actor,
  status: 'ACTIVE',
  purchasePrice: 1800,
  maintenanceTotal: 250,
  investmentTotal: 2050,
  profit: 1150,
  marginPercent: 35.9375,
  cancelledAt: null,
  cancelledBy: null,
  cancellationReason: null,
  createdAt: '2026-09-10T18:01:00Z',
  version: 0,
};
function mount(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  client.setQueryData(['devices'], { content: [] });
  client.setQueryData(['financial'], { unused: true });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/devices/:id" element={<DeviceDetailPage />} />
          <Route path="/devices/:deviceId/sale/new" element={<SaleFormPage />} />
          <Route path="/devices/:deviceId/sale" element={<SaleDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}
beforeEach(() => {
  vi.mocked(getDevice).mockResolvedValue(device);
  vi.mocked(getSale).mockResolvedValue(sale);
});
it.each([
  ['DISPONIVEL_VENDA', false, 'Registrar venda'],
  ['VENDIDO', false, 'Ver venda'],
  ['PENDENTE_MANUTENCAO', false, null],
  ['DISPONIVEL_VENDA', true, null],
] as const)('CTA for %s archived=%s', async (status, archived, action) => {
  vi.mocked(getDevice).mockResolvedValue({ ...device, status, archived });
  mount('/devices/device');
  await screen.findByRole('heading', { name: 'iPhone' });
  for (const name of ['Registrar venda', 'Ver venda']) {
    if (name === action) expect(screen.getByRole('link', { name })).toBeVisible();
    else expect(screen.queryByRole('link', { name })).not.toBeInTheDocument();
  }
});
it('uses server values after registration and invalidates inventory and financial queries', async () => {
  vi.mocked(registerSale).mockResolvedValue({ ...sale, profit: 1000 });
  vi.mocked(getSale).mockResolvedValue({ ...sale, profit: 1000 });
  const client = mount('/devices/device/sale/new');
  fireEvent.change(await screen.findByLabelText('Preço de venda (R$)'), {
    target: { value: '3200' },
  });
  fireEvent.change(screen.getByLabelText('Data e hora da venda'), {
    target: { value: '2026-09-10T19:00' },
  });
  expect(screen.getByText(/R\$\s1\.150,00/)).toBeVisible();
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar venda' }));
  await screen.findByRole('heading', { name: 'Detalhes da venda' });
  expect(screen.getByText(/R\$\s1\.000,00/)).toBeVisible();
  expect(client.getQueryState(['devices'])?.isInvalidated).toBe(true);
  expect(client.getQueryState(['financial'])?.isInvalidated).toBe(true);
  expect(registerSale).toHaveBeenCalledWith(
    'device',
    expect.objectContaining({ deviceVersion: 3, salePrice: 3200 }),
  );
});
it('cancels once, invalidates queries and shows success on the available device', async () => {
  vi.mocked(getDevice).mockResolvedValue({ ...device, status: 'VENDIDO', version: 4 });
  let finish!: (value: Sale) => void;
  vi.mocked(cancelSale).mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const client = mount('/devices/device/sale');
  await screen.findByRole('heading', { name: 'Detalhes da venda' });
  await userEvent.click(screen.getByRole('button', { name: 'Cancelar venda' }));
  await userEvent.type(screen.getByLabelText('Motivo'), 'Venda desfeita');
  await userEvent.click(screen.getByRole('button', { name: 'Confirmar cancelamento' }));
  expect(screen.getByRole('button', { name: 'Cancelando…' })).toBeDisabled();
  expect(cancelSale).toHaveBeenCalledTimes(1);
  vi.mocked(getDevice).mockResolvedValue({ ...device, version: 5 });
  finish({ ...sale, status: 'CANCELLED' });
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Venda cancelada'));
  expect(screen.getByRole('link', { name: 'Registrar venda' })).toBeVisible();
  expect(client.getQueryState(['devices'])?.isInvalidated).toBe(true);
  expect(client.getQueryState(['financial'])?.isInvalidated).toBe(true);
  expect(client.getQueryState(['sale', 'device'])?.isInvalidated).toBe(true);
});
it.each([
  'DEVICE_NOT_AVAILABLE_FOR_SALE',
  'SALE_ALREADY_EXISTS',
  'SALE_DATE_BEFORE_PURCHASE',
  'SALE_DATE_BEFORE_ACTIVE_MAINTENANCE',
  'SALE_REQUIRES_OPERATIONAL_PERIOD',
  'CONCURRENT_MODIFICATION',
])('maps %s by code, without displaying untrusted server text', (code) => {
  const error = new ApiRequestError({
    timestamp: '',
    status: 422,
    code,
    message: 'internal SQL',
    path: '',
    requestId: '',
    fieldErrors: [],
  });
  expect(errorMessage(error)).not.toContain('internal SQL');
  expect(errorMessage(error)).not.toContain('Não foi possível concluir.');
});
