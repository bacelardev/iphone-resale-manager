# Frontend — Delarte Control

Etapa F: **1.0 proposta**, aguardando aprovação. Fundação e autenticação reais; módulos
de negócio são placeholders protegidos.

Baseline visual oficial: `docs/design/prototypes/iphone-resale-manager-visual-prototype-v2.svg`
para desktop e `docs/design/prototypes/iphone-resale-manager-mobile-prototype-v1.svg`
para mobile. Os arquivos orientam composição, não contratos nem dados de negócio.

## Desenvolvimento

Requisitos: Node 24 LTS, npm e backend da Etapa E com PostgreSQL 16.

```bash
cd frontend
cp .env.example .env.local
npm ci
npm run dev
```

Abra `http://localhost:5173`. `VITE_API_BASE_URL` aponta para a origem da API, sem
`/api/v1`, query ou credencial; por padrão `http://localhost:8080`. Nenhuma variável
`VITE_*` pode conter segredo. Em produção, use HTTPS para frontend e backend e configure
o servidor estático para devolver `index.html` nas rotas da SPA. Não há service worker.

O primeiro usuário deve ser criado pelo bootstrap operacional já existente no backend,
com `APP_BOOTSTRAP_ENABLED`, `APP_BOOTSTRAP_NAME`, `APP_BOOTSTRAP_USERNAME` e
`APP_BOOTSTRAP_PASSWORD` no ambiente do **backend**. Desabilite o bootstrap depois.
Não há cadastro público nem credencial padrão. CORS do backend deve permitir exatamente
a origem do frontend: no desenvolvimento, `http://localhost:5173`.

## Comandos

| Script                    | Uso                                    |
| ------------------------- | -------------------------------------- |
| `dev`                     | Vite na porta 5173, estrita            |
| `preview`                 | Servir o build local na porta 5173     |
| `build`                   | TypeScript + build Vite                |
| `lint`                    | ESLint, zero warnings                  |
| `typecheck`               | TypeScript estrito                     |
| `test -- --run`           | Vitest sem watch                       |
| `test:watch`              | Vitest em watch                        |
| `format` / `format:check` | Prettier                               |
| `test:e2e`                | Playwright contra backend real isolado |

`npm ci` instala Husky em clones Git locais. O hook `frontend/.husky/pre-commit`
executa somente lint-staged; não inicia backend ou build. `HUSKY=0` desabilita hooks
no CI. O hook exige Node/npm e não substitui os gates obrigatórios do workflow.

## Sessão

`AuthTokenStore` é o único acesso ao `sessionStorage`. Login real salva o token opaco;
refresh sempre verifica `/auth/me`; 401 limpa sessão e cache privado. Falha de rede na
restauração mantém o token e permite retry. Logout chama o backend e limpa localmente
mesmo se a rede falhar, informando que a revogação remota não foi confirmada.

Não há token em localStorage, URL, logs, analytics, cache persistido ou markup. A senha
é enviada somente ao login e não entra no TanStack Query. `sessionStorage` é acessível
a scripts da mesma origem: não protege contra XSS. Detalhes no relatório da Etapa F.

## Testes E2E

Use **somente banco descartável**: a suíte insere uma sessão já expirada para validar
a rejeição real, sem editar registros existentes ou alterar migrations. Configure
`E2E_USERNAME=ci.socio`, `E2E_PASSWORD` com a senha do bootstrap e as variáveis `PG*`
do psql no processo de teste. Essas variáveis não são Vite e não entram no bundle.

```bash
npm run build
npx playwright install --with-deps chromium
npm run test:e2e
```

O workflow `frontend-verify.yml` prepara banco vazio, backend, credencial efêmera e
navegador automaticamente. Os testes Vitest usam fixtures; o E2E principal não simula
autenticação. Somente o cenário de conexão indisponível intercepta a rede para recusá-la.
Traces, vídeos e storage snapshots ficam desativados para não capturar credenciais.
Screenshots são feitos apenas com senha vazia ou após autenticar e não exibem tokens.

Relatório: `docs/frontend/etapa-f-fundacao-frontend.md`.
