import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  IconArrowDown,
  IconArrowUp,
  IconCash,
  IconRefresh,
  IconRotateClockwise,
} from '@tabler/icons-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/error-state';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { useAuth } from '@/features/auth/context';
import {
  completeInitialization,
  getInitialization,
  getInitializationPreview,
} from '@/features/devices/api';
import {
  createAdjustment,
  createContribution,
  createWithdrawal,
  getFinancialSummary,
  listFinancialTransactions,
} from '@/features/financial/api';
import { customPeriod, periodFor, type PeriodPreset } from '@/features/financial/periods';
import type {
  FinancialDirection,
  FinancialTransaction,
  FinancialTransactionType,
} from '@/types/stage-j';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const percent = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});
const dateTime = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'America/Bahia',
});
const typeLabels: Record<FinancialTransactionType, string> = {
  OPENING_BALANCE: 'Saldo inicial',
  DEVICE_PURCHASE: 'Compra de aparelho',
  DEVICE_PURCHASE_REVERSAL: 'Estorno de compra',
  MAINTENANCE: 'Manutenção',
  MAINTENANCE_REVERSAL: 'Estorno de manutenção',
  SALE: 'Venda',
  SALE_REVERSAL: 'Estorno de venda',
  OWNER_CONTRIBUTION: 'Aporte',
  OWNER_WITHDRAWAL: 'Retirada',
  MANUAL_ADJUSTMENT: 'Ajuste manual',
};
const reversible: FinancialTransactionType[] = [
  'OPENING_BALANCE',
  'OWNER_CONTRIBUTION',
  'OWNER_WITHDRAWAL',
  'MANUAL_ADJUSTMENT',
];

type Operation = 'contribution' | 'withdrawal' | 'adjustment' | 'reversal';

