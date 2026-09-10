import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const tokenKey = 'iphone-resale.access-token';
const token = `irs_${createHash('sha256').update('stage-h-browser-session').digest('base64url')}`;
const tokenHash = createHash('sha256').update(token).digest('hex');
const suffix = createHash('sha256')
  .update(`stage-h-${Date.now()}`)
  .digest('hex')
  .slice(0, 8)
  .toUpperCase();
const modelName = `iPhone Manutenção ${suffix}`;
const colorName = `Azul Manutenção ${suffix}`;
let partName = `Bateria Premium ${suffix}`;
const partCode = `BATTERY_H_${suffix}`;
const otherName = `Outro serviço ${suffix}`;
let cutoff = new Date();
let operationalDeviceId = '';
let importedDeviceId = '';
let operationalMaintenanceId = '';
let historicalMaintenanceId = '';

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

async function post(request: APIRequestContext, path: string, data: unknown) {
  const response = await request.post(`http://localhost:8080/api/v1${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    data,
  });
  expect(response.ok(), `${path}: HTTP ${response.status()}`).toBe(true);
  return response.json();
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

async function selectCatalog(page: Page, label: string, name: string) {
  await page.getByRole('button', { name: new RegExp(`Selecionar ${label}`, 'i') }).click();
  const search = page.getByRole('combobox', { name: new RegExp(`Buscar ${label}`, 'i') });
  await search.fill(name);
  await page.getByRole('option', { name: new RegExp(name) }).click();
}

async function fillDevice(page: Page, purchasedAt: Date) {
  await selectCatalog(page, 'modelo', modelName);
  await selectCatalog(page, 'cor', colorName);
  await page.getByLabel('Preço de compra (R$)').fill('2000.00');
  await page.getByLabel('Data e hora da compra').fill(localDateTime(purchasedAt));
  await page.getByLabel('Saúde da bateria').fill('91');
  await page.locator('#device-photos').setInputFiles([
    { name: 'frente-h.png', mimeType: 'image/png', buffer: frontPng },
    { name: 'traseira-h.png', mimeType: 'image/png', buffer: backPng },
  ]);
}

async function registerFromUi(page: Page, deviceId: string, cost: string, historical = false) {
  await authenticated(page, `/devices/${deviceId}/maintenances/${historical ? 'import' : 'new'}`);
  await selectCatalog(page, 'peça', partName);
  await page.getByLabel('Custo').fill(cost);
  await page
    .getByRole('button', { name: historical ? 'Importar manutenção' : 'Registrar manutenção' })
    .click();
  await expect(page).toHaveURL(/\/maintenances\/[0-9a-f-]+$/);
  return page.url().split('/').pop()!;
}

test.describe.serial('Etapa H com backend e PostgreSQL reais', () => {
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
      code: `IPHONE_H_${suffix}`,
      name: modelName,
      displayOrder: 8,
    });
    await post(request, '/colors', { code: `BLUE_H_${suffix}`, name: colorName });
    await post(request, '/parts', { code: 'OTHER', name: otherName });
    const initialization = await request.get(
      'http://localhost:8080/api/v1/business-initialization',
      { headers: { Authorization: `Bearer ${token}` } },
    );
    expect(initialization.ok()).toBe(true);
    const body = await initialization.json();
    expect(body.status).toBe('PREPARING');
    cutoff = new Date(body.cutoffAt);
  });

  test('cria, busca, edita, desativa e reativa uma peça', async ({ page }) => {
    await authenticated(page, '/settings/catalogs');
    const section = page.locator('.catalog-section').filter({ hasText: 'Peças' });
    await section.locator('#part-code').fill(partCode);
    await section.locator('#part-name').fill(partName);
    await section.getByRole('button', { name: 'Adicionar' }).click();
    let row = section.getByRole('listitem').filter({ hasText: partCode });
    await expect(row).toBeVisible();
    await section.getByLabel('Buscar em peças').fill(partCode);
    await expect(section.getByRole('listitem')).toHaveCount(1);
    await row.getByRole('button', { name: new RegExp(`Editar ${partName}`) }).click();
    row = section.locator('.catalog-list li').first();
    partName = `${partName} revisada`;
    await row.getByLabel('Nome de peça').fill(partName);
    await row.getByRole('button', { name: 'Salvar' }).click();
    await expect(row.getByText(partName, { exact: true })).toBeVisible();
    await row.getByRole('button', { name: /Desativar/ }).click();
    await expect(row.getByText('Inativo')).toBeVisible();
    await row.getByRole('button', { name: /Ativar/ }).click();
    await expect(row.getByText('Ativo')).toBeVisible();
  });

  test('cadastra aparelho operacional para o fluxo de manutenção', async ({ page }) => {
    await authenticated(page, '/devices/new');
    await fillDevice(page, new Date(cutoff.getTime() + 60_000));
    await page.getByRole('button', { name: 'Cadastrar novo aparelho' }).click();
    await expect(page).toHaveURL(/\/devices\/[0-9a-f-]+$/);
    operationalDeviceId = page.url().split('/').pop()!;
  });

  test('abre picker acessível e registra manutenção operacional positiva', async ({ page }) => {
    await authenticated(page, `/devices/${operationalDeviceId}/maintenances/new`);
    const trigger = page.getByRole('button', { name: /Selecionar peça/i });
    await trigger.click();
    const dialog = page.getByRole('dialog', { name: 'Peça' });
    await expect(dialog).toBeVisible();
    expect(
      (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
        .violations,
    ).toEqual([]);
    await page.keyboard.press('End');
    await page.keyboard.press('Escape');
    await expect(trigger).toBeFocused();
    await selectCatalog(page, 'peça', partName);
    await page.getByLabel('Custo').fill('250.00');
    await page.getByRole('button', { name: 'Registrar manutenção' }).click();
    await expect(page).toHaveURL(/\/maintenances\/[0-9a-f-]+$/);
    operationalMaintenanceId = page.url().split('/').pop()!;
    await expect(page.getByText('Saída financeira gerada', { exact: true })).toBeVisible();
    await page.screenshot({ path: 'test-results/stage-h-maintenance-detail.png', fullPage: true });
  });

  test('atualiza o investimento ativo do aparelho', async ({ page }) => {
    await authenticated(page, `/devices/${operationalDeviceId}`);
    const investment = page.locator('.detail-card').filter({ hasText: 'INVESTIMENTO' });
    await expect(investment.getByText('R$ 250,00', { exact: true })).toBeVisible();
    await expect(investment.getByText('R$ 2.250,00', { exact: true })).toBeVisible();
  });

  test('registra custo zero sem saída financeira', async ({ page }) => {
    await authenticated(page, `/devices/${operationalDeviceId}/maintenances/new`);
    await selectCatalog(page, 'peça', otherName);
    await page.getByLabel('Custo').fill('0.00');
    await expect(page.getByLabel('Detalhes obrigatórios')).toHaveAttribute('required', '');
    await page.getByLabel('Detalhes obrigatórios').fill('Diagnóstico sem cobrança');
    await page.getByRole('button', { name: 'Registrar manutenção' }).click();
    await expect(page).toHaveURL(/\/maintenances\/[0-9a-f-]+$/);
    await expect(page.getByText('Manutenção sem custo financeiro', { exact: true })).toBeVisible();
  });

  test('importa aparelho e manutenção histórica sem nova saída de caixa', async ({ page }) => {
    await authenticated(page, '/devices/import');
    await fillDevice(page, new Date(cutoff.getTime() - 86_400_000));
    await page.getByRole('button', { name: 'Importar aparelho existente' }).click();
    await expect(page).toHaveURL(/\/devices\/[0-9a-f-]+$/);
    importedDeviceId = page.url().split('/').pop()!;
    await page.getByRole('link', { name: /Importar histórica/ }).click();
    await expect(page.getByText(/não será criada uma nova saída de caixa/i)).toBeVisible();
    await expect(page.getByText('Data de corte')).toBeVisible();
    await page
      .getByLabel('Data da manutenção')
      .fill(localDateTime(new Date(cutoff.getTime() - 3_600_000)));
    await selectCatalog(page, 'peça', partName);
    await page.getByLabel('Custo').fill('300.00');
    await page.getByRole('button', { name: 'Importar manutenção' }).click();
    await expect(page).toHaveURL(/\/maintenances\/[0-9a-f-]+$/);
    historicalMaintenanceId = page.url().split('/').pop()!;
    await expect(page.getByText('Custo histórico — sem nova saída de caixa')).toBeVisible();
  });

  test('cancela manutenção operacional e remove o valor do investimento', async ({ page }) => {
    await authenticated(
      page,
      `/devices/${operationalDeviceId}/maintenances/${operationalMaintenanceId}`,
    );
    await page.getByRole('button', { name: 'Cancelar lançamento' }).click();
    await page.getByLabel('Motivo').fill('Correção operacional E2E.');
    await page.getByRole('button', { name: 'Confirmar cancelamento' }).click();
    await expect(page.getByText('Cancelada', { exact: true })).toBeVisible();
    await authenticated(page, `/devices/${operationalDeviceId}`);
    const investment = page.locator('.detail-card').filter({ hasText: 'INVESTIMENTO' });
    await expect(investment.getByText('R$ 0,00', { exact: true })).toBeVisible();
  });

  test('cancela manutenção histórica sem reversão financeira', async ({ page }) => {
    await authenticated(
      page,
      `/devices/${importedDeviceId}/maintenances/${historicalMaintenanceId}`,
    );
    await page.getByRole('button', { name: 'Cancelar lançamento' }).click();
    await page.getByLabel('Motivo').fill('Correção histórica E2E.');
    await page.getByRole('button', { name: 'Confirmar cancelamento' }).click();
    await expect(page.getByText('Cancelada', { exact: true })).toBeVisible();
    await expect(page.getByText('Custo histórico — sem nova saída de caixa')).toBeVisible();
  });

  test('mantém detalhes e itens de manutenção cancelada consultáveis', async ({ page }) => {
    await authenticated(
      page,
      `/devices/${operationalDeviceId}/maintenances/${operationalMaintenanceId}`,
    );
    await expect(page.getByRole('heading', { name: 'Detalhes da manutenção' })).toBeVisible();
    await expect(page.getByText(partName, { exact: true })).toBeVisible();
    await expect(page.getByText(/Correção operacional E2E/)).toBeVisible();
  });

  test('preserva responsividade e axe nas cinco larguras oficiais', async ({ page }) => {
    for (const width of [375, 430, 768, 1024, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      await authenticated(page, `/devices/${importedDeviceId}`);
      await expect(page.getByRole('heading', { name: modelName })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      expect(
        (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
          .violations,
      ).toEqual([]);
      await page.screenshot({ path: `test-results/stage-h-device-${width}.png`, fullPage: true });
    }
  });

  test('arquiva aparelho cancelando uma manutenção ativa atomicamente', async ({ page }) => {
    await registerFromUi(page, operationalDeviceId, '80.00');
    await authenticated(page, `/devices/${operationalDeviceId}`);
    await page.getByRole('button', { name: 'Arquivar' }).click();
    await page.getByLabel('Motivo').fill('Encerramento do aparelho com manutenção ativa.');
    await page.getByRole('button', { name: 'Confirmar arquivamento' }).click();
    await expect(page.getByText(/permanece imutável/i)).toBeVisible();
    await expect(page.getByText('R$ 0,00', { exact: true })).toBeVisible();
  });
});
