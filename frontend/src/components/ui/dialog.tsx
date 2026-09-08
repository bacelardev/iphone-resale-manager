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

export type DialogContentProps = {
  title: string;
  description: string;
  children: ReactNode;
  placement?: 'center' | 'drawer' | 'bottom-sheet';
};

export function DialogContent({
  title,
  description,
  children,
  placement = 'center',
}: DialogContentProps) {
  return (
    <Primitive.Portal>
      <Primitive.Overlay className="dialog-overlay" />
      <Primitive.Content
        className={cn(
          'dialog-content',
          placement === 'drawer' && 'dialog-drawer',
          placement === 'bottom-sheet' && 'dialog-sheet',
        )}
        data-placement={placement}
      >
        <Primitive.Title className="dialog-title">{title}</Primitive.Title>
        <Primitive.Description className="dialog-description">{description}</Primitive.Description>
        {children}
        <Primitive.Close asChild>
          <Button className="dialog-close" variant="ghost" size="icon" aria-label="Fechar">
            <IconX size={20} aria-hidden />
          </Button>
        </Primitive.Close>
      </Primitive.Content>
    </Primitive.Portal>
  );
}
