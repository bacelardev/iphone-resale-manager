import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const tokenKey = 'iphone-resale.access-token';
const suffix = Date.now().toString(36);
const token = `irs_${createHash('sha256').update(`stage-k-${suffix}`).digest('base64url')}`;
const tokenHash = createHash('sha256').update(token).digest('hex');
const auditSummary = `Evento E2E K ${suffix}`;
const userName = `Pessoa E2E K ${suffix}`;
const username = `e2e.k.${suffix}`;

async function authenticated(page: Page, path: string) {
  await page.goto('/login');
  await page.evaluate(({ key, value }) => sessionStorage.setItem(key, value), {
    key: tokenKey,
    value: token,
  });
  await page.goto(path);
  await expect(page).not.toHaveURL('/login');
}

async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
}

async function noAxeViolations(page: Page) {
  expect(
    (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
      .violations,
  ).toEqual([]);
}

test.describe.serial('Etapa K com backend e PostgreSQL reais — 4 fluxos', () => {
  test.beforeAll(() => {
    execFileSync(
      'psql',
      [
        '-v',
        'ON_ERROR_STOP=1',
        '-c',
        `insert into auth_session (user_id, token_hash, created_at, expires_at)
         select id, '${tokenHash}', now(), now() + interval '2 hours'
         from app_user where username = 'ci.socio'`,
        '-c',
        `insert into audit_log
           (occurred_at, actor_user_id, action, entity_type, entity_id, entity_reference, summary, changes, request_id)
         select now(), id, 'UPDATED', 'USER', '${randomUUID()}', 'e2e-k', '${auditSummary}',
                '{"publicField":"visible","passwordHash":"must-not-leak"}'::jsonb, '${randomUUID()}'
         from app_user where username = 'ci.socio'`,
      ],
      { stdio: 'pipe' },
    );
  });

  test('1. histórico filtra, detalha e não expõe segredo', async ({ page }) => {
    await authenticated(page, '/history');
    await expect(page.getByRole('heading', { name: 'Histórico' })).toBeVisible();
    await page.getByLabel('Área').selectOption('USER');
    await page.getByLabel('Ação').selectOption('UPDATED');

    const event = page.getByRole('button', { name: new RegExp(auditSummary) });
    await expect(event).toBeVisible();
    await event.click();

    const dialog = page.getByRole('dialog', { name: 'Detalhes do evento' });
    await expect(dialog).toContainText('publicField');
    await expect(dialog).toContainText('visible');
    await expect(dialog).not.toContainText('must-not-leak');
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
  });

  test('2. cria usuário e o encontra pela busca', async ({ page }) => {
    await authenticated(page, '/settings/users');
    await expect(page.getByRole('heading', { name: 'Usuários' })).toBeVisible();
    await page.getByRole('button', { name: 'Novo usuário' }).click();

    const dialog = page.getByRole('dialog', { name: 'Novo usuário' });
    await dialog.getByLabel('Nome').fill(userName);
    await dialog.getByLabel('Usuário').fill(username);
    await dialog.getByLabel('Senha inicial').fill('Senha-E2E-K-2026!');
    await dialog.getByRole('button', { name: 'Salvar' }).click();
    await expect(dialog).not.toBeVisible();

    await page.getByLabel('Buscar').fill(username);
    await expect(page.getByText(userName, { exact: true })).toBeVisible();
    await expect(page.getByText(`@${username} · SOCIO`, { exact: true })).toBeVisible();
  });

  test('3. páginas K são responsivas e sem overflow', async ({ page }) => {
    for (const width of [375, 768, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      await authenticated(page, '/history');
      await expect(page.getByRole('heading', { name: 'Histórico' })).toBeVisible();
      await noOverflow(page);
      await page.screenshot({ path: `test-results/stage-k-history-${width}.png`, fullPage: true });

      await authenticated(page, '/settings/users');
      await expect(page.getByRole('heading', { name: 'Usuários' })).toBeVisible();
      await noOverflow(page);
      await page.screenshot({ path: `test-results/stage-k-users-${width}.png`, fullPage: true });
    }
  });

  test('4. páginas K atendem WCAG A/AA automatizado', async ({ page }) => {
    for (const path of ['/history', '/settings/users']) {
      await authenticated(page, path);
      await noAxeViolations(page);
    }
  });
});
