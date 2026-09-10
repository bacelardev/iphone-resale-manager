import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const tokenKey = 'iphone-resale.access-token';
const token = `irs_${createHash('sha256').update('stage-g-browser-session').digest('base64url')}`;
const tokenHash = createHash('sha256').update(token).digest('hex');
const suffix = createHash('sha256')
  .update(String(Date.now()))
  .digest('hex')
  .slice(0, 8)
  .toUpperCase();
let modelName = `iPhone E2E ${suffix}`;
const colorName = `Titânio E2E ${suffix}`;
const cutoff = new Date(Date.now() - 120_000);
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
let operationalId = '';
let operationalCode = '';

async function api(request: APIRequestContext, path: string, data: unknown) {
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
  await page.getByRole('combobox', { name: new RegExp(`Buscar ${label}`, 'i') }).fill(name);
  await page.getByRole('option', { name: new RegExp(name) }).click();
}

async function fillDevice(page: Page, purchasedAt: Date) {
  await selectCatalog(page, 'modelo', modelName);
  await selectCatalog(page, 'cor', colorName);
  await page.getByLabel('Preço de compra (R$)').fill('2500.00');
  await page.getByLabel('Data e hora da compra').fill(localDateTime(purchasedAt));
  await page.getByLabel('Saúde da bateria').fill('88');
  await page.locator('#device-photos').setInputFiles([
    { name: 'frente.png', mimeType: 'image/png', buffer: frontPng },
    { name: 'traseira.png', mimeType: 'image/png', buffer: backPng },
  ]);
}