export function FinancialPage() {
  const client = useQueryClient();
  const { user } = useAuth();
  const [preset, setPreset] = useState<PeriodPreset>('month');
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo] = useState('');
  const [type, setType] = useState<FinancialTransactionType | ''>('');
  const [direction, setDirection] = useState<FinancialDirection | ''>('');
  const [operation, setOperation] = useState<Operation | null>(null);
  const [reversal, setReversal] = useState<FinancialTransaction | null>(null);

  const period = useMemo(() => {
    if (preset !== 'custom') return periodFor(preset);
    return customFrom && customTo ? customPeriod(customFrom, customTo) : null;
  }, [preset, customFrom, customTo]);

  const initialization = useQuery({
    queryKey: ['business-initialization'],
    queryFn: getInitialization,
  });
  const summary = useQuery({
    queryKey: ['financial-summary', period?.from, period?.to],
    queryFn: () => getFinancialSummary(period!.from, period!.to),
    enabled: !!period && initialization.data?.status === 'COMPLETED',
  });
  const transactions = useQuery({
    queryKey: ['financial-transactions', period?.from, period?.to, type, direction],
    queryFn: () =>
      listFinancialTransactions({
        from: period!.from,
        to: period!.to,
        types: type ? [type] : undefined,
        directions: direction ? [direction] : undefined,
      }),
    enabled: !!period && initialization.data?.status === 'COMPLETED',
  });

  function openOperation(value: Operation, transaction?: FinancialTransaction) {
    setReversal(transaction ?? null);
    setOperation(value);
  }

  const loading = initialization.isLoading || summary.isLoading || transactions.isLoading;
  const error = initialization.error || summary.error || transactions.error;

  return (
    <div className="financial-page">
      <PageHeader
        eyebrow="CONTROLE FINANCEIRO"
        title="Financeiro"
        description={
          summary.data
            ? `Atualizado em ${dateTime.format(new Date(summary.data.calculatedAt))}`
            : 'Caixa real, resultado e capital em estoque sem dupla contagem.'
        }
        action={
          initialization.data?.status === 'COMPLETED' ? (
            <Button onClick={() => openOperation('contribution')}>
              <IconCash size={18} aria-hidden /> Novo movimento
            </Button>
          ) : undefined
        }
      />

      {initialization.data?.status === 'PREPARING' && user && (
        <InitializationCompletion userId={user.id} userName={user.name} />
      )}
      {initialization.data?.status === 'NOT_STARTED' && (
        <Card className="financial-guidance">
          <h2>Inicie a implantação</h2>
          <p>Defina a data de corte na importação de aparelhos antes de abrir o caixa real.</p>
        </Card>
      )}

      {initialization.data?.status === 'COMPLETED' && (
        <>
          <PeriodControls
            preset={preset}
            onPreset={setPreset}
            customFrom={customFrom}
            customTo={customTo}
            onCustomFrom={setCustomFrom}
            onCustomTo={setCustomTo}
          />
          {error && <ErrorState error={error} retry={() => void client.invalidateQueries()} />}
          {loading && <p className="loading-copy">Calculando visão financeira…</p>}
          {summary.data && (
            <>
              <div className="financial-cards">
                <Metric
                  label="Saldo inicial do período"
                  value={money.format(summary.data.openingBalance)}
                />
                <Metric
                  label="Saldo final do período"
                  value={money.format(summary.data.closingBalance)}
                  strong
                />
                <Metric label="Faturamento" value={money.format(summary.data.revenue)} />
                <Metric
                  label={summary.data.profit < 0 ? 'Prejuízo' : 'Lucro'}
                  value={money.format(summary.data.profit)}
                  tone={summary.data.profit < 0 ? 'negative' : 'positive'}
                />
                <Metric
                  label="Margem"
                  value={
                    summary.data.marginPercent === null
                      ? '—'
                      : `${percent.format(summary.data.marginPercent)}%`
                  }
                />
                <Metric label="Compras" value={money.format(summary.data.devicePurchaseCost)} />
                <Metric label="Manutenções" value={money.format(summary.data.maintenanceCost)} />
                <Metric
                  label="Capital em estoque"
                  value={money.format(summary.data.stockCapital)}
                />
              </div>
              <p className="financial-explanation">
                Saldo em caixa é o dinheiro disponível. Capital em estoque é o valor investido nos
                aparelhos atuais e não é somado ao caixa.
              </p>
            </>
          )}

          <Card className="transactions-card">
            <div className="transactions-heading">
              <div>
                <p className="eyebrow">LEDGER</p>
                <h2>Movimentações</h2>
              </div>
              <div className="transaction-actions">
                <Button variant="secondary" onClick={() => openOperation('contribution')}>
                  <IconArrowUp size={17} aria-hidden /> Aporte
                </Button>
                <Button variant="secondary" onClick={() => openOperation('withdrawal')}>
                  <IconArrowDown size={17} aria-hidden /> Retirada
                </Button>
                <Button variant="secondary" onClick={() => openOperation('adjustment')}>
                  <IconRefresh size={17} aria-hidden /> Ajuste
                </Button>
              </div>
            </div>
            <div className="transaction-filters">
              <FormField id="transaction-type" label="Tipo">
                <Select
                  id="transaction-type"
                  value={type}
                  onChange={(event) => setType(event.target.value as FinancialTransactionType | '')}
                >
                  <option value="">Todos</option>
                  {Object.entries(typeLabels).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField id="transaction-direction" label="Direção">
                <Select
                  id="transaction-direction"
                  value={direction}
                  onChange={(event) => setDirection(event.target.value as FinancialDirection | '')}
                >
                  <option value="">Todas</option>
                  <option value="INFLOW">Entrada</option>
                  <option value="OUTFLOW">Saída</option>
                </Select>
              </FormField>
            </div>
            {!transactions.data?.content.length && !loading ? (
              <p className="empty-copy">Nenhuma movimentação encontrada neste período.</p>
            ) : (
              <div className="transaction-list" aria-live="polite">
                {transactions.data?.content.map((item) => (
                  <TransactionRow
                    key={item.id}
                    value={item}
                    onReverse={
                      reversible.includes(item.type) &&
                      !item.reversalOfId &&
                      !transactions.data?.content.some(
                        (candidate) => candidate.reversalOfId === item.id,
                      )
                        ? () => openOperation('reversal', item)
                        : undefined
                    }
                  />
                ))}
              </div>
            )}
          </Card>
        </>
      )}

      <OperationDialog
        operation={operation}
        reversal={reversal}
        userId={user?.id ?? ''}
        userName={user?.name ?? ''}
        onClose={() => {
          setOperation(null);
          setReversal(null);
        }}
      />
    </div>
  );
}

function PeriodControls(props: {
  preset: PeriodPreset;
  onPreset(value: PeriodPreset): void;
  customFrom: string;
  customTo: string;
  onCustomFrom(value: string): void;
  onCustomTo(value: string): void;
}) {
  return (
    <Card className="period-card">
      <div className="period-presets" aria-label="Período financeiro">
        {(
          [
            ['today', 'Hoje'],
            ['week', 'Semana'],
            ['month', 'Mês'],
            ['year', 'Ano'],
            ['custom', 'Personalizado'],
          ] as const
        ).map(([value, label]) => (
          <Button
            key={value}
            variant={props.preset === value ? 'primary' : 'ghost'}
            onClick={() => props.onPreset(value)}
            aria-pressed={props.preset === value}
          >
            {label}
          </Button>
        ))}
      </div>
      {props.preset === 'custom' && (
        <div className="custom-period">
          <FormField id="period-from" label="De">
            <Input
              id="period-from"
              type="date"
              value={props.customFrom}
              onChange={(event) => props.onCustomFrom(event.target.value)}
            />
          </FormField>
          <FormField id="period-to" label="Até">
            <Input
              id="period-to"
              type="date"
              min={props.customFrom}
              value={props.customTo}
              onChange={(event) => props.onCustomTo(event.target.value)}
            />
          </FormField>
        </div>
      )}
    </Card>
  );
}

