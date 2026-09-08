import { cn } from '@/lib/utils/cn';
export function Skeleton({ className }: { className?: string }) {
  return <span aria-hidden className={cn('skeleton', className)} />;
}
