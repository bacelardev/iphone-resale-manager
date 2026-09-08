import type { ComponentProps } from 'react';
import { Dialog, DialogContent, DialogTrigger, type DialogContentProps } from './dialog';

export function BottomSheet(props: ComponentProps<typeof Dialog>) {
  return <Dialog {...props} />;
}

export function BottomSheetTrigger(props: ComponentProps<typeof DialogTrigger>) {
  return <DialogTrigger {...props} />;
}

export function BottomSheetContent(props: Omit<DialogContentProps, 'placement'>) {
  return <DialogContent placement="bottom-sheet" {...props} />;
}
