import { useState } from 'react';

/**
 * Returns the latest non-null value. A dialog closes as soon as its value is cleared, but stays
 * mounted while it animates out; rendering the retained value keeps its content from emptying
 * during that exit.
 */
export function useRetainedValue<T>(value: T | null | undefined): T | null | undefined {
  const [retained, setRetained] = useState(value);
  if (value != null && value !== retained) setRetained(value);
  return value ?? retained;
}
