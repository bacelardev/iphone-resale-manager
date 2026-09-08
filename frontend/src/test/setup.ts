import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import { AuthTokenStore } from '@/lib/auth/token-store';
afterEach(() => {
  cleanup();
  AuthTokenStore.clear();
  vi.unstubAllGlobals();
});
