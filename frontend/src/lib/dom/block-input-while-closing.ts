/**
 * Radix keeps a closing dialog mounted until its exit animation ends. During that window the
 * content must not take input, otherwise a form could still be submitted after the user dismissed
 * it. Used as a ref callback; the returned function is React's ref cleanup.
 */
export function blockInputWhileClosing(node: HTMLElement | null) {
  if (!node) return;
  const sync = () => node.toggleAttribute('inert', node.dataset.state === 'closed');
  const observer = new MutationObserver(sync);
  observer.observe(node, { attributes: true, attributeFilter: ['data-state'] });
  sync();
  return () => observer.disconnect();
}
