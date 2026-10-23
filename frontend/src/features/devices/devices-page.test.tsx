import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { DevicesPage } from '@/pages/DevicesPage';
import type { DeviceSummary, PageResponse } from '@/types/stage-g';
import { getInitialization, listColors, listDevices, listModels } from './api';

vi.mock('./api', () => ({
  getInitialization: vi.fn(),
  listModels: vi.fn(),
  listColors: vi.fn(),
  listDevices: vi.fn(),
  startInitialization: vi.fn(),
}));

const totalPages = 3;
function device(page: number): DeviceSummary {
  return {
    id: `device-${page}`,
    internalCode: `IPH-00000${page + 1}`,
    model: { id: 'model', code: 'IPHONE', name: `iPhone página ${page + 1}` },
    color: { id: 'color', code: 'BLACK', name: 'Preto' },
    storageGb: 128,
    purchasePrice: 1000,
    maintenanceTotal: 0,
    investmentTotal: 1000,
    purchasedAt: '2026-09-10T12:00:00Z',
    status: 'DISPONIVEL_VENDA',
    registrationOrigin: 'OPERATIONAL',
    archived: false,
    coverPhotoUrl: null,
    updatedAt: '2026-09-10T12:00:00Z',
    version: 0,
  };
}
function pageOf(page: number): PageResponse<DeviceSummary> {
  return {
    content: [device(page)],
    page,
    size: 24,
    totalElements: totalPages,
    totalPages,
    first: page === 0,
    last: page === totalPages - 1,
  };
}
const empty = { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 };

/** Each request stays pending until the test releases it, so the placeholder window is explicit. */
function controlledDevices() {
  const pending: Array<{ page: number; release(): void }> = [];
  vi.mocked(listDevices).mockImplementation(
    (filters) =>
      new Promise((resolve) => {
        const page = filters.page ?? 0;
        pending.push({ page, release: () => resolve(pageOf(page)) });
      }),
  );
  return async (page: number) => {
    const request = pending.find((item) => item.page === page);
    if (!request) throw new Error(`No pending request for page ${page}`);
    pending.splice(pending.indexOf(request), 1);
    request.release();
    await screen.findByText(`Página ${page + 1} de ${totalPages}`);
  };
}
const requestedPages = () => vi.mocked(listDevices).mock.calls.map(([filters]) => filters.page);

function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <DevicesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(getInitialization).mockResolvedValue({
    status: 'COMPLETED',
    cutoffAt: null,
    trackingStartedAt: null,
  });
  vi.mocked(listModels).mockResolvedValue({ ...empty, first: true, last: true });
  vi.mocked(listColors).mockResolvedValue({ ...empty, first: true, last: true });
});

describe('DevicesPage pagination', () => {
  it('waits for the requested page before paging again and keeps the page limits', async () => {
    const release = controlledDevices();
    const { container } = mount();
    await release(0);
    const next = screen.getByRole('button', { name: 'Próxima' });
    const previous = screen.getByRole('button', { name: 'Anterior' });
    expect(previous).toBeDisabled();

    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1]);
    // The previous page stays on screen, marked as refreshing, and paging is unavailable.
    expect(screen.getByText('iPhone página 1')).toBeVisible();
    expect(container.querySelector('.device-grid')).toHaveAttribute('aria-busy', 'true');
    expect(next).toHaveAttribute('aria-disabled', 'true');
    expect(next).toHaveFocus();

    // aria-disabled does not stop clicks: the handler itself must refuse them.
    await userEvent.click(next);
    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1]);

    await release(1);
    expect(next).not.toHaveAttribute('aria-disabled');
    expect(previous).toBeEnabled();
    expect(container.querySelector('.device-grid')).toHaveAttribute('aria-busy', 'false');

    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1, 2]);
    // Page 2 is still displayed, so "Anterior" is not at a limit: only the guard can refuse it.
    expect(previous).toBeEnabled();
    expect(previous).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(previous);
    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1, 2]);
    await release(2);
    expect(next).toBeDisabled();
    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1, 2]);
    expect(requestedPages().every((page) => page! >= 0 && page! < totalPages)).toBe(true);
  });

  it('never goes before the first page on a fast double click', async () => {
    const release = controlledDevices();
    mount();
    await release(0);
    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }));
    await release(1);

    const previous = screen.getByRole('button', { name: 'Anterior' });
    await userEvent.dblClick(previous);
    expect(requestedPages()).toEqual([0, 1, 0]);
    await release(0);
    expect(previous).toBeDisabled();
    expect(requestedPages().some((page) => page! < 0)).toBe(false);
  });

  it('shows a skeleton instead of the previous results when a filter changes', async () => {
    const release = controlledDevices();
    mount();
    await release(0);

    await userEvent.type(screen.getByRole('textbox', { name: 'Buscar aparelhos' }), 'x');
    expect(screen.queryByText('iPhone página 1')).toBeNull();
    expect(screen.queryByText('Atualizando aparelhos…')).toBeNull();
    expect(screen.getByText('Carregando aparelhos…')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Paginação de aparelhos' })).toBeNull();
  });
});
