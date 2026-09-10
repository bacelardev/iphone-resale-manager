import type { MaintenanceFinancialImpact } from '@/types/stage-h';

const labels: Record<MaintenanceFinancialImpact, string> = {
  OUTFLOW_CREATED: 'Saída financeira gerada',
  OUTFLOW_REVERSED: 'Saída financeira estornada',
  NO_FINANCIAL_COST: 'Manutenção sem custo financeiro',
  HISTORICAL_COST_ONLY: 'Custo histórico — sem nova saída de caixa',
};

export function maintenanceFinancialImpactLabel(impact: MaintenanceFinancialImpact) {
  return labels[impact];
}
