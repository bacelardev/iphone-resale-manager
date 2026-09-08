# Etapa F — Fundação do frontend e autenticação real

Versão: **1.0 proposta**  
Status: **aguardando aprovação da Etapa F**

## Escopo e arquitetura

Frontend implementado sobre a Etapa E encerrada, base `9d3de88` (código E-05 `9f920c4`).
Foram lidos os documentos de negócio, arquitetura, design, domínio A/B/C, casos de uso,
contratos, segurança e decisões. Contratos reais inspecionados nos DTOs e AuthController.

Fluxo: página → AuthProvider/useAuth → authApi → apiRequest → Spring Boot.
O port de storage do navegador isola a persistência. Query cuida de /me; RHF+Zod do
formulário. Componentes visuais ficam no Design System interno.

Login e dashboard são funcionais. As rotas dos módulos futuros são placeholders
protegidos, sem consultas de negócio, métricas fictícias, CRUD, upload ou storage.
Nenhuma implementação da Etapa G foi iniciada. Backend, V1 e V2 foram preservados.

## Baseline Visual Oficial

- Desktop: [`iphone-resale-manager-visual-prototype-v2.svg`](../design/prototypes/iphone-resale-manager-visual-prototype-v2.svg)
- Mobile: [`iphone-resale-manager-mobile-prototype-v1.svg`](../design/prototypes/iphone-resale-manager-mobile-prototype-v1.svg)

Os SVGs são fonte da verdade visual, subordinados às regras A–E, contratos reais e
security baseline. Seus números, catálogos e registros são exemplos de composição,
não dados do produto. O V2 orienta layouts amplos; o Mobile-first orienta iPhone,
drawer, bottom sheet, coluna única e touch. Referências externas e decisões visuais da
Etapa F não podem sobrepor domínio, API ou segurança.

A comparação formal confirmou a direção Dark Premium / Financial Minimalism e gerou
ajustes mínimos: login central sem hero de marketing; Card e Input alinhados aos tokens
oficiais; verde, amarelo e vermelho semânticos do protótipo; motion de 150/180/220ms;
Drawer real no menu mobile; Select e BottomSheet encapsulados no Design System. Nenhum
número demonstrativo dos SVGs foi levado ao dashboard.

## Decisões F-01 a F-20

