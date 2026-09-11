# Etapa I — Vendas, lucro, margem e reversão

**Status:** Etapa I — versão 1.0 proposta aguardando aprovação.
**Branch:** `codex/etapa-i-sales`.
**Base:** `520d8a871b4f827a8aa927b24f14a9447264d6e1` (merge da Etapa H).
**PR:** draft; sem merge automático. Etapa J não iniciada.

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

Evidências finais do head técnico e workflows serão registradas após a execução do CI.
O ambiente local tem Node 24; Java 21/PostgreSQL 16 são validados no GitHub Actions.
Nenhum teste não executado é contado como aprovado.

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
