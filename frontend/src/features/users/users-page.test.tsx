import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UsersPage } from '@/pages/UsersPage';
import { AuthContext, type AuthContextValue } from '@/features/auth/context';
import { user } from '@/test/fixtures';
import type { AppUser, UserPage } from '@/types/stage-k';
import { listUsers } from './api';

vi.mock('./api', () => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  setUserActive: vi.fn(),
}));

const auth: AuthContextValue = {
  status: 'authenticated',
  user,
  error: null,
  notice: null,
  loggingOut: false,
  login: vi.fn(),
  logout: vi.fn(),
  retry: vi.fn(),
};
const totalPages = 3;
function member(page: number): AppUser {
  return {
    id: `user-${page}`,
    name: `Sócio da página ${page + 1}`,
    username: `socio.${page + 1}`,
    role: 'SOCIO',
    active: true,
    createdAt: '2026-09-06T12:00:00Z',
    updatedAt: '2026-09-06T12:00:00Z',
    version: 0,
  };
}
function pageOf(page: number): UserPage {
  return {
    content: [member(page)],
    page,
    size: 20,
    totalElements: totalPages,
    totalPages,
    first: page === 0,
    last: page === totalPages - 1,
  };
}

/** Each request stays pending until the test releases it, so the placeholder window is explicit. */
function controlledUsers() {
  const pending: Array<{ page: number; release(): void }> = [];
  vi.mocked(listUsers).mockImplementation(
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
const requestedPages = () => vi.mocked(listUsers).mock.calls.map(([input]) => input.page);

function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AuthContext value={auth}>
        <UsersPage />
      </AuthContext>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.resetAllMocks();
});

describe('UsersPage pagination', () => {
  it('refuses to page again until the requested page arrives', async () => {
    const release = controlledUsers();
    const { container } = mount();
    await release(0);
    const next = screen.getByRole('button', { name: 'Próxima' });

    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1]);
    expect(screen.getByText('Sócio da página 1')).toBeVisible();
    expect(container.querySelector('.user-list')).toHaveAttribute('aria-busy', 'true');
    // The wait is shown in the page label; the rows and badges keep their full contrast.
    expect(container.querySelector('.page-status')).toHaveAttribute('data-busy');
    expect(screen.getByRole('status')).toHaveTextContent('Atualizando usuários…');
    expect(next).toHaveAttribute('aria-disabled', 'true');
    expect(next).toHaveFocus();

    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1]);

    await release(1);
    expect(next).not.toHaveAttribute('aria-disabled');
    expect(container.querySelector('.user-list')).toHaveAttribute('aria-busy', 'false');
    expect(container.querySelector('.page-status')).not.toHaveAttribute('data-busy');

    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1, 2]);
    // Page 2 is still displayed, so "Anterior" is not at a limit: only the guard can refuse it.
    const previous = screen.getByRole('button', { name: 'Anterior' });
    expect(previous).toBeEnabled();
    expect(previous).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(previous);
    expect(requestedPages()).toEqual([0, 1, 2]);

    await release(2);
    expect(next).toBeDisabled();
    await userEvent.click(next);
    expect(requestedPages()).toEqual([0, 1, 2]);
  });

  it('never requests a page before the first one on a fast double click', async () => {
    const release = controlledUsers();
    mount();
    await release(0);
    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }));
    await release(1);

    const previous = screen.getByRole('button', { name: 'Anterior' });
    await userEvent.dblClick(previous);
    expect(requestedPages()).toEqual([0, 1, 0]);
    expect(requestedPages().some((page) => page! < 0)).toBe(false);
  });

  it('shows a skeleton instead of the previous results when the search changes', async () => {
    const release = controlledUsers();
    mount();
    await release(0);

    await userEvent.type(screen.getByLabelText('Buscar'), 'a');
    expect(screen.queryByText('Sócio da página 1')).toBeNull();
    expect(screen.queryByText('Atualizando usuários…')).toBeNull();
    expect(screen.getByText('Carregando usuários…')).toBeInTheDocument();
  });
});
