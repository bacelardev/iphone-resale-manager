# Etapa K — fechamento da V1

## Estado inicial auditado

- base: `main` em `4984b987a45974cf98318f755dc2037718c30320`;
- o commit adicionava apenas as instruções da Etapa K;
- não havia branch, commit ou PR da K no GitHub;
- não havia working tree anterior disponível para recuperação;
- V1–V6 estavam intactas;
- `/history` e `/settings/users` eram placeholders;
- `AuditLog`, `AuditService`, `PhotoStorage`, `LocalFilePhotoStorage`, autenticação e rate limiter já
  existiam e foram reaproveitados.

## Decisão sobre roles

As instruções de continuidade citavam `ADMIN` e `OPERATOR`, mas o repositório real usa somente
`SOCIO`: enum Java, constraint da V1, DTO frontend, autenticação, contratos e testes. A Etapa K não
altera migrations antigas nem cria RBAC. O comportamento consolidado foi preservado e a divergência
foi registrada em vez de inventar uma migração de roles.

## Entrega

- consulta de auditoria por filtros, paginação allowlisted e detalhe somente leitura;
- sanitização defensiva de campos sensíveis em `changes`;
- tela `/history` responsiva com filtros, paginação, loading, vazio, erro e detalhe acessível;
- criação, listagem, consulta, edição, ativação e desativação de usuários;
- Argon2id, optimistic version, username normalizado/único, troca de senha apenas própria,
  autodesativação bloqueada e sessões revogadas na desativação;
- tela `/settings/users` responsiva, com criação, edição e mudança de estado;
- validação de upload por tamanho, MIME declarado, magic bytes, extensão e dimensões/pixels;
- adapter ImageKit privado sem expor segredo ao frontend e fallback não destrutivo para fotos antigas;
- Dockerfile/Render, Netlify SPA, validação de produção, healthcheck e runbook Neon/UptimeRobot;
- limitação do rate limiter em memória documentada para a topologia de uma instância.

## Limites

- nenhum deploy foi executado;
- nenhuma conta Render, Netlify, Neon, ImageKit ou UptimeRobot foi criada ou conectada;
- integração real com ImageKit exige credenciais e deve ser validada em staging;
- migração de fotos deve preservar backup local e manter o path relativo;
- nenhuma migration foi criada; V1–V6 permanecem byte a byte preservadas.

## Integração

- PR #6 (`codex/etapa-k-production-readiness` → `main`) integrado em 2026-10-13 por merge commit
  `7ccedeb6c88bc56036e058edf23c2c9d2c46acb9`; head integrado
  `9c20e52036c174ecb26511eee23c80efe18e8dd9`; árvore `d618e50fc86166bd9e0b39ea39ebf2878195b8c5`,
  idêntica à do head.
- CI aprovado no head (Backend verify #105, Frontend verify #121) e no merge commit (Backend
  verify #106, Frontend verify #122), com todos os steps e gates de zero skips verdes.
- Branch de origem mantida para recuperação.
- A integração do código **não** equivale a implantação: nenhum deploy, serviço externo,
  credencial real ou smoke test de produção foi executado. Os limites acima permanecem válidos.

## Auditoria pós-integração (2026-10-14)

Auditoria somente leitura sobre a árvore integrada, registrada em
`auditoria-etapa-k-pr6-2026-10-14.md`. Resultado: aprovado tecnicamente, sem bloqueios. Os
riscos abaixo **não foram corrigidos** e permanecem como follow-up em PRs próprios:

- rate limiting de login baseado em `getRemoteAddr()` com `forward-headers-strategy=framework`
  no Render, o que permite contornar o limite por `X-Forwarded-For`; sem limite por username;
- `ProductionConfigurationValidator` executa como `ApplicationRunner`, após Flyway e após o
  servidor aceitar requisições, apenas no perfil `prod` e sem testes;
- troca da própria senha sem exigir a senha atual e sem revogar as demais sessões;
- consistência do armazenamento de fotos: compensação de upload também em `STATUS_UNKNOWN`,
  remoção agora apaga conteúdo no provider local, falha de delete no ImageKit sem `fileId` no
  log, cópias migradas de fotos legadas não removidas, runbook sem exigir upload privado;
- lacunas de testes: URL assinada expirada/forjada, `ImageKitSdkGateway`, validador de
  produção e `AuditQueryService`; sanitizador oculta `passwordChanged`.

## Validação local

- backend Java 21: 181 fontes e 21 fontes de teste compiladas;
- backend unitário: 34 testes, zero falhas, erros ou skips;
- frontend: Prettier, ESLint e TypeScript aprovados;
- frontend unitário/componentes: 75 testes em 18 arquivos, todos aprovados;
- build Vite aprovado; o aviso não bloqueante do chunk principal de 559 kB permanece registrado;
- integração PostgreSQL/Testcontainers e os quatro novos fluxos Playwright K dependem de Docker e
  ficam a cargo dos gates obrigatórios do GitHub Actions, pois Docker não está disponível no ambiente
  local desta execução.
