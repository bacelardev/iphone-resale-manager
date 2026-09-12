import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const tokenKey = 'iphone-resale.access-token';
const token = `irs_${createHash('sha256').update(`stage-i-browser-session-${Date.now()}`).digest('base64url')}`;
const tokenHash = createHash('sha256').update(token).digest('hex');
const suffix = createHash('sha256')
  .update(`stage-i-${Date.now()}`)
  .digest('hex')
  .slice(0, 8)
  .toUpperCase();
const modelName = `iPhone Venda ${suffix}`;
const colorName = `Titânio Venda ${suffix}`;
const partName = `Reparo Venda ${suffix}`;
let partId = '';
let cutoff = new Date();
let profitableDeviceId = '';
let chronologyDeviceId = '';
let importedDeviceId = '';

const frontPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAEAAAABAEAIAAAB1mzrKAAAAIGNIUk0AAHomAACAhAAA+gAAAIDoAAB1MAAA6mAAADqYAAAXcJy6UTwAAAAGYktHRP///////wlY99wAAAAHdElNRQfqCQkRHB99iCZQAAABH0lEQVR42u3cQQ3DQAxE0YxlArv3qkDKISASDCXREgyEwuhhQfxD/kMQaeW1PWmax2Pfr2sTpDNy5ks/xn11Zp3bh36M+7ICYB4AzCsI1hk5rABOZ9S5eQCYzrQHkDoj9gBQZ5QVAFoV4AFgVg/wCsI4BcG63IRRbsIwN2GYYRxsXUEeAGZVgD0A4xgKM4yDGcbBDONgnWkYR3IKgjkFwTrTKIKUV72fv6Yf474cQ2EuYjCbMMwxFGYFwOwBMKcgmGEcrDNz2AM4vpSHrSnIHoBxCoLZhGGOobAuryCUQTMMA7mb0NhLmKwzjCKIHVm2QNAfqYKcxOG+Z0wzCsI5hUEM4yD+T4AZhgH6/JLeZT/FQEzjIO5B8AM42BWAOwPfT1DsjWEGX0AAAAASUVORK5CYII=',
  'base64',
);
const backPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAEAAAABAEAIAAAB1mzrKAAAAIGNIUk0AAHomAACAhAAA+gAAAIDoAAB1MAAA6mAAADqYAAAXcJy6UTwAAAAGYktHRP///////wlY99wAAAAHdElNRQfqCQkRHB99iCZQAAABDElEQVR42u3cwQ2DMBQEUdbaFuwuQgFISWW0TA85uIg5MK8CJGTW+y2T+76u5zkEaWbOfOnHeK9m5Tx8AZhm5jx+9GO8V7PycQVwmjnMAND+BPkCMB2GMMoQhjXLHkBq5vATBDKEYfYAmLMg2J4FuQvCmAEwd0Ewx9EwQxi2Z0GGMKaZ9gCSuyBYszyQIbkCYDZhmCsAZg+ANctRBMkeADOEYWYArMNdEMpxNMwDGZgZAHMXBLMHwAxhmBc0YE5DYYYwzBUAM4RhHRYxVDOHPQBkCMMMYZi3JGGuAJjnATBDGOaBDMx7wjBDGOYLgHV4QQPl/4JgjqNhZgDMHgCzB8D2LMgQxhjCMEMY9gcPUCR+86A+xQAAAABJRU5ErkJggg==',
  'base64',
);

