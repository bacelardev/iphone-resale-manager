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
- I: implementação autorizada, versão 1.0 proposta em branch própria e PR draft.
- J e etapas seguintes: não iniciadas; exigem nova autorização explícita.


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

## Etapa I — versão 1.0 proposta aguardando aprovação

- Base: merge da H `520d8a871b4f827a8aa927b24f14a9447264d6e1`.
- Branch: `codex/etapa-i-sales`; PR em draft, sem merge automático.
- Fase 7: registro, consulta singular, cálculo oficial, prejuízo, cancelamento/reversão e revenda.
- V5 aditiva; V1–V4 preservadas. Frontend V2/Mobile-first, cinco larguras, axe e CI real.
- Evidências e limites em `docs/sales/etapa-i-vendas.md`.
- Nenhuma funcionalidade da Etapa J iniciada; não há conclusão da implantação.
