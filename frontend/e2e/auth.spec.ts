import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;
if (!username || !password)
  throw new Error('Configure E2E_USERNAME e E2E_PASSWORD para o backend real de teste.');
const key = 'iphone-resale.access-token';
async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Usuário', { exact: true }).fill(username!);
  await page.getByLabel('Senha', { exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page).toHaveURL('/dashboard');
}

test('real login, CORS, session restoration, protected navigation and logout', async ({ page }) => {
  await login(page);
  expect(await page.evaluate((k) => !!sessionStorage.getItem(k), key)).toBe(true);
  expect(await page.evaluate((k) => localStorage.getItem(k) === null, key)).toBe(true);
  // Keep the token only in test memory; no traces, storage snapshots or credential output.
  const token = await page.evaluate((k) => sessionStorage.getItem(k), key);
  expect(
    await page
      .locator('body')
      .evaluate((body, value) => !body.textContent?.includes(value!), token),
  ).toBe(true);
  const restored = page.waitForResponse((response) => response.url().endsWith('/auth/me'));
  await page.reload();
  expect((await restored).status()).toBe(200);
  const dashboardHeading = page.getByRole('heading', { name: /Olá,/ });
  await expect(dashboardHeading).toBeVisible();
  await expect(dashboardHeading).toBeFocused();
  await expect(page).toHaveTitle(/Olá,.* · iPhone Resale/);
  await page.goto('/devices/new');
  await expect(page.getByRole('heading', { name: 'Novo aparelho' })).toBeVisible();
  await page.getByRole('button', { name: 'Sair', exact: true }).click();
  await expect(page).toHaveURL('/login');
  expect(await page.evaluate((k) => sessionStorage.getItem(k) === null, key)).toBe(true);
  // Revoked token is rejected by the actual backend, even when restored manually.
  await page.evaluate(({ k, value }) => sessionStorage.setItem(k, value!), {
    k: key,
    value: token,
  });
  await page.goto('/dashboard');
  await expect(page).toHaveURL('/login');
  expect(await page.evaluate((k) => sessionStorage.getItem(k) === null, key)).toBe(true);
});

test('invalid token redirects without flashing protected content', async ({ page }) => {
  await page.goto('/login');
  await page.evaluate((k) => sessionStorage.setItem(k, 'irs_invalid-test-token'), key);
  await page.goto('/financial');
  await expect(page).toHaveURL('/login');
  expect(await page.evaluate((k) => sessionStorage.getItem(k) === null, key)).toBe(true);
});

test('expired server-side session is rejected on refresh', async ({ page }) => {
  await login(page);
  const token = await page.evaluate((k) => sessionStorage.getItem(k), key);
  const hash = createHash('sha256').update(token!).digest('hex');
  // Isolated CI database only: insert an already-expired session under the existing immutable schema.
  // The original session cannot be edited, so use an independent test token for this fixture.
  const expiredToken = 'irs_' + createHash('sha256').update(hash).digest('base64url');
  const expiredHash = createHash('sha256').update(expiredToken).digest('hex');
  execFileSync(
    'psql',
    [
      '-v',
      'ON_ERROR_STOP=1',
      '-c',
      `insert into auth_session (user_id, token_hash, created_at, expires_at) select id, '${expiredHash}', now() - interval '2 hours', now() - interval '1 hour' from app_user where username = 'ci.socio'`,
    ],
    { stdio: 'pipe' },
  );
  await page.evaluate(({ k, value }) => sessionStorage.setItem(k, value), {
    k: key,
    value: expiredToken,
  });
  await page.reload();
  await expect(page).toHaveURL('/login');
});

test('offline restoration retains session and retry reconnects', async ({ page, context }) => {
  await login(page);
  await context.route('**/api/v1/**', (route) => route.abort('connectionrefused'));
  await page.reload();
  await expect(page.getByText(/Não foi possível conectar/)).toBeVisible();
  expect(await page.evaluate((k) => !!sessionStorage.getItem(k), key)).toBe(true);
  await context.unroute('**/api/v1/**');
  await page.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect(page.getByRole('heading', { name: /Olá,/ })).toBeVisible();
});

