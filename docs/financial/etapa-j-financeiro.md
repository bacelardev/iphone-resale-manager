# Etapa J — Financeiro, implantação e Delarte Control

Status: **versão 1.0 proposta aguardando aprovação**.

## Objetivo

Fechar o ciclo financeiro operacional sem dupla contagem entre caixa, estoque e capital histórico,
concluir de forma definitiva a implantação de um negócio existente e disponibilizar Dashboard e
Financeiro com números oficiais.

## Marca

O nome visível do produto é **Delarte Control**. A assinatura institucional é
**Desenvolvido por Andelar**. O repositório `iphone-resale-manager`, o package
`io.github.bacelardev.iphoneresale`, o schema PostgreSQL, `/api/v1`, migrations históricas,
prefixo `irs_` e IDs persistidos permanecem inalterados.

## Decisões J-01 a J-12

Consulte `docs/decisions/architecture-decisions.md`. Em síntese: caixa e estoque são separados;
saldo zero não cria transação; capital histórico não é equity nem ledger; `ownerUser` difere de
`createdBy`; lançamentos são append-only; períodos são civis em `America/Bahia`;
`stockCapital` é snapshot; concorrência usa locks; a V6 é aditiva; a UI é mobile-first.

## Conclusão da implantação

`POST /api/v1/business-initialization/complete` executa atomicamente:

1. lock da implantação e validação de `expectedVersion`;
2. exigência do estado `PREPARING`;
3. validação de sócios únicos e existentes;
4. criação/reuso coerente do saldo de abertura;
5. persistência do capital histórico informativo;
6. transição para `COMPLETED`, com data e responsável;
7. auditoria financeira e da implantação.

Caixa positivo gera exatamente um `OPENING_BALANCE / INFLOW` em `cutoffAt`. Caixa zero
mantém `openingBalanceTransactionId = null`. Após concluir, importações `INITIAL_IMPORT`,
alteração do cutoff e nova conclusão ficam bloqueadas.

## Saldo inicial explícito

`POST /api/v1/financial/opening-balance` recebe `amount > 0`, `occurredAt` e `description`
durante `PREPARING`. `occurredAt` deve ser igual ao `cutoffAt`. A criação e a conclusão
compartilham a mesma trava consultiva transacional: duas requisições simultâneas não criam duas
aberturas, e a conclusão reutiliza a existente quando data e valor declarado coincidem.

Uma abertura existente bloqueia alteração posterior do `cutoffAt`. Divergência na conclusão usa
o código estável `INITIALIZATION_OPENING_BALANCE_MISMATCH`; uma segunda abertura usa
`OPENING_BALANCE_ALREADY_EXISTS`.

## Ledger operacional

- `DEVICE_PURCHASE / OUTFLOW`
- `DEVICE_PURCHASE_REVERSAL / INFLOW`
- `MAINTENANCE / OUTFLOW`
- `MAINTENANCE_REVERSAL / INFLOW`
- `SALE / INFLOW`
- `SALE_REVERSAL / OUTFLOW`
- `OWNER_CONTRIBUTION / INFLOW`
- `OWNER_WITHDRAWAL / OUTFLOW`
- `MANUAL_ADJUSTMENT / INFLOW|OUTFLOW`

Estorno manual usa `MANUAL_ADJUSTMENT`, direção oposta, mesmo valor e `reversalOf`. Somente
saldo inicial, aporte, retirada e ajuste manual originais podem ser estornados por esse fluxo.
Operações de aparelho, manutenção e venda são revertidas apenas pelos casos de uso responsáveis.

## Resumo

`GET /api/v1/financial/summary` usa `[from,to)`. Sem período explícito, usa o mês atual em
`America/Bahia`. Os limites efetivos ficam exclusivamente dentro de `period`:

```json
{
  "period": {
    "from": "2026-09-01T03:00:00Z",
    "to": "2026-10-01T03:00:00Z",
    "businessTimezone": "America/Bahia"
  },
  "openingBalance": 0.00,
  "closingBalance": 0.00,
  "revenue": 0.00,
  "devicePurchaseCost": 0.00,
  "maintenanceCost": 0.00,
  "profit": 0.00,
  "marginPercent": null,
  "stockCapital": 0.00,
  "calculatedAt": "2026-09-14T12:00:00Z"
}
```

`openingBalance` soma o efeito líquido anterior ao início. `closingBalance` agrega o movimento
do período. Receita inclui vendas ativas. Custos operacionais excluem importações históricas.
Lucro por venda usa preço menos compra e manutenções ativas; margem é lucro/receita, quatro casas,
ou `null` sem receita. `stockCapital` soma compra e manutenção ativa dos aparelhos atuais não
vendidos e não arquivados.

## Concorrência e banco

A V6 preserva V1–V5 e adiciona proteções para capital histórico imutável, conclusão consistente,
saldo de abertura coerente, temporalidade operacional e bloqueio de importação após conclusão.
Locks de implantação, lançamento original e advisory lock impedem dupla conclusão, dupla abertura
e duplo estorno sem deixar gravação parcial.

## Interface

A tela Financeiro contém cards, presets, filtros, ledger e ações manuais. O Dashboard consome os
mesmos dados oficiais. Valores são formatados em BRL. Lucro/prejuízo e entrada/saída usam texto,
ícone e cor. Dialogs mantêm foco, fecham com Escape e viram bottom sheet no mobile. Larguras-alvo:
375, 430, 768, 1024 e 1440 px.

## Limites

Histórico/auditoria final e gestão final de usuários permanecem para a Etapa K. Não há deploy,
serviços de produção, CRM, fiscal, garantia, comissão, envio, marketplace, multiempresa,
permissões avançadas ou analytics externo nesta etapa.
