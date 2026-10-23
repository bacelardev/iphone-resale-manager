import { act } from '@testing-library/react';

/** Entry and exit animation names that `globals.css` gives each Radix dialog part. */
const animations: Record<string, { open: string; closed: string }> = {
  'dialog-overlay': { open: 'fade-in', closed: 'fade-out' },
  'dialog-content': { open: 'modal-in', closed: 'modal-out' },
};

/**
 * jsdom applies no stylesheet animations, so Radix Presence would unmount a closing dialog at
 * once. This reports the animation names the real CSS produces for the current `data-state`,
 * keeping a closing dialog mounted until `finishExitAnimation` ends its exit. The spy is restored
 * after each test by `restoreMocks`.
 */
export function simulateDialogAnimations() {
  const original = globalThis.getComputedStyle.bind(globalThis);
  return vi
    .spyOn(globalThis, 'getComputedStyle')
    .mockImplementation((element: Element, pseudo?: string | null) => {
      const styles = original(element, pseudo);
      const part = Object.keys(animations).find((name) => element.classList.contains(name));
      if (!part) return styles;
      const names = animations[part]!;
      return new Proxy(styles, {
        get(target, property) {
          if (property === 'animationName') {
            return (element as HTMLElement).dataset.state === 'closed' ? names.closed : names.open;
          }
          const value: unknown = Reflect.get(target, property, target);
          return typeof value === 'function' ? value.bind(target) : value;
        },
      });
    });
}

/** Ends the exit animation of a closing dialog part, as the browser does after its duration. */
export function finishExitAnimation(element: Element) {
  const part = Object.keys(animations).find((name) => element.classList.contains(name));
  if (!part) throw new Error('Not an animated dialog part.');
  const event = new Event('animationend', { bubbles: true });
  Object.defineProperty(event, 'animationName', { value: animations[part]!.closed });
  act(() => {
    element.dispatchEvent(event);
  });
}

/** Runs pending MutationObserver callbacks; they are queued as microtasks by the DOM mutation. */
export async function flushMutations() {
  await act(async () => {
    await Promise.resolve();
  });
}
