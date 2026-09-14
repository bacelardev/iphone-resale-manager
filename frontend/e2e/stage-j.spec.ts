import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const tokenKey = 'iphone-resale.access-token';
const token = `irs_${createHash('sha256').update(`stage-j-${Date.now()}`).digest('base64url')}`;
const tokenHash = createHash('sha256').update(token).digest('hex');
const contributionDescription = `Aporte E2E J ${Date.now()}`;
const withdrawalDescription = `Retirada E2E J ${Date.now()}`;
const adjustmentDescription = `Ajuste E2E J ${Date.now()}`;
let ownerId = '';

function localDateTime(date = new Date()) {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

async function authenticated(page: Page, path: string) {
  await page.goto('/login');
  await page.evaluate(({ key, value }) => sessionStorage.setItem(key, value), {
    key: tokenKey,
    value: token,
  });
  await page.goto(path);
  await expect(page).not.toHaveURL('/login');
}

async function get(request: APIRequestContext, path: string) {
  const response = await request.get(`http://localhost:8080/api/v1${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok(), `${path}: HTTP ${response.status()}`).toBe(true);
  return response.json();
}

async function post(request: APIRequestContext, path: string, data: unknown) {
  const response = await request.post(`http://localhost:8080/api/v1${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    data,
  });
  return response;
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

async function openMovement(page: Page, name: 'Aporte' | 'Retirada' | 'Ajuste') {
  await page.getByRole('button', { name, exact: true }).click();
  return page.getByRole('dialog');
}

async function fillMovement(
  page: Page,
  dialog: ReturnType<Page['getByRole']>,
  amount: string,
  description: string,
) {
  await dialog.getByLabel('Valor').fill(amount);
  await dialog.getByLabel('Data e hora').fill(localDateTime());
  await dialog.getByLabel('Descrição').fill(description);
  await dialog.getByRole('button', { name: 'Confirmar' }).click();
  await expect(dialog).not.toBeVisible();
  await expect(page.getByText(description, { exact: true })).toBeVisible();
}

test.describe.serial('Etapa J com backend e PostgreSQL reais — 18 fluxos', () => {
  test.beforeAll(async ({ request }) => {
    execFileSync(
      'psql',
      [
        '-v',
        'ON_ERROR_STOP=1',
        '-c',
        `insert into auth_session (user_id, token_hash, created_at, expires_at) select id, '${tokenHash}', now(), now() + interval '2 hours' from app_user where username = 'ci.socio'`,
      ],
      { stdio: 'pipe' },
    );
    const me = await get(request, '/auth/me');
    ownerId = me.id;
    const initialization = await get(request, '/business-initialization');
    if (initialization.status === 'NOT_STARTED') {
      const response = await post(request, '/business-initialization/start', {
        cutoffAt: new Date(Date.now() - 3_600_000).toISOString(),
      });
      expect(response.status()).toBe(201);
    }
  });

  test('1. login exibe o branding Delarte Control', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByText(/Delarte Control/).first()).toBeVisible();
    await expect(page).toHaveTitle(/Delarte Control/);
    await page.screenshot({ path: 'test-results/stage-j-login.png', fullPage: true });
  });

  test('2. fluxo PREPARING orienta a conclusão da implantação', async ({ page }) => {
    await authenticated(page, '/financial');
    await expect(
      page.getByRole('heading', { name: 'Concluir configuração inicial' }),
    ).toBeVisible();
    await expect(page.getByText(/Após concluir, aparelhos e manutenções históricas/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Novo movimento' })).not.toBeVisible();
  });

  test('3. preview separa estoque e caixa antes da conclusão', async ({ page }) => {
    await authenticated(page, '/financial');
    const preview = page.locator('.completion-preview');
    await expect(preview.getByText('Data de corte', { exact: true })).toBeVisible();
    await expect(preview.getByText('Aparelhos importados', { exact: true })).toBeVisible();
    await expect(preview.getByText('Capital em estoque', { exact: true })).toBeVisible();
    await page.screenshot({
      path: 'test-results/stage-j-initialization-preview.png',
      fullPage: true,
    });
  });

  test('4. conclui implantação com saldo inicial e capital histórico informativo', async ({
    page,
  }) => {
    await authenticated(page, '/financial');
    await page.getByLabel('Caixa real na data de corte').fill('4000.00');
    await page.getByLabel('Aportes históricos (informativo)').fill('9000.00');
    await page.getByLabel('Retiradas históricas (informativo)').fill('1000.00');
    await page.getByRole('button', { name: 'Concluir implantação' }).click();
    await expect(
      page.getByRole('heading', { name: 'Concluir configuração inicial' }),
    ).not.toBeVisible();
    await expect(page.getByText('Saldo inicial', { exact: true })).toBeVisible();
    await expect(page.getByText('R$ 4.000,00', { exact: true }).first()).toBeVisible();
  });

  test('5. bloqueia importações históricas depois de COMPLETED', async ({ page }) => {
    await authenticated(page, '/devices/import');
    await expect(page.getByRole('heading', { name: 'Preparação necessária' })).toBeVisible();
    await expect(
      page.getByRole('button', { name: 'Importar aparelho existente' }),
    ).not.toBeVisible();
  });

  test('6. financeiro abre com resumo e ledger reais', async ({ page }) => {
    await authenticated(page, '/financial');
    await expect(page.getByRole('heading', { name: 'Financeiro' })).toBeVisible();
    await expect(page.getByText('Saldo final do período', { exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Movimentações' })).toBeVisible();
    await expect(page.getByText('Desenvolvido por Andelar', { exact: true })).toBeVisible();
    await page.screenshot({ path: 'test-results/stage-j-financial.png', fullPage: true });
  });

  test('7. registra aporte com sócio e autor explícitos', async ({ page }) => {
    await authenticated(page, '/financial');
    const dialog = await openMovement(page, 'Aporte');
    await expect(dialog.getByRole('heading', { name: 'Registrar aporte' })).toBeVisible();
    await fillMovement(page, dialog, '500.00', contributionDescription);
    const row = page.locator('.transaction-row').filter({ hasText: contributionDescription });
    await expect(row).toContainText('Sócio relacionado: Sócio CI');
    await expect(row).toContainText('Registrado por: Sócio CI');
  });

  test('8. registra retirada mesmo quando reduz o caixa', async ({ page }) => {
    await authenticated(page, '/financial');
    const dialog = await openMovement(page, 'Retirada');
    await fillMovement(page, dialog, '5000.00', withdrawalDescription);
    const row = page.locator('.transaction-row').filter({ hasText: withdrawalDescription });
    await expect(row).toContainText('Retirada');
    await expect(row.getByLabel('Saída')).toBeVisible();
  });

  test('9. registra ajuste manual com direção escolhida', async ({ page }) => {
    await authenticated(page, '/financial');
    const dialog = await openMovement(page, 'Ajuste');
    await dialog.getByLabel('Direção').selectOption('INFLOW');
    await fillMovement(page, dialog, '250.00', adjustmentDescription);
    const row = page.locator('.transaction-row').filter({ hasText: adjustmentDescription });
    await expect(row).toContainText('Ajuste manual');
    await expect(row.getByLabel('Entrada')).toBeVisible();
  });

  test('10. estorna manualmente uma movimentação elegível uma única vez', async ({ page }) => {
    await authenticated(page, '/financial');
    const row = page.locator('.transaction-row').filter({ hasText: contributionDescription });
    await row.getByRole('button', { name: 'Estornar movimentação' }).click();
    const dialog = page.getByRole('dialog', { name: 'Estornar movimentação' });
    await dialog.getByLabel('Data e hora').fill(localDateTime());
    await dialog.getByLabel('Descrição').fill('Estorno E2E J do aporte.');
    await dialog.getByRole('button', { name: 'Confirmar' }).click();
    await expect(dialog).not.toBeVisible();
    await expect(row.getByRole('button', { name: 'Estornar movimentação' })).not.toBeVisible();
    await expect(page.getByText('Estorno E2E J do aporte.', { exact: true })).toBeVisible();
  });

  test('11. filtra transações por tipo e direção', async ({ page }) => {
    await authenticated(page, '/financial');
    await page.getByLabel('Tipo').selectOption('OWNER_WITHDRAWAL');
    await page.getByLabel('Direção').selectOption('OUTFLOW');
    await expect(page.getByText(withdrawalDescription, { exact: true })).toBeVisible();
    await expect(page.getByText(contributionDescription, { exact: true })).not.toBeVisible();
  });

  test('12. troca períodos predefinidos e personalizado inclusivo', async ({ page }) => {
    await authenticated(page, '/financial');
    for (const name of ['Hoje', 'Semana', 'Ano']) {
      await page.getByRole('button', { name, exact: true }).click();
      await expect(page.getByRole('button', { name, exact: true })).toHaveAttribute(
        'aria-pressed',
        'true',
      );
    }
    await page.getByRole('button', { name: 'Personalizado' }).click();
    const today = new Date().toISOString().slice(0, 10);
    await page.getByLabel('De').fill(today);
    await page.getByLabel('Até').fill(today);
    await expect(page.getByLabel('Até')).toHaveValue(today);
  });

  test('13. dashboard reflete os dados financeiros e de estoque', async ({ page }) => {
    await authenticated(page, '/');
    await expect(page.getByText('Saldo atual', { exact: true })).toBeVisible();
    await expect(page.getByText('Faturamento do mês', { exact: true })).toBeVisible();
    await expect(page.getByText('Capital em estoque', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: /Financeiro/ })).toBeVisible();
    await page.screenshot({ path: 'test-results/stage-j-dashboard.png', fullPage: true });
  });

  test('14. mantém as rotas J responsivas e sem overflow nas cinco larguras', async ({ page }) => {
    for (const width of [375, 430, 768, 1024, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      await authenticated(page, '/financial');
      await expect(page.getByRole('heading', { name: 'Financeiro' })).toBeVisible();
      await noOverflow(page);
      await page.screenshot({
        path: `test-results/stage-j-financial-${width}.png`,
        fullPage: true,
      });
      await authenticated(page, '/');
      await noOverflow(page);
    }
  });

  test('15. permite abrir operações somente por teclado', async ({ page }) => {
    await authenticated(page, '/financial');
    const trigger = page.getByRole('button', { name: 'Retirada', exact: true });
    await trigger.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('dialog', { name: 'Registrar retirada' })).toBeVisible();
  });

  test('16. mantém o foco preso no dialog e usa bottom sheet no mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await authenticated(page, '/financial');
    await page.getByRole('button', { name: 'Aporte', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Registrar aporte' });
    await expect(dialog).toHaveCSS('position', 'fixed');
    for (let index = 0; index < 6; index += 1) {
      await page.keyboard.press('Tab');
      await expect
        .poll(() => dialog.evaluate((element) => element.contains(document.activeElement)))
        .toBe(true);
    }
  });

  test('17. Escape fecha o dialog e devolve foco ao acionador', async ({ page }) => {
    await authenticated(page, '/financial');
    const trigger = page.getByRole('button', { name: 'Ajuste', exact: true });
    await trigger.click();
    const dialog = page.getByRole('dialog', { name: 'Registrar ajuste' });
    await expect(dialog).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    await expect(trigger).toBeFocused();
  });

  test('18. não apresenta violações axe nas rotas da Etapa J', async ({ page }) => {
    await page.goto('/login');
    await noAxeViolations(page);
    for (const path of ['/financial', '/']) {
      await authenticated(page, path);
      await noAxeViolations(page);
    }
  });

  test.afterAll(async ({ request }) => {
    const initialization = await get(request, '/business-initialization');
    expect(initialization.status).toBe('COMPLETED');
    const response = await post(request, '/financial/contributions', {
      ownerUserId: ownerId,
      amount: 0,
      occurredAt: new Date().toISOString(),
      description: 'Validação final de contrato.',
    });
    expect(response.status()).toBe(400);
  });
});
