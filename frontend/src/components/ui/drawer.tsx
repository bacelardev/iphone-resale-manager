import type { ComponentProps } from 'react';
import { Dialog, DialogContent, DialogTrigger, type DialogContentProps } from './dialog';

export function Drawer(props: ComponentProps<typeof Dialog>) {
  return <Dialog {...props} />;
}

export function DrawerTrigger(props: ComponentProps<typeof DialogTrigger>) {
  return <DialogTrigger {...props} />;
}

export function DrawerContent(props: Omit<DialogContentProps, 'placement'>) {
  return <DialogContent placement="drawer" {...props} />;
}