function Metric({
  label,
  value,
  tone,
  strong,
}: {
  label: string;
  value: string;
  tone?: 'positive' | 'negative';
  strong?: boolean;
}) {
  return (
    <Card className={`financial-metric ${tone ? `metric-${tone}` : ''}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      {strong && <small>posição ao fim do período</small>}
    </Card>
  );
}

function TransactionRow({
  value,
  onReverse,
}: {
  value: FinancialTransaction;
  onReverse?: () => void;
}) {
  return (
    <article className="transaction-row">
      <div className={`transaction-direction transaction-${value.direction.toLowerCase()}`}>
        {value.direction === 'INFLOW' ? (
          <IconArrowUp size={18} aria-label="Entrada" />
        ) : (
          <IconArrowDown size={18} aria-label="Saída" />
        )}
      </div>
      <div className="transaction-main">
        <strong>{typeLabels[value.type]}</strong>
        <span>{value.description || value.source?.reference || 'Movimentação operacional'}</span>
        <small>
          {dateTime.format(new Date(value.occurredAt))}
          {value.ownerUser ? ` · Sócio relacionado: ${value.ownerUser.name}` : ''}
          {value.createdBy ? ` · Registrado por: ${value.createdBy.name}` : ''}
        </small>
      </div>
      <strong className={value.direction === 'INFLOW' ? 'value-positive' : 'value-negative'}>
        {value.direction === 'INFLOW' ? '+' : '−'} {money.format(value.amount)}
      </strong>
      {onReverse && (
        <Button variant="ghost" size="icon" aria-label="Estornar movimentação" onClick={onReverse}>
          <IconRotateClockwise size={18} aria-hidden />
        </Button>
      )}
    </article>
  );
}

function OperationDialog({
  operation,
  reversal,
  userId,
  userName,
  onClose,
}: {
  operation: Operation | null;
  reversal: FinancialTransaction | null;
  userId: string;
  userName: string;
  onClose(): void;
}) {
  const client = useQueryClient();
  const [amount, setAmount] = useState('');
  const [occurredAt, setOccurredAt] = useState(() => new Date().toISOString().slice(0, 16));
  const [description, setDescription] = useState('');
  const [direction, setDirection] = useState<FinancialDirection>('INFLOW');

  const mutation = useMutation({
    mutationFn: async () => {
      const economicDate = new Date(occurredAt).toISOString();
      if (operation === 'contribution') {
        return createContribution({
          ownerUserId: userId,
          amount: Number(amount),
          occurredAt: economicDate,
          description,
        });
      }
      if (operation === 'withdrawal') {
        return createWithdrawal({
          ownerUserId: userId,
          amount: Number(amount),
          occurredAt: economicDate,
          description,
        });
      }
      if (operation === 'reversal') {
        return createAdjustment({
          reversalOfTransactionId: reversal!.id,
          occurredAt: economicDate,
          description,
        });
      }
      return createAdjustment({
        direction,
        amount: Number(amount),
        occurredAt: economicDate,
        description,
      });
    },
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['financial-summary'] }),
        client.invalidateQueries({ queryKey: ['financial-transactions'] }),
      ]);
      onClose();
      setAmount('');
      setDescription('');
    },
  });

  if (!operation) return null;
  const title =
    operation === 'contribution'
      ? 'Registrar aporte'
      : operation === 'withdrawal'
        ? 'Registrar retirada'
        : operation === 'reversal'
          ? 'Estornar movimentação'
          : 'Registrar ajuste';

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!description.trim() || mutation.isPending) return;
    if (operation !== 'reversal' && Number(amount) <= 0) return;
    mutation.mutate();
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        title={title}
        description={
          operation === 'reversal' && reversal
            ? `${typeLabels[reversal.type]} · ${money.format(reversal.amount)}`
            : 'Registre o efeito econômico real no caixa.'
        }
        placement="bottom-sheet"
        className="financial-dialog"
      >
        <form className="modal-form" onSubmit={submit}>
          {(operation === 'contribution' || operation === 'withdrawal') && (
            <FormField id="owner-user" label="Sócio relacionado">
              <Select id="owner-user" value={userId} required disabled>
                <option value={userId}>{userName}</option>
              </Select>
            </FormField>
          )}
          {operation === 'adjustment' && (
            <FormField id="adjustment-direction" label="Direção">
              <Select
                id="adjustment-direction"
                value={direction}
                onChange={(event) => setDirection(event.target.value as FinancialDirection)}
              >
                <option value="INFLOW">Entrada</option>
                <option value="OUTFLOW">Saída</option>
              </Select>
            </FormField>
          )}
          {operation !== 'reversal' && (
            <FormField id="financial-amount" label="Valor">
              <Input
                id="financial-amount"
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
                required
              />
            </FormField>
          )}
          <FormField id="financial-date" label="Data e hora">
            <Input
              id="financial-date"
              type="datetime-local"
              value={occurredAt}
              onChange={(event) => setOccurredAt(event.target.value)}
              required
            />
          </FormField>
          <FormField id="financial-description" label="Descrição">
            <Input
              id="financial-description"
              value={description}
              maxLength={500}
              onChange={(event) => setDescription(event.target.value)}
              required
            />
          </FormField>
          {mutation.error && <ErrorState error={mutation.error} />}
          <Button type="submit" disabled={mutation.isPending || !description.trim()}>
            {mutation.isPending ? 'Salvando…' : 'Confirmar'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function InitializationCompletion({ userId, userName }: { userId: string; userName: string }) {
  const client = useQueryClient();
  const initialization = useQuery({
    queryKey: ['business-initialization'],
    queryFn: getInitialization,
  });
  const preview = useQuery({
    queryKey: ['business-initialization-preview'],
    queryFn: getInitializationPreview,
  });
  const [cash, setCash] = useState('');
  const [contribution, setContribution] = useState('0');
  const [withdrawal, setWithdrawal] = useState('0');
  const completion = useMutation({
    mutationFn: () =>
      completeInitialization({
        expectedVersion: initialization.data!.version!,
        declaredCashBalance: Number(cash),
        ownerCapitalOpenings:
          Number(contribution) > 0 || Number(withdrawal) > 0
            ? [
                {
                  ownerUserId: userId,
                  historicalContributionAmount: Number(contribution),
                  historicalWithdrawalAmount: Number(withdrawal),
                },
              ]
            : [],
      }),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['business-initialization'] }),
        client.invalidateQueries({ queryKey: ['financial-summary'] }),
        client.invalidateQueries({ queryKey: ['financial-transactions'] }),
      ]);
    },
  });

  return (
    <Card className="completion-card">
      <div>
        <p className="eyebrow">IMPLANTAÇÃO EXISTENTE</p>
        <h2>Concluir configuração inicial</h2>
        <p>
          Informe o caixa real disponível na data de corte. O estoque de{' '}
          <strong>{money.format(preview.data?.stockCapital ?? 0)}</strong> é mostrado separadamente
          e não será somado ao caixa.
        </p>
      </div>
      <div className="completion-preview">
        <span>Data de corte</span>
        <strong>
          {preview.data ? dateTime.format(new Date(preview.data.cutoffAt)) : 'Carregando…'}
        </strong>
        <span>Aparelhos importados</span>
        <strong>{preview.data?.initialDeviceCount ?? '—'}</strong>
        <span>Capital em estoque</span>
        <strong>{money.format(preview.data?.stockCapital ?? 0)}</strong>
      </div>
      <form
        className="completion-form"
        onSubmit={(event) => {
          event.preventDefault();
          if (cash !== '' && !completion.isPending) completion.mutate();
        }}
      >
        <FormField id="declared-cash" label="Caixa real na data de corte">
          <Input
            id="declared-cash"
            type="number"
            min="0"
            step="0.01"
            value={cash}
            onChange={(event) => setCash(event.target.value)}
            required
          />
        </FormField>
        <FormField id="capital-owner" label="Sócio relacionado">
          <Select id="capital-owner" value={userId} disabled>
            <option value={userId}>{userName}</option>
          </Select>
        </FormField>
        <FormField id="historical-contribution" label="Aportes históricos (informativo)">
          <Input
            id="historical-contribution"
            type="number"
            min="0"
            step="0.01"
            value={contribution}
            onChange={(event) => setContribution(event.target.value)}
          />
        </FormField>
        <FormField id="historical-withdrawal" label="Retiradas históricas (informativo)">
          <Input
            id="historical-withdrawal"
            type="number"
            min="0"
            step="0.01"
            value={withdrawal}
            onChange={(event) => setWithdrawal(event.target.value)}
          />
        </FormField>
        <p className="completion-warning">
          Após concluir, aparelhos e manutenções históricas não poderão mais ser importados.
        </p>
        {completion.error && <ErrorState error={completion.error} />}
        <Button type="submit" disabled={cash === '' || completion.isPending}>
          {completion.isPending ? 'Concluindo…' : 'Concluir implantação'}
        </Button>
      </form>
    </Card>
  );
}
