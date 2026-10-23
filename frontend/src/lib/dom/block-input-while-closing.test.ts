import { waitFor } from '@testing-library/react';
import { blockInputWhileClosing } from './block-input-while-closing';

describe('blockInputWhileClosing', () => {
  it('makes closing content inert and stops observing on cleanup', async () => {
    const node = document.createElement('div');
    node.dataset.state = 'open';
    const cleanup = blockInputWhileClosing(node);
    expect(node).not.toHaveAttribute('inert');

    node.dataset.state = 'closed';
    await waitFor(() => expect(node).toHaveAttribute('inert'));

    node.dataset.state = 'open';
    await waitFor(() => expect(node).not.toHaveAttribute('inert'));

    cleanup?.();
    node.dataset.state = 'closed';
    await Promise.resolve();
    expect(node).not.toHaveAttribute('inert');
  });

  it('ignores the detach call React makes without a node', () => {
    expect(blockInputWhileClosing(null)).toBeUndefined();
  });
});