| ID | Tema | Decisão e consequência |
| --- | --- | --- |
| F-01 | Stack frontend | React 19, TypeScript estrito, Vite 8 e Tailwind 4; componentes internos no padrão shadcn/ui com Radix, Tabler, Query, Router, RHF e Zod. Node 24 LTS. Sem Redux/Next.js. |
| F-02 | Estrutura | app compõe providers/query; features/auth concentra autenticação; lib/api centraliza transporte; lib/auth isola token; layouts, pages, routes, types e components/ui possuem fronteiras simples. |
| F-03 | Design System | Baseline oficial V2 desktop + Mobile-first. Paleta #080808 / #101010 / #151515 / #1B1B1B; borda #262626; texto #F5F5F7 / #A1A1A6 / #6E6E73. Branco para ação principal e cor somente semântica. Motion 150ms para hover/press, 180ms para card/estado e 200–220ms para modal/drawer/sheet, com reduced-motion. |
| F-04 | Componentes | Button, Input, Select, Card, Badge, Dialog, Drawer, BottomSheet, Skeleton, Spinner, FormField, PageHeader, EmptyState e ErrorState em components/ui; Radix encapsulado. Select nasce sem catálogo inventado; Drawer tem uso real no App Shell; BottomSheet prepara o padrão mobile oficial. |
| F-05 | Roteamento | /login e /dashboard funcionais. /devices, /devices/new, /devices/:id, /financial, /history e /settings/users protegidas com placeholders. Fallback vai ao dashboard, passando pelo guard. |
| F-06 | App Shell | Sidebar fixa desktop, header discreto e Drawer Radix no mobile, com navegação por links reais e identificação do sócio. Não há segunda identidade entre breakpoints nem indicador financeiro fictício. |
| F-07 | Cliente HTTP | apiRequest<T> concentra base URL, JSON/FormData, Bearer, UUID, 204, envelope de erro, rede, cancelamento e timeout 15s. Sem cookies, redirects ou cache HTTP; somente caminhos /api/v1 na origem configurada. |
| F-08 | ApiError | Envelope REST tipado e validado com Zod; lógica decide por code. Mensagens públicas mapeadas, sem renderizar causas internas ou payloads desconhecidos. Erro sem JSON recebe fallback seguro. |
| F-09 | Request ID | UUID novo por request; referência discreta em erros. Não é idempotência. Retry-After pode ser lido quando exposto pelo backend; mensagem 429 funciona sem depender desse header. |
| F-10 | sessionStorage | AuthTokenStore concentra get/set/clear. Apenas token opaco é persistido por aba. Não há localStorage, usuário persistido, persistência do cache Query ou refresh token. |
| F-11 | Segurança do token | Token somente no store e header Bearer; nunca URL, logs, console, analytics ou markup. Não há dangerouslySetInnerHTML, scripts externos, senhas no Query ou segredos VITE. sessionStorage não protege contra XSS. |
| F-12 | Login real | Composição central limpa e sem hero de marketing. RHF+Zod validam UX; POST real ao backend, guarda token, recebe DTO explícito e navega ao dashboard. Senha 12–128 unidades UTF-16 conforme backend, sem truncar ou normalizar. 401/429/rede/validação têm estados e botão sem resize. |
| F-13 | Restauração | Sem token, visitante; com token, GET /auth/me via Query sem retry automático. 200 restaura; 401 remove; falha de rede preserva token e oferece retry, sem mostrar conteúdo protegido. |
| F-14 | Estado de autenticação | AuthProvider/useAuth com checking/authenticated/unauthenticated/error, user, login, logout e retry. Contador de geração evita que resposta antiga restaure contexto encerrado. Efeitos compatíveis com StrictMode. |
| F-15 | ProtectedRoute | Guard impede flash durante checking e error; visitante vai ao login; autenticado entra. /login redireciona autenticado ao dashboard. Autorização de negócio continua no backend. |
| F-16 | Logout | POST real; finally remove token, usuário e queries privadas, cancelando consultas e redirecionando. Falha de rede encerra só a aba e informa que a sessão remota pode permanecer até expirar; sem promessa falsa de revogação. |
| F-17 | 401 global | Chamada protegida com 401 invalida o contexto atual, limpa cache e leva ao login. Login é exceção. Resposta associada a token antigo não invalida login novo; não há retry infinito. |
| F-18 | TanStack Query + forms | Query para estado do servidor (/me), sem credenciais nas chaves/cache. RHF+Zod para formulário, sem inserir senha/resposta com token no cache de mutation. Backend continua sendo a validação final. |
| F-19 | Responsividade + acessibilidade | 375, 430, 768, 1024 e 1440px; labels, alvos de 44px, foco visível, skip link, teclado, trap/restauração de foco Radix, sem overflow e reduced-motion. Playwright + axe verificam WCAG A/AA; não equivalem a auditoria assistiva completa. |
| F-20 | Qualidade, testes e CI | ESLint 10, Prettier, Husky/lint-staged, Vitest/RTL e workflow frontend separado: npm ci, lint, format, typecheck, tests, build e Playwright contra Spring Boot/PostgreSQL reais. Gate exige ao menos 11 E2E sem skips e valida tokens/paleta/motion oficiais. Backend CI preservado. |

## Fluxos e limites de segurança

- Login não envia token anterior. Em sucesso, persiste somente accessToken e mantém
  User em memória. O usuário ativo vem do backend; papel não autoriza regras no browser.
- Refresh exige /me real. Falha de rede mantém o token, porém oculta o conteúdo privado.
- Logout sempre limpa localmente e elimina cache privado; a impossibilidade de revogar
  remotamente é informada. Encerrar uma aba não revoga a sessão no servidor por si só.
- sessionStorage pode ser copiado pelo navegador ao duplicar uma aba e é legível por
  scripts da mesma origem. Não é cofre, não elimina XSS e não substitui HTTPS/CSP.
- Variável pública VITE_API_BASE_URL contém somente origem, sem segredo. CORS deve
  corresponder à origem do frontend; não foi aberto wildcard.
- Requests têm timeout e AbortSignal; redirecionamentos HTTP não são seguidos. Não
  existe renovação silenciosa, PWA, cache offline ou credencial padrão.
- A política de cache e CSP do **servidor estático de produção** será configurada no
  deploy futuro: SPA fallback, HTTPS, `Cache-Control: no-store` para HTML e
  `default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self' <origem-api>;
  img-src 'self' data:; font-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'`.
  A política deve ser ensaiada nesse ambiente; não foi realizado deploy.
- O contrato não foi alterado. Header Retry-After não é requisito para exibir o erro 429;
  a allowlist CORS atual pode impedir sua leitura cross-origin.

## Validação

Local: Node 24.19.0 / npm 11.9.0. `npm ci`, `format:check`, `lint`,
`typecheck`, `npm test -- --run` e `build` passaram. Após o complemento visual são
**33 testes Vitest em 5 arquivos**, zero falhas. Build: JS 459,93 kB
(144,18 kB gzip), CSS 17,94 kB (4,97 kB gzip). Valores são tamanho de bundle,
não benchmark de latência.

