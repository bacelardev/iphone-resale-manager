import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HistoryPage } from '@/pages/HistoryPage';
import type { AuditEvent, AuditPage } from '@/types/stage-k';
import { listAuditEvents } from './api';

vi.mock('./api', () => ({
  listAuditEvents: vi.fn(),
  getAuditEvent: vi.fn(),
}));

const totalPages = 3;
function event(page: number): AuditEvent {
  return {
    id: `event-${page}`,
    occurredAt: '2026-09-06T12:00:00Z',
    actorUser: { id: 'user', name: 'Sócio' },
    action: 'CREATED',
    entityType: 'DEVICE',
    entityId: `device-${page}`,
    entityReference: `IPH-00000${page + 1}`,
    summary: `Evento da página ${page + 1}`,
    changes: {},
    requestId: null,
  };
}
function pageOf(page: number): AuditPage {
  return {
    content: [event(page)],
    page,
    size: 20,
    totalElements: totalPages,
    totalPages,
    first: page === 0,
    last: page === totalPages - 1,
  };
}

/** Each request stays pending until the test releases it, so the placeholder window is explicit. */
function controlledEvents() {
  const pending: Array<{ page: number; release(): void }> = [];
  vi.mocked(listAuditEvents).mockImplementation(
    (input) =>
      new Promise((resolve) => {
        const page = input.page ?? 0;
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
const requestedPages = () => vi.mocked(listAuditEvents).mock.calls.map(([input]) => input.page);

function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <HistoryPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
});

describe('HistoryPage pagination', () => {
  it('keeps the previous events at full contrast and shows the wait in the page label', async () => {
    const release = controlledEvents();
    const { container } = mount();
    await release(0);
    const list = container.querySelector('.stage-k-list');
    const status = container.querySelector('.page-status');
    expect(status).not.toHaveAttribute('data-busy');

    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }));
    expect(screen.getByText('Evento da página 1')).toBeVisible();
    expect(list).toHaveAttribute('aria-busy', 'true');
    expect(status).toHaveAttribute('data-busy');
    expect(screen.getByRole('status')).toHaveTextContent('Atualizando histórico…');

    await release(1);
    expect(screen.getByText('Evento da página 2')).toBeVisible();
    expect(list).toHaveAttribute('aria-busy', 'false');
    expect(status).not.toHaveAttribute('data-busy');
    expect(screen.getByRole('status')).toBeEmptyDOMElement();
  });

  it('refuses to page again until the requested page arrives', async () => {
    const release = controlledEvents();
    mount();
    await release(0);
    const next = screen.getByRole('button', { name: 'Próxima' });
    const previous = screen.getByRole('button', { name: 'Anterior' });
    expect(previous).toBeDisabled();
    expect(next).not.toHaveAttribute('aria-disabled');

    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1]);
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
    expect(next).toHaveAttribute('aria-disabled', 'true');
    expect(next).toHaveFocus();

    // aria-disabled does not stop clicks: the handler itself must refuse them.
    await userEvent.click(next);
    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1]);

    await release(1);
    expect(next).not.toHaveAttribute('aria-disabled');
    expect(previous).not.toHaveAttribute('aria-disabled');
    expect(previous).toBeEnabled();

    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1, 2]);
    // Page 2 is still displayed, so "Anterior" is not at a limit: only the guard can refuse it.
    expect(previous).toBeEnabled();
    expect(previous).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(previous);
    expect(requestedPages()).toEqual([0, 1, 2]);

    await release(2);
    expect(next).toBeDisabled();
    expect(next).not.toHaveAttribute('aria-disabled');
    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1, 2]);
  });

  it('never requests a page before the first one on a fast double click', async () => {
    const release = controlledEvents();
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
});
