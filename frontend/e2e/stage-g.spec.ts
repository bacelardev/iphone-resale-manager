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
const modelName = `iPhone E2E ${suffix}`;
const colorName = `Titânio E2E ${suffix}`;
const cutoff = new Date(Date.now() - 120_000);
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
  await page.getByRole('textbox', { name: new RegExp(`Buscar ${label}`, 'i') }).fill(name);
  await page.getByRole('option', { name: new RegExp(name) }).click();
}

async function fillDevice(page: Page, purchasedAt: Date) {
  await selectCatalog(page, 'modelo', modelName);
  await selectCatalog(page, 'cor', colorName);
  await page.getByLabel('Preço de compra (R$)').fill('2500.00');
  await page.getByLabel('Data e hora da compra').fill(purchasedAt.toISOString().slice(0, 16));
  await page.getByLabel('Saúde da bateria').fill('88');
  const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3]);
  await page.locator('#device-photos').setInputFiles([
    { name: 'frente.png', mimeType: 'image/png', buffer: png },
    { name: 'traseira.png', mimeType: 'image/png', buffer: png },
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

    const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 4, 5, 6]);
    await page.locator('#add-device-photo').setInputFiles({
      name: 'lateral.png',
      mimeType: 'image/png',
      buffer: png,
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

  test('lista e filtra dados reais em cards responsivos', async ({ page }) => {
    await authenticated(page, '/devices');
    await page.getByLabel('Buscar aparelhos').fill(operationalCode);
    await expect(page.locator('.device-card')).toHaveCount(1);
    await expect(page.locator('.device-card h2')).toHaveText(modelName);
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
    const code = `COLOR_UI_${suffix}`;
    await page.locator('#color-code').fill(code);
    await page.locator('#color-name').fill(`Cor UI ${suffix}`);
    await page.getByRole('button', { name: 'Adicionar' }).last().click();
    const row = page.getByRole('listitem').filter({ hasText: code });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: /Desativar/ }).click();
    await expect(row.getByText('Inativo')).toBeVisible();
  });

  test('model picker é bottom sheet no mobile e combobox pesquisável no desktop', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await authenticated(page, '/devices/import');
    await page.getByRole('button', { name: /Selecionar modelo/i }).click();
    const panel = page.locator('.picker-panel');
    await expect(panel).toHaveCSS('position', 'fixed');
    await expect(panel).toHaveCSS('bottom', '0px');
    await page.keyboard.press('Escape');
    await page.setViewportSize({ width: 1024, height: 800 });
    await page.getByRole('button', { name: /Selecionar modelo/i }).click();
    await expect(panel).toHaveCSS('position', 'absolute');
    await expect(page.getByRole('textbox', { name: 'Buscar modelo' })).toBeVisible();
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
