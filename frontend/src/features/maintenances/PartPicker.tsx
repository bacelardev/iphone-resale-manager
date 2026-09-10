import { CatalogPicker } from '@/features/devices/CatalogPicker';
import type { CatalogItem } from '@/types/stage-g';

export function PartPicker({
  items,
  value,
  onChange,
}: {
  items: CatalogItem[];
  value: string;
  onChange: (value: string) => void;
}) {
  return <CatalogPicker label="Peça" items={items} value={value} onChange={onChange} />;
}
