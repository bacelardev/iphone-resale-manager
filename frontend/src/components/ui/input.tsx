import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils/cn';
export function Input({ className, ...props }: ComponentProps<'input'>) {
  return <input className={cn('input', className)} {...props} />;
}
