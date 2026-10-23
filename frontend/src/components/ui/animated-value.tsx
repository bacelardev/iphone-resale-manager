import { useState } from 'react';
import { cn } from '@/lib/utils/cn';

/**
 * Text that replays a short rise whenever it changes. Its opacity never changes, so financial
 * values keep their full contrast during the update. The first render is static, and the element
 * is never remounted: alternating the animation name restarts it in place, so the surrounding
 * card and any live region only see the text change.
 */
export function AnimatedValue({ value, className }: { value: string; className?: string }) {
  const [state, setState] = useState({ value, changes: 0 });
  if (state.value !== value) setState({ value, changes: state.changes + 1 });
  return (
    <span
      className={cn('animated-value', className)}
      data-motion={state.changes === 0 ? undefined : state.changes % 2 ? 'a' : 'b'}
    >
      {value}
    </span>
  );
}
