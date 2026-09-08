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
- G e módulos de negócio: não iniciados; exigem nova autorização explícita.