test.describe.serial('Etapa G com backend e PostgreSQL reais', () => {
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
    const model = await api(request, '/models', {
      code: `IPHONE_E2E_${suffix}`,
      name: modelName,
      displayOrder: 1,
    });
    const color = await api(request, '/colors', {
      code: `TITANIUM_E2E_${suffix}`,
      name: colorName,
    });
    expect(model.id).toBeTruthy();
    expect(color.id).toBeTruthy();
    await api(request, '/business-initialization/start', { cutoffAt: cutoff.toISOString() });
  });

  test('cadastra compra operacional, altera status e gerencia fotos', async ({ page }) => {
    await authenticated(page, '/devices/new');
    await fillDevice(page, new Date(Date.now() - 60_000));
    await page.getByRole('button', { name: 'Cadastrar novo aparelho' }).click();
    await expect(page).toHaveURL(/\/devices\/[0-9a-f-]+$/);
    operationalId = page.url().split('/').pop()!;
    operationalCode = (await page.locator('.page-heading .eyebrow').textContent())!;
    await expect(page.getByText('Compra operacional', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Marcar disponível' }).click();
    await expect(page.getByText('Disponível para venda', { exact: true })).toBeVisible();

    await expect
      .poll(() =>
        page
          .locator('.photo-grid img')
          .first()
          .evaluate((image: HTMLImageElement) => image.naturalWidth),
      )
      .toBeGreaterThan(0);
    await page.locator('#add-device-photo').setInputFiles({
      name: 'lateral.png',
      mimeType: 'image/png',
      buffer: backPng,
    });
    await expect(page.locator('.photo-grid figure')).toHaveCount(3);
    await page.getByRole('button', { name: 'Remover foto 3' }).click();
    await expect(page.locator('.photo-grid figure')).toHaveCount(2);
    await page.screenshot({ path: 'test-results/stage-g-device-detail.png', fullPage: true });
  });

  test('importa estoque existente com corte persistido e explicação financeira', async ({
    page,
  }) => {
    await authenticated(page, '/devices/import');
    await expect(page.getByText(/não será criada uma nova saída de caixa/i)).toBeVisible();
    await expect(page.locator('.import-notice strong')).toContainText('Data de corte:');
    await fillDevice(page, new Date(cutoff.getTime() - 86_400_000));
    await page.getByRole('button', { name: 'Importar aparelho existente' }).click();
    await expect(page).toHaveURL(/\/devices\/[0-9a-f-]+$/);
    await expect(page.getByText('Estoque inicial', { exact: true })).toBeVisible();
  });

  test('lista e filtra capacidade e período no desktop e drawer mobile', async ({ page }) => {
    await authenticated(page, '/devices');
    await page.getByLabel('Buscar aparelhos').fill(operationalCode);
    await page.getByLabel('Filtrar por capacidade').selectOption('128');
    await page
      .getByLabel('Filtrar compra a partir de')
      .fill(localDateTime(new Date(Date.now() - 3_600_000)));
    await page
      .getByLabel('Filtrar compra até')
      .fill(localDateTime(new Date(Date.now() + 3_600_000)));
    await expect(page.locator('.device-card')).toHaveCount(1);
    await expect(page.locator('.device-card h2')).toHaveText(modelName);
    await page.setViewportSize({ width: 375, height: 812 });
    await page.getByRole('button', { name: 'Filtros' }).click();
    const drawer = page.getByRole('dialog', { name: 'Filtrar aparelhos' });
    await expect(drawer.getByLabel('Filtrar por capacidade')).toHaveValue('128');
    await expect(drawer.getByLabel('Filtrar compra a partir de')).toBeVisible();
    await expect(drawer.getByLabel('Filtrar compra até')).toBeVisible();
  });

  test('expõe conflito otimista e conclui arquivamento terminal após recarga', async ({ page }) => {
    await authenticated(page, `/devices/${operationalId}`);
    execFileSync(
      'psql',
      [
        '-v',
        'ON_ERROR_STOP=1',
        '-c',
        `update device set updated_at = now() where id = '${operationalId}'`,
      ],
      {
        stdio: 'pipe',
      },
    );
    await page.getByRole('button', { name: 'Marcar pendente' }).click();
    await expect(page.getByText(/mudou em outra operação/i)).toBeVisible();
    await page.reload();
    await page.getByRole('button', { name: 'Arquivar' }).click();
    await page.getByLabel('Motivo').fill('Cadastro E2E encerrado.');
    await page.getByRole('button', { name: 'Confirmar arquivamento' }).click();
    await expect(page.getByText(/permanece imutável/i)).toBeVisible();
  });

  test('administra catálogos reais sem hard delete', async ({ page }) => {
    await authenticated(page, '/settings/catalogs');
    await expect(page.getByText(modelName, { exact: true })).toBeVisible();
    const modelList = page.locator('.catalog-list').first();
    const modelRow = modelList.getByRole('listitem').filter({ hasText: modelName });
    await modelRow.getByRole('button', { name: `Editar ${modelName}` }).click();
    const editor = modelList.locator('.catalog-edit-form');
    const updatedModelName = `${modelName} revisado`;
    await editor.getByLabel('Nome de modelo').fill(updatedModelName);
    await editor.getByLabel('Ordem do modelo').fill('7');
    await editor.getByRole('button', { name: 'Salvar' }).click();
    await expect(modelList.getByText(updatedModelName, { exact: true })).toBeVisible();
    await expect(modelList.getByText(/ordem 7/)).toBeVisible();
    modelName = updatedModelName;
    const code = `COLOR_UI_${suffix}`;
    const colorSection = page.locator('.catalog-section').filter({ hasText: 'Cores' });
    await colorSection.locator('#color-code').fill(code);
    await colorSection.locator('#color-name').fill(`Cor UI ${suffix}`);
    await colorSection.getByRole('button', { name: 'Adicionar' }).click();
    const row = colorSection.getByRole('listitem').filter({ hasText: code });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: /Desativar/ }).click();
    await expect(row.getByText('Inativo')).toBeVisible();
  });

  test('model picker é bottom sheet no mobile e combobox pesquisável no desktop', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await authenticated(page, '/devices/import');
    const trigger = page.locator('.catalog-picker').first().locator('.picker-trigger');
    await trigger.click();
    const dialog = page.getByRole('dialog', { name: 'Modelo' });
    await expect(dialog).toBeVisible();
    await expect(dialog).toHaveCSS('position', 'fixed');
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
    await page.setViewportSize({ width: 1024, height: 800 });
    await trigger.click();
    const combobox = page.getByRole('combobox', { name: 'Buscar modelo' });
    await combobox.fill(modelName);
    await page.keyboard.press('Home');
    await page.keyboard.press('Enter');
    await expect(dialog).not.toBeVisible();
    await expect(trigger).toContainText(modelName);
  });

  for (const width of [375, 430, 768, 1024, 1440]) {
    test(`estoque sem overflow e acessível em ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await authenticated(page, '/devices');
      await expect(page.getByRole('heading', { name: 'Aparelhos' })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      expect(
        (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze())
          .violations,
      ).toEqual([]);
      await page.screenshot({ path: `test-results/stage-g-devices-${width}.png`, fullPage: true });
    });
  }
});
