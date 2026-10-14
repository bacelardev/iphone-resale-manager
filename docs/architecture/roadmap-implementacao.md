# Roadmap de Implementação

## Fase 0 — Documentação

Status: concluída na versão inicial.

- escopo;
- arquitetura;
- referências;
- mapa de telas;
- design system inicial.

## Fase 1 — Engenharia do domínio

- modelagem PostgreSQL;
- diagrama ER;
- migrations;
- entidades;
- regras de integridade;
- contratos da API.

## Fase 2 — Fundação do backend

- projeto Spring Boot;
- configuração de ambiente;
- PostgreSQL;
- Flyway;
- Spring Security;
- tratamento global de erros;
- validação;
- logging;
- estrutura de testes.

## Fase 3 — Fundação do frontend

- React + Vite + TypeScript;
- Tailwind;
- Design System;
- layout responsivo;
- roteamento;
- autenticação visual;
- ESLint;
- Prettier;
- Husky.

## Fase 4 — Autenticação

- login;
- sessão/token;
- proteção de rotas;
- auditoria de usuário.

## Fase 5 — Aparelhos

- cadastro;
- listagem;
- filtros;
- detalhes;
- edição;
- fotos;
- status.

## Fase 6 — Manutenção

- registro de peças;
- custos;
- total automático;
- atualização do investimento.

## Fase 7 — Venda

- seleção do aparelho;
- cálculo;
- lucro;
- margem;
- atualização de status;
- auditoria.

## Fase 8 — Financeiro

- agregações;
- filtros por período;
- dashboard;
- capital em estoque;
- gráficos.

## Fase 9 — Histórico

- timeline;
- filtros;
- paginação.

## Fase 10 — Hardening

- testes;
- segurança;
- performance;
- responsividade;
- validação mobile;
- backup;
- deploy.


## Registro por etapas — 06/09/2026

- A, B e C: domínio e persistência aprovados e encerrados.
- D: contratos e casos de uso aprovados e encerrados.
- E: segurança e autenticação, versão 1.0 aprovada e encerrada; Argon2id validado.
- F: fundação frontend e integração real de autenticação, **versão 1.0 aprovada**,
  **Etapa F encerrada**. Entrega reúne a fundação visual da Fase 3 e a integração web
  da Fase 4. Validação final em `docs/frontend/etapa-f-fundacao-frontend.md`.
- G: aparelhos, catálogos e preparação da implantação, **versão 1.0 aprovada** e **Etapa G encerrada**.
- H: manutenções, peças e importação histórica, **versão 1.0 aprovada** e **Etapa H encerrada**.
- I: vendas, lucro, margem e reversão, **versão 1.0 aprovada** e **Etapa I encerrada**.
- J: financeiro e conclusão da implantação aprovados e encerrados pelo PR #5.
- K: fechamento de produção da V1 integrado pelo PR #6 e encerrado; sem deploy nem
  validação operacional em produção.


## Etapa G encerrada

- Branch: `codex/etapa-g-devices-catalogs`.
- Entrega: catálogos reais, aparelhos, fotos, estoque, importação inicial e `business_initialization` em `PREPARING`.
- Migration V3 aditiva; V1/V2 preservadas.
- `maintenance.registration_origin` e atribuição de sócio existem somente como preparação de schema/JPA.
- Status: **versão 1.0 aprovada; Etapa G encerrada**.
- Evidências aprovadas: head técnico `2c56075a` e fechamento `4525b092`.
- No encerramento da G, `maintenance.registration_origin` permanecia somente schema/JPA;
  a autorização posterior implementou e encerrou a H em branch e PR próprios.

## Etapa H encerrada

- Branch: `codex/etapa-h-maintenance`; PR #3 aprovado para merge.
- Fase 6: catálogo de peças, manutenção operacional, importação histórica, totais,
  ledger, cancelamento/reversão, preview e arquivamento integrado.
- Migration V4 aditiva; V1/V2/V3 preservadas.
- Frontend V2/Mobile-first com histórico, formulário, detalhe, cancelamento e PartPicker
  dentro da fundação acessível existente.
- Status: **versão 1.0 aprovada — Etapa H encerrada**.
- Etapa I não iniciada.

## Etapa I encerrada

- Base: merge da H `520d8a871b4f827a8aa927b24f14a9447264d6e1`.
- Branch: `codex/etapa-i-sales`; PR #4 aprovado para merge.
- Fase 7: registro, consulta singular, cálculo oficial, prejuízo, cancelamento/reversão e revenda.
- V5 aditiva; V1–V4 preservadas. Frontend V2/Mobile-first, cinco larguras, axe e CI real.
- Evidências e limites em `docs/sales/etapa-i-vendas.md`.
- Status: **versão 1.0 aprovada; Etapa I encerrada**, sem alterações funcionais no fechamento.
- Nenhuma funcionalidade da Etapa J iniciada; não há conclusão da implantação.


## Estado da Etapa J

**Versão 1.0 aprovada e encerrada pelo PR #5.** A etapa fechou o ciclo financeiro operacional,
concluiu a implantação existente, ativou Financeiro e Dashboard com dados reais e oficializou
Delarte Control / Desenvolvido por Andelar. A Etapa K é o fechamento de produção da V1.

## Etapa K encerrada

- Base: `main` em `4984b987a45974cf98318f755dc2037718c30320`.
- Branch: `codex/etapa-k-production-readiness`; PR #6 integrado em 2026-10-13 por merge commit
  `7ccedeb6c88bc56036e058edf23c2c9d2c46acb9` (head `9c20e52036c174ecb26511eee23c80efe18e8dd9`).
- CI no head e no merge: Backend verify #105/#106 e Frontend verify #121/#122 aprovados, com
  PostgreSQL/Testcontainers, Flyway, Hibernate validate e gates de zero skips.
- V1–V6 preservadas; nenhuma migration nova. Papel único `SOCIO` confirmado.
- Status: **versão 1.0 integrada; Etapa K encerrada**. A integração **não** significa sistema
  implantado nem validado operacionalmente em produção: nenhum deploy, conta externa, credencial
  real ou smoke test foi executado.
- Auditoria pós-integração de 2026-10-14 sem bloqueios. Riscos técnicos permanecem como
  follow-up: rate limiting de login atrás do proxy, momento da validação de configuração de
  produção, troca de senha e sessões, consistência do armazenamento de fotos e lacunas de testes.
  Detalhes em `docs/etapa-k/auditoria-etapa-k-pr6-2026-10-14.md`.
- Nenhuma etapa posterior iniciada.
