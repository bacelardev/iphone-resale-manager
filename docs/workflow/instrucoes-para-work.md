# Instruções para uma Nova Sessão no ChatGPT Work / Codex

Use este texto como mensagem inicial da nova sessão.

---

Você está trabalhando na implementação de um sistema de gestão de compra, manutenção, venda e controle financeiro de iPhones usados.

Antes de produzir código, leia todos os arquivos da pasta `docs`.

Esses documentos são a **fonte da verdade do projeto**.

## Regras de execução

- Não invente regras de negócio não documentadas.
- Quando houver ambiguidade arquitetural relevante, apresente as alternativas e peça aprovação antes de implementar.
- Não overengineer.
- O MVP será utilizado inicialmente por apenas dois sócios.
- Priorize simplicidade, segurança, performance e manutenção.
- Backend principal: Java + Spring Boot.
- Banco: PostgreSQL.
- Frontend: React + TypeScript.
- O backend deve concentrar regras de negócio e cálculos financeiros.
- Toda mudança importante deve preservar auditoria.
- Valores monetários devem usar `BigDecimal`/`NUMERIC`.
- Não usar segredos hardcoded.
- Não adicionar dependências sem justificar.
- Não introduzir microservices.
- Não implementar funcionalidades fora do escopo MVP sem autorização.

## Primeira missão

Execute a **Sprint 1 — Engenharia do Domínio e Banco** descrita em `07-modelagem-dominio-pendente.md`.

Entregue:

1. proposta de modelo de domínio;
2. lista de entidades;
3. atributos e tipos;
4. relacionamentos;
5. cardinalidades;
6. regras de integridade;
7. enums;
8. estratégia de IDs;
9. estratégia de datas;
10. estratégia de auditoria;
11. estratégia financeira;
12. modelo lógico PostgreSQL;
13. diagrama ER em Mermaid;
14. DDL PostgreSQL;
15. migration Flyway inicial;
16. esqueleto das entidades JPA;
17. justificativa das principais decisões.

## Processo obrigatório

Não implemente tudo silenciosamente.

Trabalhe por blocos:

### Etapa A
Modelo de domínio e decisões.

Aguarde aprovação.

### Etapa B
PostgreSQL + diagrama ER.

Aguarde aprovação.

### Etapa C
Migration + entidades JPA.

Aguarde aprovação.

### Etapa D
Revisão final de consistência.

Não avance para controllers, services ou frontend nesta Sprint sem autorização explícita.

---