function localDateTime(date: Date) {
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

async function post(request: APIRequestContext, path: string, data: unknown) {
  const response = await request.post(`http://localhost:8080/api/v1${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    data,
  });
  expect(response.ok(), `${path}: HTTP ${response.status()}`).toBe(true);
  return response.json();
}

async function get(request: APIRequestContext, path: string) {
  const response = await request.get(`http://localhost:8080/api/v1${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok(), `${path}: HTTP ${response.status()}`).toBe(true);
  return response.json();
}

async function selectCatalog(page: Page, label: string, name: string) {
  await page.getByRole('button', { name: new RegExp(`Selecionar ${label}`, 'i') }).click();
  await page.getByRole('combobox', { name: new RegExp(`Buscar ${label}`, 'i') }).fill(name);
  await page.getByRole('option', { name: new RegExp(name) }).click();
}

async function createDevice(page: Page, purchasedAt: Date, imported = false) {
  await authenticated(page, imported ? '/devices/import' : '/devices/new');
  await selectCatalog(page, 'modelo', modelName);
  await selectCatalog(page, 'cor', colorName);
  await page.getByLabel('Preço de compra (R$)').fill(imported ? '1500.00' : '1800.00');
  await page.getByLabel('Data e hora da compra').fill(localDateTime(purchasedAt));
  await page.getByLabel('Saúde da bateria').fill('91');
  await page.locator('#device-photos').setInputFiles([
    { name: 'frente-i.png', mimeType: 'image/png', buffer: frontPng },
    { name: 'traseira-i.png', mimeType: 'image/png', buffer: backPng },
  ]);
  await page
    .getByRole('button', {
      name: imported ? 'Importar aparelho existente' : 'Cadastrar novo aparelho',
    })
    .click();
  await expect(page).toHaveURL(/\/devices\/[0-9a-f-]+$/);
  const id = page.url().split('/').pop()!;
  await page.getByRole('button', { name: 'Marcar disponível' }).click();
  await expect(page.getByText('Disponível para venda', { exact: true })).toBeVisible();
  return id;
}

test.describe.serial('Etapa I com backend e PostgreSQL reais', () => {
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
    await post(request, '/models', {
      code: `IPHONE_I_${suffix}`,
      name: modelName,
      displayOrder: 9,
    });
    await post(request, '/colors', { code: `TITANIUM_I_${suffix}`, name: colorName });
    const part = await post(request, '/parts', { code: `SALE_I_${suffix}`, name: partName });
    partId = part.id;
    let initialization = await get(request, '/business-initialization');
    if (initialization.status === 'NOT_STARTED') {
      initialization = await post(request, '/business-initialization/start', {
        cutoffAt: new Date(Date.now() - 3_600_000).toISOString(),
      });
    }
    cutoff = new Date(initialization.cutoffAt);
  });

  test('prepara aparelho disponível com manutenção ativa', async ({ page, request }) => {
    profitableDeviceId = await createDevice(page, new Date(cutoff.getTime() + 60_000));
    await post(request, `/devices/${profitableDeviceId}/maintenances`, {
      performedAt: new Date(cutoff.getTime() + 120_000).toISOString(),
      items: [{ partId, cost: 250 }],
    });
    await authenticated(page, `/devices/${profitableDeviceId}`);
    await expect(page.getByRole('link', { name: 'Registrar venda' })).toBeVisible();
  });

  test('mostra investimento, lucro e margem antes da confirmação', async ({ page }) => {
    await authenticated(page, `/devices/${profitableDeviceId}/sale/new`);
    await page.getByLabel('Preço de venda (R$)').fill('3200.00');
    await page
      .getByLabel('Data e hora da venda')
      .fill(localDateTime(new Date(cutoff.getTime() + 180_000)));
    await expect(page.getByText('R$ 2.050,00', { exact: true })).toBeVisible();
    await expect(page.getByText('R$ 1.150,00', { exact: true })).toBeVisible();
    await expect(page.getByText('35,94%', { exact: true })).toBeVisible();
  });

  test('registra venda, muda para VENDIDO e exibe valores oficiais', async ({ page, request }) => {
    await authenticated(page, `/devices/${profitableDeviceId}/sale/new`);
    await page.getByLabel('Preço de venda (R$)').fill('3200.00');
    await page
      .getByLabel('Data e hora da venda')
      .fill(localDateTime(new Date(cutoff.getTime() + 180_000)));
    await page.getByRole('button', { name: 'Confirmar venda' }).click();
    await expect(page).toHaveURL(`/devices/${profitableDeviceId}/sale`);
    await expect(page.getByRole('heading', { name: 'Detalhes da venda' })).toBeVisible();
    await expect(page.getByText('Entrada financeira gerada', { exact: true })).toBeVisible();
    await expect(page.getByText('R$ 1.150,00', { exact: true })).toBeVisible();
    await page.screenshot({ path: 'test-results/stage-i-sale-detail.png', fullPage: true });
    await authenticated(page, `/devices/${profitableDeviceId}`);
    await expect(page.locator('.page-actions').getByText('Vendido', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ver venda' })).toBeVisible();
    await page.getByRole('button', { name: 'Editar', exact: true }).click();
    await expect(page.getByLabel('Preço de compra', { exact: true })).toBeDisabled();
    await expect(page.getByLabel('Data de compra', { exact: true })).toBeDisabled();
    await page.getByLabel('Saúde da bateria', { exact: true }).fill('92');
    await page.getByRole('button', { name: 'Salvar alterações' }).click();
    await expect(page.getByText('92%', { exact: true })).toBeVisible();

    const sold = await get(request, `/devices/${profitableDeviceId}`);
    await page.getByRole('link', { name: 'Voltar', exact: true }).click();
    await page.getByRole('textbox', { name: 'Buscar aparelhos' }).fill(sold.internalCode);
    await expect(page.locator('.device-grid').getByText('Vendido', { exact: true })).toBeVisible();
  });

  test('dialog de cancelamento preserva foco, Escape e axe', async ({ page }) => {
    await authenticated(page, `/devices/${profitableDeviceId}/sale`);
    const trigger = page.getByRole('button', { name: 'Cancelar venda' });
    await trigger.click();
    const dialog = page.getByRole('dialog', { name: 'Cancelar venda' });
    await expect(dialog).toBeVisible();
    expect(
      (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
        .violations,
    ).toEqual([]);
    for (let index = 0; index < 4; index += 1) {
      await page.keyboard.press('Tab');
      await expect
        .poll(() => dialog.evaluate((element) => element.contains(document.activeElement)))
        .toBe(true);
    }
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    await expect(trigger).toBeFocused();
  });

  test('cancela, estorna e devolve o aparelho a DISPONIVEL_VENDA', async ({ page, request }) => {
    await authenticated(page, `/devices/${profitableDeviceId}/sale`);
    await page.getByRole('button', { name: 'Cancelar venda' }).click();
    await page.getByLabel('Motivo').fill('Venda desfeita e valor devolvido ao comprador.');
    await page.getByRole('button', { name: 'Confirmar cancelamento' }).click();
    await expect(page).toHaveURL(`/devices/${profitableDeviceId}`);
    await expect(page.getByText('Disponível para venda', { exact: true })).toBeVisible();
    await expect(page.locator('.sale-success-notice')).toContainText('Venda cancelada');
    const available = await get(request, `/devices/${profitableDeviceId}`);
    await page.getByRole('link', { name: 'Voltar', exact: true }).click();
    await page.getByRole('textbox', { name: 'Buscar aparelhos' }).fill(available.internalCode);
    await expect(
      page.locator('.device-grid').getByText('Disponível para venda', { exact: true }),
    ).toBeVisible();
  });

  test('permite nova venda com prejuízo após cancelamento', async ({ page }) => {
    await authenticated(page, `/devices/${profitableDeviceId}/sale/new`);
    await page.getByLabel('Preço de venda (R$)').fill('1900.00');
    await page
      .getByLabel('Data e hora da venda')
      .fill(localDateTime(new Date(cutoff.getTime() + 240_000)));
    await expect(page.getByText('Prejuízo estimado', { exact: true })).toBeVisible();
    await expect(page.getByText('-R$ 150,00', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Confirmar venda' }).click();
    await expect(page.getByRole('heading', { name: 'Detalhes da venda' })).toBeVisible();
    await expect(page.getByText('Prejuízo', { exact: true })).toBeVisible();
  });

  test('rejeita venda anterior à manutenção ativa', async ({ page, request }) => {
    chronologyDeviceId = await createDevice(page, new Date(cutoff.getTime() + 300_000));
    await post(request, `/devices/${chronologyDeviceId}/maintenances`, {
      performedAt: new Date(cutoff.getTime() + 420_000).toISOString(),
      items: [{ partId, cost: 100 }],
    });
    await authenticated(page, `/devices/${chronologyDeviceId}/sale/new`);
    await page.getByLabel('Preço de venda (R$)').fill('2300.00');
    await page
      .getByLabel('Data e hora da venda')
      .fill(localDateTime(new Date(cutoff.getTime() + 360_000)));
    await page.getByRole('button', { name: 'Confirmar venda' }).click();
    await expect(
      page.getByText('A venda não pode ser anterior a uma manutenção ativa.'),
    ).toBeVisible();
  });

  test('vende aparelho INITIAL_IMPORT depois do cutoff', async ({ page }) => {
    importedDeviceId = await createDevice(page, new Date(cutoff.getTime() - 86_400_000), true);
    await page.getByRole('link', { name: 'Registrar venda' }).click();
    await page.getByLabel('Preço de venda (R$)').fill('2100.00');
    await page
      .getByLabel('Data e hora da venda')
      .fill(localDateTime(new Date(cutoff.getTime() + 480_000)));
    await page.getByRole('button', { name: 'Confirmar venda' }).click();
    await expect(page.getByRole('heading', { name: 'Detalhes da venda' })).toBeVisible();
    await expect(page.getByText('R$ 600,00', { exact: true })).toBeVisible();
  });

  for (const width of [375, 430, 768, 1024, 1440]) {
    test(`venda sem overflow e acessível em ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await authenticated(page, `/devices/${importedDeviceId}/sale`);
      await expect(page.getByRole('heading', { name: 'Detalhes da venda' })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      expect(
        (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
          .violations,
      ).toEqual([]);
      await page.screenshot({ path: `test-results/stage-i-sale-${width}.png`, fullPage: true });
      await page.getByRole('button', { name: 'Cancelar venda' }).click();
      await expect(page.getByRole('dialog', { name: 'Cancelar venda' })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      expect(
        (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
          .violations,
      ).toEqual([]);
      await page.screenshot({ path: `test-results/stage-i-cancel-${width}.png`, fullPage: true });
      await page.keyboard.press('Escape');
      await expect(page.getByRole('button', { name: 'Cancelar venda' })).toBeFocused();
      await authenticated(page, `/devices/${chronologyDeviceId}/sale/new`);
      await page.getByLabel('Preço de venda (R$)').fill('2500');
      await expect(page.getByRole('heading', { name: 'Registrar venda' })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      expect(
        (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
          .violations,
      ).toEqual([]);
      await page.screenshot({ path: `test-results/stage-i-form-${width}.png`, fullPage: true });
    });
  }
});