test('official visual baseline tokens are applied without a marketing hero', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 960 });
  await page.goto('/login');

  expect(await page.locator('.login-story').count()).toBe(0);
  expect(
    await page.evaluate(() => {
      const styles = getComputedStyle(document.documentElement);
      return {
        background: styles.getPropertyValue('--color-background').trim(),
        surface: styles.getPropertyValue('--color-surface').trim(),
        card: styles.getPropertyValue('--color-card').trim(),
        hover: styles.getPropertyValue('--color-hover').trim(),
        border: styles.getPropertyValue('--color-border').trim(),
      };
    }),
  ).toEqual({
    background: '#080808',
    surface: '#101010',
    card: '#151515',
    hover: '#1b1b1b',
    border: '#262626',
  });
  expect(
    await page.locator('.login-form-wrap').evaluate((el) => getComputedStyle(el).backgroundColor),
  ).toBe('rgb(16, 16, 16)');
  expect(
    await page.getByLabel('Usuário').evaluate((el) => getComputedStyle(el).backgroundColor),
  ).toBe('rgb(27, 27, 27)');
  expect(
    await page
      .getByRole('button', { name: 'Entrar', exact: true })
      .evaluate((el) => getComputedStyle(el).transitionDuration),
  ).toContain('0.15s');

  await login(page);
  expect(
    await page
      .locator('.module-card')
      .first()
      .evaluate((el) => getComputedStyle(el).backgroundColor),
  ).toBe('rgb(21, 21, 21)');
  expect(
    await page
      .locator('.module-card')
      .first()
      .evaluate((el) => getComputedStyle(el).transitionDuration),
  ).toContain('0.18s');
});

for (const width of [375, 430, 768, 1024, 1440]) {
  test(`responsive and accessible at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 960 });
    await page.goto('/login');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(
      (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
        .violations,
    ).toEqual([]);
    await page.screenshot({ path: `test-results/login-${width}.png`, fullPage: true });
    await login(page);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(
      (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
        .violations,
    ).toEqual([]);
    if (width < 1024) {
      await page.getByRole('button', { name: 'Abrir menu' }).click();
      const dialog = page.getByRole('dialog');
      await expect(dialog).toBeVisible();
      await expect(dialog).toHaveAttribute('data-placement', 'drawer');
      for (let i = 0; i < 8; i++) {
        await page.keyboard.press('Tab');
        expect(await dialog.evaluate((el) => el.contains(document.activeElement))).toBe(true);
      }
      await page.keyboard.press('Escape');
      await expect(dialog).not.toBeVisible();
      await expect(page.getByRole('button', { name: 'Abrir menu' })).toBeFocused();
    }
    await page.screenshot({ path: `test-results/dashboard-${width}.png`, fullPage: true });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    expect(
      await page
        .locator('.module-card')
        .first()
        .evaluate((el) => getComputedStyle(el).transitionDuration),
    ).toBe('0s');
    await page.getByRole('button', { name: 'Sair', exact: true }).click();
    await expect(page).toHaveURL('/login');
  });
}

test('real invalid credentials and rate limit remain contextual', async ({ page }) => {
  await page.goto('/login');
  for (let i = 0; i < 5; i++) {
    await page.getByLabel('Usuário', { exact: true }).fill(username!);
    await page.getByLabel('Senha', { exact: true }).fill('wrong-test-password');
    const request = page.waitForResponse(
      (response) =>
        response.url().endsWith('/auth/login') && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    expect((await request).status()).toBe(401);
    await expect(page.getByText('Usuário ou senha inválidos.')).toBeVisible();
  }
  await page.getByLabel('Senha', { exact: true }).fill('wrong-test-password');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByText(/Muitas tentativas/)).toBeVisible();
});
