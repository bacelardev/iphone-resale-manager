import type { ComponentProps } from 'react';
import { IconChevronDown } from '@tabler/icons-react';
import { cn } from '@/lib/utils/cn';

export function Select({ className, children, ...props }: ComponentProps<'select'>) {
  return (
    <span className="select-shell">
      <select className={cn('select', className)} {...props}>
        {children}
      </select>
      <IconChevronDown className="select-icon" size={18} aria-hidden />
    </span>
  );
}
