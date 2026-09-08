import { StrictMode } from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { App } from '@/App';
import { createQueryClient } from '@/app/query/client';
import { AuthTokenStore } from '@/lib/auth/token-store';
import { apiRequest } from '@/lib/api/client';
import { apiFailure, fixtureToken, json, loginResult, user } from '@/test/fixtures';
import { AuthProvider } from './AuthProvider';

function renderApp(path = '/login') {
  const client = createQueryClient();
  render(
    <StrictMode>
      <QueryClientProvider client={client}>
        <AuthProvider>
          <MemoryRouter initialEntries={[path]}>
            <App />
          </MemoryRouter>
        </AuthProvider>
      </QueryClientProvider>
    </StrictMode>,
  );
  return client;
}
async function submitLogin() {
  const actor = userEvent.setup();
  await actor.type(await screen.findByLabelText('Usuário'), 'socio.teste');
  await actor.type(screen.getByLabelText('Senha', { exact: true }), 'valid-test-password');
  await actor.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('authentication and routing', () => {
  it('sends a real-shaped request, stores the token and redirects after login', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(loginResult));
    vi.stubGlobal('fetch', fetchMock);
    renderApp();
    await submitLogin();
    expect(await screen.findByRole('heading', { name: 'Olá, Sócio.' })).toBeInTheDocument();
    expect(AuthTokenStore.get()).toBe(fixtureToken);
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/auth/login',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ username: 'socio.teste', password: 'valid-test-password' }),
      }),
    );
    expect(document.body.textContent).not.toContain(fixtureToken);
  });
  it.each([
    [401, 'AUTHENTICATION_FAILED', 'Usuário ou senha inválidos.'],
    [429, 'LOGIN_RATE_LIMITED', 'Muitas tentativas. Aguarde um momento antes de tentar novamente.'],
    [400, 'VALIDATION_ERROR', 'Confira os campos informados e tente novamente.'],
  ])('shows a contextual %i error', async (status, code, message) => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => Promise.resolve(apiFailure(Number(status), String(code)))),
    );
    renderApp();
    await submitLogin();
    expect(await screen.findByText(String(message))).toBeInTheDocument();
    expect(AuthTokenStore.get()).toBeNull();
  });
  it('shows a network error on login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    renderApp();
    await submitLogin();
    expect(await screen.findByText(/Não foi possível conectar/)).toBeInTheDocument();
  });
  it('protects deep links without a token and does not call me', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderApp('/devices/new');
    expect(
      await screen.findByRole('heading', { name: 'Entre no seu espaço.' }),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it('keeps protected content hidden while checking', async () => {
    AuthTokenStore.set(fixtureToken);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => new Promise(() => {})),
    );
    renderApp('/dashboard');
    expect(screen.getByRole('status', { name: 'Verificando sessão' })).toBeInTheDocument();
    expect(screen.queryByText('Explore seu espaço')).not.toBeInTheDocument();
  });
  it('restores me and redirects an authenticated visitor away from login', async () => {
    AuthTokenStore.set(fixtureToken);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => Promise.resolve(json(user))),
    );
    renderApp();
    expect(await screen.findByRole('heading', { name: 'Olá, Sócio.' })).toBeInTheDocument();
  });
  it('clears an invalid token after me returns 401', async () => {
    AuthTokenStore.set(fixtureToken);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(() => Promise.resolve(apiFailure(401, 'UNAUTHORIZED'))),
    );
    renderApp('/dashboard');
    expect(
      await screen.findByRole('heading', { name: 'Entre no seu espaço.' }),
    ).toBeInTheDocument();
    expect(AuthTokenStore.get()).toBeNull();
  });
  it('retains the token on restore failure and allows retry', async () => {
    AuthTokenStore.set(fixtureToken);
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('offline'));
    vi.stubGlobal('fetch', fetchMock);
    renderApp('/dashboard');
    expect(await screen.findByText(/Não foi possível conectar/)).toBeInTheDocument();
    expect(AuthTokenStore.get()).toBe(fixtureToken);
    fetchMock.mockImplementation(() => Promise.resolve(json(user)));
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(await screen.findByRole('heading', { name: 'Olá, Sócio.' })).toBeInTheDocument();
  });
  it('logs out, removes user and clears private queries', async () => {
    AuthTokenStore.set(fixtureToken);
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockImplementation((url: string) =>
          Promise.resolve(
            url.endsWith('/logout') ? new Response(null, { status: 204 }) : json(user),
          ),
        ),
    );
    const client = renderApp('/dashboard');
    await screen.findByRole('heading', { name: 'Olá, Sócio.' });
    client.setQueryData(['private', 'fixture'], { value: 1 });
    await userEvent.click(screen.getByRole('button', { name: 'Sair' }));
    expect(
      await screen.findByRole('heading', { name: 'Entre no seu espaço.' }),
    ).toBeInTheDocument();
    expect(AuthTokenStore.get()).toBeNull();
    expect(client.getQueryCache().getAll()).toHaveLength(0);
    expect(screen.queryByText(user.name)).not.toBeInTheDocument();
  });
  it('clears locally on logout network failure and explains remote uncertainty', async () => {
    AuthTokenStore.set(fixtureToken);
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockImplementation((url: string) =>
          url.endsWith('/logout')
            ? Promise.reject(new TypeError('offline'))
            : Promise.resolve(json(user)),
        ),
    );
    renderApp('/dashboard');
    await screen.findByRole('heading', { name: 'Olá, Sócio.' });
    await userEvent.click(screen.getByRole('button', { name: 'Sair' }));
    expect(
      await screen.findByText(/Não foi possível confirmar o encerramento/),
    ).toBeInTheDocument();
    expect(AuthTokenStore.get()).toBeNull();
  });
  it('handles a global protected 401 and discards private cached data', async () => {
    AuthTokenStore.set(fixtureToken);
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(json(user)));
    vi.stubGlobal('fetch', fetchMock);
    const client = renderApp('/dashboard');
    await screen.findByRole('heading', { name: 'Olá, Sócio.' });
    client.setQueryData(['private', 'fixture'], { value: 1 });
    fetchMock.mockImplementation(() => Promise.resolve(apiFailure(401, 'UNAUTHORIZED')));
    await act(async () => {
      await apiRequest('/api/v1/auth/me').catch(() => undefined);
    });
    await waitFor(() => expect(AuthTokenStore.get()).toBeNull());
    expect(client.getQueryCache().getAll()).toHaveLength(0);
    expect(
      await screen.findByRole('heading', { name: 'Entre no seu espaço.' }),
    ).toBeInTheDocument();
  });
  it('toggles password visibility with an accessible button', async () => {
    renderApp();
    const password = await screen.findByLabelText('Senha', { exact: true });
    expect(password).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Mostrar senha' }));
    expect(password).toHaveAttribute('type', 'text');
    await userEvent.click(screen.getByRole('button', { name: 'Ocultar senha' }));
    expect(password).toHaveAttribute('type', 'password');
  });
});
