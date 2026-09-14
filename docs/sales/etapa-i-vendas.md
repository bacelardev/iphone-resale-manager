# Etapa I — Vendas, lucro, margem e reversão

**Versão:** 1.0 aprovada.
**Status:** Etapa I encerrada.
**Branch:** `codex/etapa-i-sales`.
**Base:** `520d8a871b4f827a8aa927b24f14a9447264d6e1` (merge da Etapa H).
**PR:** [#4](https://github.com/bacelardev/iphone-resale-manager/pull/4), aprovado para merge. Etapa J não iniciada.

## Fechamento

A versão 1.0 foi aprovada em 14/09/2026. O fechamento é exclusivamente documental:
nenhum código funcional, contrato ou comportamento foi alterado. Permanecem integrais
as migrations V1–V5, as decisões A–I, a security baseline e os baselines visuais
V2 + Mobile-first. A Etapa J, o financeiro posterior e a conclusão da implantação
não foram iniciados nem antecipados.

## Entrega

Registro, consulta da venda ativa e cancelamento nos três endpoints aprovados.
Sale, status do aparelho, ledger e auditoria confirmam em transação única; cancelamento
cria estorno vinculado à entrada original e permite revenda. Custos derivam de compra e
manutenções ativas, com lucro negativo permitido e margem de quatro casas `HALF_UP`.

A V5 protege disponibilidade, arquivamento, compra, manutenção ativa e cutoff no
PostgreSQL; mudanças de cutoff consideram vendas ativas e canceladas. V1–V4 permanecem
idênticas à base aprovada. Não há colunas financeiras derivadas ou dependências novas.

Frontend inclui formulário com preview, detalhe com valores oficiais e cancelamento
acessível em dialog/sheet. O estoque é invalidado após venda e estorno, e o aparelho
retorna a disponível com feedback de sucesso. Segue os dois SVGs oficiais da F.

## Validação

**Head técnico validado:** `b2e8bc7a84c87bc8793199b2ad6401f56cd3ce66`.

- [Backend verify #42](https://github.com/bacelardev/iphone-resale-manager/actions/runs/34723033256): Java 21, Maven 3.9.16 e `mvn clean verify` verdes. Foram 29 testes unitários e 46 de integração (75 no total), sem falhas ou skips; a Etapa I adiciona 5 testes de domínio e 17 cenários de integração.
- [Frontend verify #61](https://github.com/bacelardev/iphone-resale-manager/actions/runs/34723033250): lint, Prettier, TypeScript e build verdes; 66 testes unitários/componentes em 13 arquivos e 46 testes Playwright, todos aprovados sem skips.
- O frontend iniciou o backend real contra PostgreSQL 16.15; Flyway aplicou a cadeia V1–V5 e Hibernate `ddl-auto=validate` iniciou sem divergência.
- Playwright cobriu autenticação real, venda, cancelamento e estorno, revenda, prejuízo permitido, cronologia, estoque atualizado, foco/Escape, axe e as larguras 375, 430, 768, 1024 e 1440 px.
- [Artefato `frontend-responsive-screenshots`](https://github.com/bacelardev/iphone-resale-manager/actions/runs/34723033250/artifacts/10307355038): 2.901.536 bytes, SHA-256 `32bbf1b3163cf4c8b2f3541d7020bd6d4261acb4b8a83069d891aa0b3dfcefbf`, retenção até 19/09/2026.

Nenhum teste não executado foi contado como aprovado. As falhas intermediárias foram
de precisão temporal e escopo de seletores de teste; foram corrigidas antes do head
técnico final, sem relaxar regras de negócio ou acessibilidade.

Cobertura adicionada: contratos/validações, cálculos, prejuízo/zero, cutoff, cronologia,
reversão, versões, ledger ausente, revenda, imutabilidade SQL, concorrência de vendas e
manutenção, API frontend, CTA, invalidação, foco, axe e cinco larguras.
O caso de ledger ausente simula somente o retorno do repository, mantendo a aplicação
HTTP e o restante da persistência reais; não desabilita proteções do banco.

## Decisões e limites

Decisões I-01 a I-20 estão em `docs/decisions/architecture-decisions.md`.
Consulta ativa usa snapshot `REPEATABLE_READ` para consistência de leitura concorrente.
Motivo completo é preservado na venda/auditoria; descrição de estorno é curta.

Não há CRM, comprador, pagamento, parcelamento, garantia, importação de vendas,
lista global, dashboard financeiro final, caixa inicial, aportes/retiradas ou conclusão
da implantação. Essas funcionalidades não foram antecipadas. Não houve deploy.
Axe automatizado não substitui avaliação completa com leitores de tela.
