import { AuthTokenStore, SessionStorageError } from './token-store';
import { fixtureToken } from '@/test/fixtures';
describe('AuthTokenStore', () => {
  it('sets, reads and clears only tab storage', () => {
    const local = vi.spyOn(Storage.prototype, 'setItem');
    AuthTokenStore.set(fixtureToken);
    expect(AuthTokenStore.get()).toBe(fixtureToken);
    expect(local.mock.instances).toEqual([window.sessionStorage]);
    AuthTokenStore.clear();
    expect(AuthTokenStore.get()).toBeNull();
  });
  it('handles disabled storage explicitly when saving', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Denied');
    });
    expect(() => AuthTokenStore.set(fixtureToken)).toThrow(SessionStorageError);
  });
});
