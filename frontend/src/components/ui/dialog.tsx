import type { ComponentProps, ReactNode } from 'react';
import * as Primitive from '@radix-ui/react-dialog';
import { IconX } from '@tabler/icons-react';
import { cn } from '@/lib/utils/cn';
import { Button } from './button';

export function Dialog({ children, ...props }: ComponentProps<typeof Primitive.Root>) {
  return <Primitive.Root {...props}>{children}</Primitive.Root>;
}
export function DialogTrigger({ children }: { children: ReactNode }) {
  return <Primitive.Trigger asChild>{children}</Primitive.Trigger>;
}
export function DialogContent({
  title,
  description,
  children,
  drawer = false,
}: {
  title: string;
  description: string;
  children: ReactNode;
  drawer?: boolean;
}) {
  return (
    <Primitive.Portal>
      <Primitive.Overlay className="dialog-overlay" />
      <Primitive.Content className={cn('dialog-content', drawer && 'dialog-drawer')}>
        <Primitive.Title className="dialog-title">{title}</Primitive.Title>
        <Primitive.Description className="dialog-description">{description}</Primitive.Description>
        {children}
        <Primitive.Close asChild>
          <Button className="dialog-close" variant="ghost" size="icon" aria-label="Fechar menu">
            <IconX size={20} aria-hidden />
          </Button>
        </Primitive.Close>
      </Primitive.Content>
    </Primitive.Portal>
  );
}
