import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  IconArrowRight,
  IconDeviceMobile,
  IconHistory,
  IconWallet,
} from '@tabler/icons-react';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/error-state';
import { PageHeader } from '@/components/ui/page-header';
import { useAuth } from '@/features/auth/context';
import { getInitialization, listDevices } from '@/features/devices/api';
import { getFinancialSummary } from '@/features/financial/api';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const percent = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});

export function DashboardPage() {
  const { user } = useAuth();
  const initialization = useQuery({
    queryKey: ['business-initialization'],
    queryFn: getInitialization,
  });
  const summary = useQuery({
    queryKey: ['financial-summary', 'dashboard'],
    queryFn: () => getFinancialSummary(),
    enabled: initialization.data?.status === 'COMPLETED',
  });
  const available = useQuery({
    queryKey: ['devices-count', 'DISPONIVEL_VENDA'],
    queryFn: () => listDevices({ status: 'DISPONIVEL_VENDA', size: 1 }),
  });
  const maintenance = useQuery({
    queryKey: ['devices-count', 'PENDENTE_MANUTENCAO'],
    queryFn: () => listDevices({ status: 'PENDENTE_MANUTENCAO', size: 1 }),
  });
  const sold = useQuery({
    queryKey: ['devices-count', 'VENDIDO'],
    queryFn: () => listDevices({ status: 'VENDIDO', size: 1 }),
  });

  const error = summary.error || available.error || maintenance.error || sold.error;
  return (
    <div className="dashboard-page">
      <PageHeader
        eyebrow="SEU PAINEL"
        title={`Olá, ${user?.name.split(' ')[0] ?? 'sócio'}.`}
        description="Uma leitura real do caixa, do resultado e dos aparelhos."
        action={
          <Badge positive>
            <span className="status-dot" /> Dados atualizados
          </Badge>
        }
      />
      {error && <ErrorState error={error} />}
      <div className="dashboard-metrics" aria-live="polite">
        <DashboardMetric label="Saldo atual" value={summary.data ? money.format(summary.data.closingBalance) : '—'} />
        <DashboardMetric label="Faturamento do mês" value={summary.data ? money.format(summary.data.revenue) : '—'} />
        <DashboardMetric
          label={summary.data && summary.data.profit < 0 ? 'Prejuízo do mês' : 'Lucro do mês'}
          value={summary.data ? money.format(summary.data.profit) : '—'}
        />
        <DashboardMetric
          label="Margem"
          value={
            summary.data?.marginPercent == null
              ? '—'
              : `${percent.format(summary.data.marginPercent)}%`
          }
        />
        <DashboardMetric label="Capital em estoque" value={summary.data ? money.format(summary.data.stockCapital) : '—'} />
        <DashboardMetric label="Disponíveis" value={String(available.data?.totalElements ?? '—')} />
        <DashboardMetric label="Em manutenção" value={String(maintenance.data?.totalElements ?? '—')} />
        <DashboardMetric label="Vendidos" value={String(sold.data?.totalElements ?? '—')} />
      </div>
      <p className="financial-explanation">
        Saldo em caixa representa dinheiro disponível; capital em estoque representa o investimento
        nos aparelhos atuais.
      </p>
      <div className="dashboard-modules">
        <Link to="/devices" className="module-card">
          <IconDeviceMobile size={24} aria-hidden />
          <h2>Aparelhos</h2>
          <p>Estoque, manutenção e vendas.</p>
          <IconArrowRight size={18} aria-hidden />
        </Link>
        <Link to="/financial" className="module-card">
          <IconWallet size={24} aria-hidden />
          <h2>Financeiro</h2>
          <p>Caixa, resultado e movimentações.</p>
          <IconArrowRight size={18} aria-hidden />
        </Link>
        <Link to="/history" className="module-card">
          <IconHistory size={24} aria-hidden />
          <h2>Histórico</h2>
          <p>Auditoria consolidada em uma próxima etapa.</p>
          <Badge>Em breve</Badge>
        </Link>
      </div>
    </div>
  );
}

function DashboardMetric({ label, value }: { label: string; value: string }) {
  return (
    <Card className="dashboard-metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </Card>
  );
}
