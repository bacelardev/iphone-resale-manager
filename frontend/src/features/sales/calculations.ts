export type SalePreview = {
  investmentTotal: number;
  profit: number;
  marginPercent: number | null;
};

export function calculateSalePreview(
  purchasePrice: number,
  maintenanceTotal: number,
  salePrice: number,
): SalePreview {
  const investmentTotal = purchasePrice + maintenanceTotal;
  const profit = salePrice - investmentTotal;
  return {
    investmentTotal,
    profit,
    marginPercent: salePrice > 0 ? (profit / salePrice) * 100 : null,
  };
}