CI e validação funcional real: **concluídos com sucesso** no workflow
[`Frontend verify` #34243971100](https://github.com/bacelardev/iphone-resale-manager/actions/runs/34243971100),
commit `7c15430`, em 2026-09-08. O job executou Node 24, Java 21,
PostgreSQL 16 vazio, Flyway V1+V2, `ddl-auto=validate`, backend real e Chromium.
Passaram instalação bloqueada, lint, formatação, typecheck, build de produção,
**31 testes Vitest em 4 arquivos** e **10 testes Playwright** em 16,3 s, sem skips,
resultados inesperados ou flakiness. O startup HTTP só ficou disponível após Flyway,
validação do Hibernate e bootstrap efêmero concluírem; a autenticação E2E usou a API
real e comprovou CORS.

Não confundir fixtures dos testes Vitest com autenticação mockada na aplicação:
a aplicação usa somente as três rotas reais. O E2E cobriu login, refresh/me,
placeholders, logout e revogação, token inválido/expirado, 401/429, conexão recusada
simulada e as cinco larguras com axe e teclado. Traces, vídeos e storage snapshots
ficaram desativados. O artefato `frontend-responsive-screenshots`, digest
`sha256:3e99f672c347fb655fac233346312adc4afd0fad3ef08bd7407ae1b404260142`,
contém somente capturas da interface, sem senha ou token expostos.

O complemento amplia o E2E para **11 cenários** e verifica explicitamente ausência de
hero no login, tokens de fundo/surface/card/hover/borda, cores efetivas de Input/Card,
motion de Button/Card e Drawer mobile. O check `Frontend verify` do head do PR #1 é a
evidência integrada exigida antes da aprovação desta versão atualizada.

## Operação

Ver `frontend/README.md`: comandos, bootstrap backend, CORS, SPA fallback,
variáveis públicas e execução E2E em banco descartável. Scripts obrigatórios presentes.
Husky usa lint-staged; sem build/servidor no pre-commit. CI é a verificação final.

## Dependências

Versões exatas e dependências transitivas em `frontend/package-lock.json`.
Dependências diretas de runtime: React/React DOM 19.2.8, React Router DOM 7.18.3,
TanStack Query 5.102.8, React Hook Form 7.87.0, Zod 4.5.4, resolvers 5.9.1,
Radix Dialog 1.1.23, Radix Slot 1.3.3, Tabler Icons 3.46.0, CVA 0.7.1,
clsx 2.1.1 e tailwind-merge 3.6.0. Toolchain: Vite 8.2.2, TypeScript 6.0.3,
Tailwind CSS 4.3.3, ESLint 10.10.0, Prettier 3.9.6, Vitest 5.0.0,
Testing Library, Playwright 1.63.0, axe 4.13.0, Husky 9.1.7 e lint-staged 17.5.0.
Não há dependência Redux, Next.js, analytics, SDK de autenticação ou storage persistente.

## Arquivos

Esta proposta atualizada altera **67 arquivos** em relação a `main`: workflow
`frontend-verify.yml`; relatório, ADR, roadmap, baseline e changelog; configuração
Node/Vite/TypeScript/Tailwind/ESLint/Prettier/Husky/Playwright; aplicação em `src/`;
33 testes unitários/de componente; suíte E2E real; lockfile; documentação operacional;
e os dois SVGs oficiais em `docs/design/prototypes/`.
Backend, migrations V1/V2 e artefatos das Etapas A–E não foram alterados.

Entrega preparada na branch `codex/etapa-f-foundation`, à frente de `main` e sem
divergência da base `9d3de88`. A etapa permanece proposta até revisão e merge;
nenhuma publicação direta em `main` faz parte desta conferência.

## Limitações reais

Sem módulos de negócio, deploy, OAuth, 2FA, recuperação, refresh token, storage ou PWA.
Rate limit do backend continua por processo. Logout offline não garante revogação
remota. Acessibilidade automatizada não substitui teste com leitores de tela.
Nenhuma métrica de negócio foi simulada como dado real. Select e BottomSheet são
fundação visual sem catálogo ou fluxo de negócio conectado nesta etapa.

## Referências técnicas

- [shadcn/ui em Vite](https://ui.shadcn.com/docs/installation/vite)
- [shadcn/ui e Tailwind 4](https://ui.shadcn.com/docs/tailwind-v4)
- [TanStack Query — cancelamento](https://tanstack.com/query/latest/docs/framework/react/guides/query-cancellation)
- [Node — ciclo LTS](https://nodejs.org/en/about/previous-releases)

A Etapa F permanece proposta. Não avançar para a Etapa G sem aprovação explícita.
