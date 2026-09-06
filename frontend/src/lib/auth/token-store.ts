const KEY = 'iphone-resale.access-token';

export class SessionStorageError extends Error {
  constructor() {
    super('O navegador não permitiu guardar a sessão nesta aba.');
  }
}

// The only browser-storage boundary. Tokens are never persisted beyond this tab.
export const AuthTokenStore = {
  get(): string | null {
    try {
      return window.sessionStorage.getItem(KEY);
    } catch {
      return null;
    }
  },
  set(token: string): void {
    try {
      window.sessionStorage.setItem(KEY, token);
    } catch {
      throw new SessionStorageError();
    }
  },
  clear(): void {
    try {
      window.sessionStorage.removeItem(KEY);
    } catch {
      /* Storage is unavailable. */
    }
  },
};
