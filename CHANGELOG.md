# Changelog

## 1.2 — Segurança e autenticação (proposta)

### Implementado

- autenticação stateless com Bearer token opaco e sessão persistida por hash SHA-256;
- migration Flyway `V2__opaque_auth_sessions.sql`, sem alteração da V1;
- login, `/auth/me` e logout com revogação persistente e repetição segura;
- BCrypt custo 12, comparação dummy e resposta genérica de falha;
- principal mínimo e `CurrentUserIdProvider` integrado ao SecurityContext;
- default-deny, erros 401/403 padronizados, CORS allowlist e CSRF coerente;
- request ID, no-store, headers defensivos e rate limit de login bounded/expirável;
- bootstrap operacional, transacional e concorrente do primeiro `SOCIO`;
- suporte a credenciais separadas de migration/runtime e Compose em loopback;
- testes unitários e integração PostgreSQL 16/Testcontainers;
- baseline de segurança permanente e decisões E-01 a E-20.

### Status

- Etapa E versão 1.0 proposta; aguardando aprovação explícita.
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
