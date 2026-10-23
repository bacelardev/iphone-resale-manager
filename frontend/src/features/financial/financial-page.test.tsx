import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { FinancialPage } from '@/pages/FinancialPage';
import { AuthContext, type AuthContextValue } from '@/features/auth/context';
import { getInitialization } from '@/features/devices/api';
import { finishExitAnimation, simulateDialogAnimations } from '@/test/dialog-motion';
import { user } from '@/test/fixtures';
import type { FinancialSummary, FinancialTransactionPage } from '@/types/stage-j';
import {
  createAdjustment,
  createContribution,
  createWithdrawal,
  getFinancialSummary,
  listFinancialTransactions,
} from './api';
import { periodFor } from './periods';

vi.mock('@/features/devices/api', () => ({
  getInitialization: vi.fn(),
  getInitializationPreview: vi.fn(),
  completeInitialization: vi.fn(),
}));
vi.mock('./api', () => ({
  getFinancialSummary: vi.fn(),
  listFinancialTransactions: vi.fn(),
  createContribution: vi.fn(),
  createWithdrawal: vi.fn(),
  createAdjustment: vi.fn(),
}));

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
// Testing Library collapses the no-break space Intl puts after "R$"; compare the same text.
const money = { format: (value: number) => currency.format(value).replace(/\s/g, ' ') };
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
const noTransactions: FinancialTransactionPage = {
  content: [],
  page: 0,
  size: 50,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

function summary(values: Partial<FinancialSummary>): FinancialSummary {
  return {
    period: { from: '', to: '', businessTimezone: 'America/Bahia' },
    openingBalance: 0,
    closingBalance: 0,
    revenue: 0,
    devicePurchaseCost: 0,
    maintenanceCost: 0,
    profit: 0,
    marginPercent: null,
    stockCapital: 0,
    calculatedAt: '2026-10-22T12:00:00Z',
    ...values,
  };
}
const monthValues = summary({
  openingBalance: 1000.01,
  closingBalance: 1111.11,
  revenue: 2222.22,
  devicePurchaseCost: 444.44,
  maintenanceCost: 55.55,
  profit: 333.33,
  stockCapital: 6666.66,
});
const weekValues = summary({
  openingBalance: 7000.07,
  closingBalance: 7777.77,
  revenue: 8888.88,
  devicePurchaseCost: 99.99,
  maintenanceCost: 12.34,
  profit: 543.21,
  stockCapital: 3210.98,
});
const amounts = (value: FinancialSummary) =>
  [
    value.openingBalance,
    value.closingBalance,
    value.revenue,
    value.devicePurchaseCost,
    value.maintenanceCost,
    value.profit,
    value.stockCapital,
  ].map((amount) => money.format(amount));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

/**
 * TanStack runs `mutationFn` a few microtasks after `mutate()`. A zero-delay timeout fires only
 * once the microtask queue is empty, so assertions about mutation calls see the settled result.
 */
async function settlePendingWork() {
  await act(() => new Promise<void>((done) => setTimeout(done, 0)));
}

function mount() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const view = render(
    <QueryClientProvider client={client}>
      <AuthContext value={auth}>
        <FinancialPage />
      </AuthContext>
    </QueryClientProvider>,
  );
  return view.container;
}

beforeEach(() => {
  // Module mocks live across tests: start each one with no calls and no queued responses.
  vi.resetAllMocks();
  vi.mocked(getInitialization).mockResolvedValue({
    status: 'COMPLETED',
    cutoffAt: '2026-09-01T03:00:00Z',
    trackingStartedAt: '2026-09-01T03:00:00Z',
  });
  vi.mocked(listFinancialTransactions).mockResolvedValue(noTransactions);
});

