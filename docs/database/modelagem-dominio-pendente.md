# Sprint 1 — Engenharia do Domínio e Banco

## Objetivo

Antes de iniciar implementação funcional, fechar a modelagem completa do domínio e do PostgreSQL.

## Entregáveis obrigatórios

1. Modelo de domínio.
2. Entidades e atributos.
3. Relacionamentos.
4. Regras de integridade.
5. Diagrama ER.
6. Modelo lógico PostgreSQL.
7. PKs e FKs.
8. Constraints.
9. Índices.
10. Estratégia de auditoria.
11. Estratégia de exclusão.
12. Script SQL inicial.
13. Migration Flyway `V1__initial_schema.sql`.
14. Estrutura inicial de entidades JPA.
15. Enums.
16. Justificativa das decisões.

## Entidades esperadas

A modelagem deve avaliar, no mínimo:

- User
- Device / Iphone
- DevicePhoto
- Maintenance
- MaintenanceItem
- Sale
- AuditLog

Também deve avaliar se é necessário ou não persistir uma entidade explícita de movimentação financeira.

## Cuidados

Não criar tabela ou abstração apenas por “boas práticas”.

A modelagem deve refletir o MVP real.

### Questões a decidir

- armazenamento do modelo do iPhone: enum ou catálogo em tabela;
- armazenamento de cor: enum, texto validado ou tabela;
- catálogo de peças;
- edição de vendas;
- cancelamento de venda;
- soft delete;
- geração do saldo financeiro;
- necessidade de ledger/movimentações;
- auditoria JPA versus tabela explícita;
- timestamps e timezone;
- precisão dos campos monetários.

## Padrões obrigatórios

Valores monetários:

- nunca usar `float` ou `double`;
- Java: `BigDecimal`;
- PostgreSQL: `NUMERIC`.

Datas:

- armazenar timestamps de forma consistente;
- definir estratégia de UTC/backend e exibição local.

IDs:

- usar identificadores estáveis;
- decidir UUID ou bigint com justificativa.

## Critério de conclusão

A Sprint 1 termina quando a modelagem estiver aprovada e for possível iniciar Spring Boot sem dúvidas estruturais relevantes.
