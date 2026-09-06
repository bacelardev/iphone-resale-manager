# Kit de Documentação - MVP Gestão de Revenda de iPhones

Versão: **1.0**  
Status: **Base oficial para início da implementação**

Este diretório contém a fonte da verdade do projeto. O objetivo é permitir que qualquer nova conversa, sessão de Work, Codex ou IDE consiga retomar o projeto sem depender do histórico completo do chat.

## Regra principal

Antes de implementar qualquer funcionalidade:

1. Ler os documentos desta pasta.
2. Não alterar regras de negócio sem registrar a decisão.
3. Não introduzir dependências, entidades ou fluxos novos sem necessidade.
4. Priorizar simplicidade, segurança, performance e manutenção.
5. Manter o MVP enxuto para uso de dois sócios.
6. Preservar compatibilidade futura com aplicativo iOS consumindo a mesma API.

## Arquivos

- `01-contexto-negocio.md` — visão do negócio e problema.
- `02-escopo-mvp.md` — funcionalidades incluídas na versão 1.0.
- `03-arquitetura-tecnica.md` — stack e decisões arquiteturais.
- `04-regras-negocio.md` — regras funcionais do sistema.
- `05-mapa-telas.md` — telas e fluxos principais.
- `06-design-system.md` — direção visual e referências.
- `07-modelagem-dominio-pendente.md` — requisitos para a Sprint 1 de domínio/banco.
- `08-roadmap-implementacao.md` — ordem recomendada de construção.
- `09-instrucoes-para-work.md` — prompt operacional para iniciar uma nova sessão.
- `10-etapa-a-modelo-dominio.md` — modelo de domínio e decisões A-01 a A-14 aprovados.
- `11-etapa-b-postgresql-er.md` — modelo lógico, diagrama ER e integridade PostgreSQL aprovados.
- `12-etapa-c-flyway-jpa.md` — migration, estrutura Java, mapeamentos JPA e validação integrada aprovados.
- `schema-v1-proposal.sql` — DDL PostgreSQL de referência aprovado na Etapa B.
- `CHANGELOG.md` — histórico das mudanças de documentação.

## Observação

Esta documentação representa a versão 1.0 do planejamento. Pode evoluir conforme o sistema for usado na prática.