describe('FinancialPage period changes', () => {
  it('never shows the previous period values while the selected period loads', async () => {
    const week = deferred<FinancialSummary>();
    vi.mocked(getFinancialSummary)
      .mockResolvedValueOnce(monthValues)
      .mockReturnValueOnce(week.promise);
    const container = mount();
    for (const amount of amounts(monthValues)) {
      expect(await screen.findByText(amount)).toBeVisible();
    }

    await userEvent.click(screen.getByRole('button', { name: 'Semana' }));
    const selected = periodFor('week');
    expect(getFinancialSummary).toHaveBeenLastCalledWith(selected.from, selected.to);
    for (const amount of amounts(monthValues)) expect(screen.queryByText(amount)).toBeNull();
    const cards = container.querySelector('.financial-cards')!;
    expect(cards).toHaveAttribute('aria-busy', 'true');
    expect(cards.querySelectorAll('.financial-metric')).toHaveLength(8);
    expect(cards.querySelectorAll('.financial-metric .skeleton-value')).toHaveLength(8);
    expect(screen.getByText('Calculando visão financeira…')).toBeInTheDocument();

    week.resolve(weekValues);
    for (const amount of amounts(weekValues)) expect(await screen.findByText(amount)).toBeVisible();
    expect(cards).toHaveAttribute('aria-busy', 'false');
    expect(cards.querySelectorAll('.skeleton-value')).toHaveLength(0);
    for (const amount of amounts(monthValues)) expect(screen.queryByText(amount)).toBeNull();
  });

  it('hides the cards instead of keeping old values while a custom period is incomplete', async () => {
    vi.mocked(getFinancialSummary).mockResolvedValueOnce(monthValues);
    const container = mount();
    expect(await screen.findByText(money.format(monthValues.closingBalance))).toBeVisible();

    await userEvent.click(screen.getByRole('button', { name: 'Personalizado' }));
    expect(container.querySelectorAll('.financial-metric')).toHaveLength(0);
    for (const amount of amounts(monthValues)) expect(screen.queryByText(amount)).toBeNull();
    expect(getFinancialSummary).toHaveBeenCalledTimes(1);
  });
});

describe('FinancialPage operation dialog', () => {
  beforeEach(() => {
    vi.mocked(getFinancialSummary).mockResolvedValue(monthValues);
  });

  async function openContribution() {
    mount();
    await userEvent.click(await screen.findByRole('button', { name: 'Aporte' }));
    const dialog = screen.getByRole('dialog', { name: 'Registrar aporte' });
    await userEvent.type(within(dialog).getByLabelText('Valor'), '150');
    await userEvent.type(within(dialog).getByLabelText('Descrição'), 'Aporte de teste');
    return dialog;
  }

  it('submits an operation that is still open', async () => {
    vi.mocked(createContribution).mockResolvedValue({} as never);
    const dialog = await openContribution();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Confirmar' }));
    expect(createContribution).toHaveBeenCalledTimes(1);
    expect(createContribution).toHaveBeenCalledWith(
      expect.objectContaining({
        ownerUserId: user.id,
        amount: 150,
        description: 'Aporte de teste',
      }),
    );
    expect(createAdjustment).not.toHaveBeenCalled();
    expect(createWithdrawal).not.toHaveBeenCalled();
  });

  it('detects a direct form submit of an open operation with the same mechanism', async () => {
    // Control for the next test: this submit path and settling do reach the mutation.
    vi.mocked(createContribution).mockReturnValue(new Promise(() => {}));
    const dialog = await openContribution();
    dialog.querySelector('form')!.requestSubmit();
    await settlePendingWork();
    expect(createContribution).toHaveBeenCalledTimes(1);
    expect(createAdjustment).not.toHaveBeenCalled();
  });

  it('ignores a late submit while the dismissed dialog animates out', async () => {
    simulateDialogAnimations();
    const dialog = await openContribution();
    const form = dialog.querySelector('form')!;

    await userEvent.keyboard('{Escape}');
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute('data-state', 'closed');
    // The retained operation keeps its own title and fields during the exit…
    expect(within(dialog).getByText('Registrar aporte')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Valor')).toHaveValue(150);

    // …but a submit that still reaches the form, such as a late Enter, must not run a mutation.
    form.requestSubmit();
    form.requestSubmit();
    await settlePendingWork();
    expect(createContribution).not.toHaveBeenCalled();
    expect(createAdjustment).not.toHaveBeenCalled();
    expect(createWithdrawal).not.toHaveBeenCalled();

    finishExitAnimation(dialog);
    expect(dialog).not.toBeInTheDocument();
    expect(createAdjustment).not.toHaveBeenCalled();
  });
});
