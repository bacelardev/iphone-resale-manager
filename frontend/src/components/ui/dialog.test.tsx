import { render, screen } from '@testing-library/react';
import {
  finishExitAnimation,
  flushMutations,
  simulateDialogAnimations,
} from '@/test/dialog-motion';
import { Dialog, DialogContent } from './dialog';

function TestDialog({ open }: { open: boolean }) {
  return (
    <Dialog open={open}>
      <DialogContent title="Confirmação" description="Revise antes de confirmar.">
        <button type="button">Confirmar</button>
      </DialogContent>
    </Dialog>
  );
}

const overlay = () => document.querySelector('.dialog-overlay');

describe('DialogContent exit', () => {
  it('stays mounted and inert while closing, then unmounts and stops observing', async () => {
    simulateDialogAnimations();
    const disconnect = vi.spyOn(MutationObserver.prototype, 'disconnect');
    const { rerender } = render(<TestDialog open />);
    const dialog = screen.getByRole('dialog', { name: 'Confirmação' });
    await flushMutations();
    expect(dialog).not.toHaveAttribute('inert');

    rerender(<TestDialog open={false} />);
    await flushMutations();
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute('data-state', 'closed');
    expect(dialog).toHaveAttribute('inert');

    finishExitAnimation(dialog);
    finishExitAnimation(overlay()!);
    expect(dialog).not.toBeInTheDocument();
    expect(overlay()).toBeNull();
    expect(disconnect).toHaveBeenCalled();
  });

  it('becomes interactive again when reopened during its exit', async () => {
    simulateDialogAnimations();
    const { rerender } = render(<TestDialog open />);
    const dialog = screen.getByRole('dialog', { name: 'Confirmação' });

    rerender(<TestDialog open={false} />);
    await flushMutations();
    expect(dialog).toHaveAttribute('inert');

    rerender(<TestDialog open />);
    await flushMutations();
    expect(screen.getByRole('dialog', { name: 'Confirmação' })).toBe(dialog);
    expect(dialog).toHaveAttribute('data-state', 'open');
    expect(dialog).not.toHaveAttribute('inert');
  });

  it('opens a fresh, interactive dialog after a completed exit', async () => {
    simulateDialogAnimations();
    const { rerender } = render(<TestDialog open />);
    const first = screen.getByRole('dialog', { name: 'Confirmação' });
    rerender(<TestDialog open={false} />);
    await flushMutations();
    finishExitAnimation(first);
    finishExitAnimation(overlay()!);

    rerender(<TestDialog open />);
    await flushMutations();
    const second = screen.getByRole('dialog', { name: 'Confirmação' });
    expect(second).not.toBe(first);
    expect(second).not.toHaveAttribute('inert');
    expect(screen.getByRole('button', { name: 'Confirmar' })).toBeEnabled();
  });

  it('unmounts at once when no exit animation applies, as with reduced motion', () => {
    const { rerender } = render(<TestDialog open />);
    rerender(<TestDialog open={false} />);
    expect(screen.queryByRole('dialog', { name: 'Confirmação' })).not.toBeInTheDocument();
  });
});
