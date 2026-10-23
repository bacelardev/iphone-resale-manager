import { renderHook } from '@testing-library/react';
import { useRetainedValue } from './use-retained-value';

describe('useRetainedValue', () => {
  it('follows the current value and keeps the last one after it is cleared', () => {
    const { result, rerender } = renderHook(({ value }) => useRetainedValue(value), {
      initialProps: { value: null as string | null },
    });
    expect(result.current).toBeNull();

    rerender({ value: 'first' });
    expect(result.current).toBe('first');

    rerender({ value: null });
    expect(result.current).toBe('first');

    rerender({ value: 'second' });
    expect(result.current).toBe('second');
  });
});
