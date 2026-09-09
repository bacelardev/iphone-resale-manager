# Changelog

## 1.3 — Fundação frontend (Etapa F 1.0 aprovada)

- React/TypeScript/Vite/Tailwind; Design System interno shadcn/Radix/Tabler.
- Baseline visual oficial versionado: desktop V2 e Mobile-first V1; login central sem
  hero de marketing, tokens e motion alinhados aos protótipos.
- Select, Drawer e BottomSheet incorporados à fronteira `components/ui`; Drawer usado
  na navegação mobile, sem antecipar catálogo ou regra de negócio.
- App Shell responsivo, login real e rotas protegidas; demais módulos são placeholders.
- Cliente HTTP central, sessionStorage isolado, restauração /me, logout e 401 global.
- Falha de rede distinta de credencial inválida; cache privado eliminado ao sair.
- RHF/Zod, TanStack Query, ESLint, Prettier e Husky/lint-staged.
- 33 testes Vitest locais e gate de 11 testes Playwright; workflow frontend separado com
  PostgreSQL 16, backend real, Flyway/Hibernate validate e axe, sem skips.
- Evidências finais em `docs/frontend/etapa-f-fundacao-frontend.md`.
- **Versão: 1.0 aprovada**; **Status: Etapa F encerrada**.
- Backend e migrations preservados. Etapa G não iniciada.

## 1.2 — Segurança e autenticação (Etapa E 1.0 aprovada)

### Implementado

- autenticação stateless com Bearer token opaco e sessão persistida por hash SHA-256;
- migration Flyway `V2__opaque_auth_sessions.sql`, sem alteração da V1;
- login, `/auth/me` e logout com revogação persistente e repetição segura;
- Argon2id (19 MiB, 2 iterações, paralelismo 1), comparação dummy e falha genérica;
- suporte sem truncamento a 12–128 caracteres, incluindo Unicode, via `PasswordHashService`;
- principal mínimo e `CurrentUserIdProvider` integrado ao SecurityContext;
- default-deny, erros 401/403 padronizados, CORS allowlist e CSRF coerente;
- request ID, no-store, headers defensivos e rate limit de login bounded/expirável;
- bootstrap operacional, transacional e concorrente do primeiro `SOCIO`;
- suporte a credenciais separadas de migration/runtime e Compose em loopback;
- testes unitários e integração PostgreSQL 16/Testcontainers;
- baseline de segurança permanente e decisões E-01 a E-20.

### Status

- Etapa E: **Versão: 1.0 aprovada**; **Status: Etapa E encerrada**, em 06/09/2026.
- E-05 resolvida; código `9f920c4` aprovado no GitHub Actions
  [34041521491](https://github.com/bacelardev/iphone-resale-manager/actions/runs/34041521491).
- `mvn clean verify`: 46 testes (24 unitários + 22 de integração), zero falhas, erros ou
  ignorados, JDK 21, Maven 3.9.16, PostgreSQL 16.15/Testcontainers, Flyway V1+V2,
  startup HTTP real e Hibernate validate.
- Limites 11/12/127/128/129, Unicode, bootstrap 128, enumeração e exposição verificados.
- Encoding de 97 caracteres cabe na coluna existente; V1 e V2 permanecem intactas.
- Evidências e arquivos em `docs/security/validacao-etapa-e.md`.
- Nenhum recurso da Etapa F foi iniciado.

## 1.1 — Domínio e fundação de persistência

### Aprovado

- decisões de domínio A-01 a A-14;
- modelo lógico PostgreSQL em 3FN;
- diagrama ER, integridade, ledger imutável, auditoria e índices;
- saldo inicial, aportes, retiradas e ajustes auditados no MVP;
- `purchasePrice` estritamente maior que zero;
- estratégia UUID com `gen_random_uuid()`;
- nomes de catálogos únicos sem diferenciar caixa;
- capacidades válidas e semântica de bateria não aferida;
- constraints diferíveis de fotos e itens de manutenção;
- regra de uma única origem operacional por lançamento financeiro.
- migration Flyway `V1__initial_schema.sql`;
- estrutura de pacotes Java;
- enums e entidades JPA anotadas;
- decisões C-01 a C-10.
- validação integrada com JDK 21, Maven 3.9.11 e PostgreSQL 16.13;
- execução da V1 via Flyway em schema vazio;
- `mvn clean verify`, inicialização da aplicação e validação Hibernate concluídos sem erro.

### Encerrado

- Etapa C versão 1.0 aprovada e encerrada em 03/09/2026.

## 1.0 — Fundação do projeto

### Definido

- visão de negócio;
- escopo do MVP;
- arquitetura React + Spring Boot + PostgreSQL;
- Supabase como opção inicial de infraestrutura;
- autenticação de sócios;
- cadastro de aparelhos;
- fotos;
- status;
- manutenção;
- venda;
- financeiro;
- auditoria;
- mapa de telas;
- direção visual;
- referências de frontend;
- roadmap;
- Sprint 1 de modelagem de domínio e banco.

### Próxima versão

A versão 1.1 deverá incorporar as decisões aprovadas durante a modelagem PostgreSQL e contratos iniciais da API.


## [Etapa G — 1.0 aprovada] — 2026-09-09

### Adicionado

- migration V3 para preparação da implantação, origem de registros e atribuição futura de capital;
- CRUD real de modelos e cores;
- aparelhos operacionais e importação inicial sem saída financeira duplicada;
- listagem, filtros, detalhe, edição, status e arquivamento terminal;
- storage local configurável, fotos validadas e URLs temporárias assinadas;
- frontend responsivo V2/Mobile-first com model picker e lightbox;
- cobertura unitária, integração PostgreSQL e E2E real.

### Preservado

- migrations V1/V2, decisões A–F e baseline de autenticação;
- manutenção funcional, venda, conclusão da implantação e financeiro permanecem fora do escopo;
- Etapa H não iniciada.

### Encerrado

- **Versão: 1.0 aprovada**; **Status: Etapa G encerrada**.
- Evidências finais preservadas no head técnico `2c56075a` e no fechamento `4525b092`.
- PR #2 autorizado para revisão final e merge, sem antecipar comportamento da Etapa H.
