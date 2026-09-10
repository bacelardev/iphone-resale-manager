import { maintenanceFinancialImpactLabel } from './financial-impact';

describe('maintenanceFinancialImpactLabel', () => {
  it.each([
    ['OUTFLOW_CREATED', 'Saída financeira gerada'],
    ['OUTFLOW_REVERSED', 'Saída financeira estornada'],
    ['NO_FINANCIAL_COST', 'Manutenção sem custo financeiro'],
    ['HISTORICAL_COST_ONLY', 'Custo histórico — sem nova saída de caixa'],
  ] as const)('labels %s', (impact, label) => {
    expect(maintenanceFinancialImpactLabel(impact)).toBe(label);
  });
});
