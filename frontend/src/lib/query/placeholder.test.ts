import { keepPreviousPage, withoutLastSegment } from './placeholder';

describe('keepPreviousPage', () => {
  const previous = { content: ['page 0'] };

  it('keeps the previous page while paging inside the same filters', () => {
    const placeholder = keepPreviousPage(['users', 'ana', 'true', 1], withoutLastSegment);
    expect(placeholder(previous, { queryKey: ['users', 'ana', 'true', 0] })).toBe(previous);
  });

  it('never shows results of a different filter, search or period', () => {
    const placeholder = keepPreviousPage(['users', 'ana', 'true', 0], withoutLastSegment);
    expect(placeholder(previous, { queryKey: ['users', 'bruno', 'true', 0] })).toBeUndefined();
    expect(placeholder(previous, { queryKey: ['users', 'ana', 'false', 0] })).toBeUndefined();
  });

  it('does not treat keys of different queries as the same scope', () => {
    const placeholder = keepPreviousPage(['users', 'ana', '', 1], withoutLastSegment);
    expect(placeholder(previous, { queryKey: ['audit', 'ana', '', 0] })).toBeUndefined();
    expect(placeholder(previous, { queryKey: ['users', 'ana', 0] })).toBeUndefined();
  });

  it('has nothing to keep on the first load', () => {
    const placeholder = keepPreviousPage(['users', '', '', 0], withoutLastSegment);
    expect(placeholder(undefined, undefined)).toBeUndefined();
  });

  it('supports scopes stored inside a filters object', () => {
    const scopeOf = (key: readonly unknown[]) => [
      key[0],
      { ...(key[1] as object), page: undefined },
    ];
    const placeholder = keepPreviousPage(['devices', { archived: false, page: 2 }], scopeOf);
    expect(placeholder(previous, { queryKey: ['devices', { archived: false, page: 1 }] })).toBe(
      previous,
    );
    expect(
      placeholder(previous, { queryKey: ['devices', { archived: true, page: 1 }] }),
    ).toBeUndefined();
  });
});
