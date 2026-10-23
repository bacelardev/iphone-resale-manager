import { hashKey, type QueryKey } from '@tanstack/react-query';

/**
 * placeholderData for paginated lists. While the user pages through the same scope, the previous
 * page stays on screen until the next one arrives. Any other change (filters, search, period)
 * returns undefined, so results that belong to a different selection are never shown as current.
 */
export function keepPreviousPage(queryKey: QueryKey, scopeOf: (key: QueryKey) => QueryKey) {
  const scope = hashKey(scopeOf(queryKey));
  return <T>(previous: T | undefined, previousQuery?: { queryKey: QueryKey }): T | undefined =>
    previousQuery && hashKey(scopeOf(previousQuery.queryKey)) === scope ? previous : undefined;
}

/** Scope of a key whose last segment is the page index. */
export function withoutLastSegment(key: QueryKey): QueryKey {
  return key.slice(0, -1);
}
