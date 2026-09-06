import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';
export function Badge({ children, positive = false }: { children: ReactNode; positive?: boolean }) {
  return <span className={cn('badge', positive && 'badge-positive')}>{children}</span>;
}
