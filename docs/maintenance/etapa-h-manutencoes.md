# Etapa H — Manutenções, Peças e Importação Histórica

**Versão:** 1.0 proposta

**Status:** aguardando aprovação

**Branch:** `codex/etapa-h-maintenance`

**PR:** #3 — draft, sem merge automático

## Escopo entregue

- catálogo de peças autenticado, pesquisável, versionado e sem hard delete;
- manutenção operacional e importação histórica com itens ordenados;
- total automático com `BigDecimal`, manutenção ativa e investimento do aparelho;
- ledger operacional positivo, custo zero sem ledger, cancelamento e reversão;
- `maintenanceCapital` histórico no preview da implantação;
- arquivamento com cancelamento automático e commit único;
- frontend responsivo com rotas, PartPicker, detalhe e cancelamento;
- V4 aditiva para invariantes de cutover no PostgreSQL.

## Fronteira temporal e financeira

| Origem | Janela | Ledger na criação | Reversão no cancelamento |
| --- | --- | --- | --- |
| `OPERATIONAL` | `performedAt >= purchasedAt`; com implantação, `performedAt > cutoffAt` | `MAINTENANCE/OUTFLOW` se total positivo | `MAINTENANCE_REVERSAL/INFLOW` se existiu saída |
| `INITIAL_IMPORT` | `PREPARING`, aparelho importado e compra ≤ manutenção ≤ cutoff | Nunca | Nunca |

Origem, data, itens, custos e responsável são imutáveis. Correção ocorre somente por
cancelamento e novo registro. Manutenção cancelada continua consultável e sai do total
ativo.

O contrato `financialImpact` representa o efeito líquido atual:

| Estado | Valor |
| --- | --- |
| `ACTIVE + OPERATIONAL + total > 0` | `OUTFLOW_CREATED` |
| `CANCELLED + OPERATIONAL + total > 0` | `OUTFLOW_REVERSED` |
| `OPERATIONAL + total = 0` | `NO_FINANCIAL_COST` |
| `INITIAL_IMPORT` | `HISTORICAL_COST_ONLY` |

No detalhe do aparelho, a seção de manutenções exibe quantidade ativa por
`totalElements` filtrado, `maintenanceTotal` calculado pelo backend e o responsável de
cada registro.

## Concorrência e atomicidade

A ordem canônica é aparelho → implantação, quando aplicável → manutenções em UUID.
Registro compete com arquivamento pelo lock do aparelho. Cancelamento bloqueia aparelho
e manutenção. O arquivamento cancela todas as ativas, cria reversões necessárias,
estorna compra operacional e arquiva na mesma transação. O evento principal de
arquivamento resume os efeitos automáticos.

## V4

`V4__maintenance_cutover_integrity.sql` adiciona dois triggers:

- valida origem, estado `PREPARING`, origem do aparelho e janela temporal no INSERT;
- rejeita mudança de cutoff que reclassificaria dispositivos ou manutenções existentes.

Validação SQL direta executou V1, V2, V3 e V4 em transações sobre PostgreSQL 16.13,
aceitou os dois limites válidos e rejeitou três violações com SQLSTATE `23514`.
V1/V2/V3 permanecem byte a byte fora do escopo.

## Frontend e acessibilidade

O PartPicker adapta o CatalogPicker/Dialog/BottomSheet aprovado na F/G. Preserva busca,
listbox, setas, Home/End, Enter, trap de foco, Escape, restauração, reduced motion e
comportamento mobile-first. O formulário mostra a data de compra, cutoff, aviso histórico
e total apenas como preview.

## Evidências finais

O head técnico validado é
[`6787c324ee11259f564c3d84d33e9dd134aa5c4a`](https://github.com/bacelardev/iphone-resale-manager/commit/6787c324ee11259f564c3d84d33e9dd134aa5c4a).

- [Backend verify `34487892600`](https://github.com/bacelardev/iphone-resale-manager/actions/runs/34487892600):
  `success`, 53 testes (24 unitários e 29 de integração), sem falhas, erros ou skips;
- [Frontend verify `34487892690`](https://github.com/bacelardev/iphone-resale-manager/actions/runs/34487892690):
  `success`, Prettier, ESLint, TypeScript, build, 45/45 Vitest em 9 arquivos e
  33/33 Playwright, sem skips;
- PostgreSQL 16.15, Flyway V1–V4, Hibernate `ddl-auto=validate`, backend real e
  autenticação/CORS aprovados no CI;
- axe e responsividade aprovados em 375, 430, 768, 1024 e 1440 px;
- [screenshots responsivos](https://github.com/bacelardev/iphone-resale-manager/actions/runs/34487892690/artifacts/10156553778)
  publicados como artefato do workflow;
- validação SQL direta adicional em PostgreSQL 16.13 aceitou os dois limites válidos da
  V4 e rejeitou três violações com SQLSTATE `23514`.

## Limites

- Não há PATCH de manutenção, reativação ou seed silencioso de peças.
- Conclusão da implantação, venda, financeiro completo e Etapa I não foram iniciados.
- A Etapa H não está aprovada nem mergeada; aguarda revisão explícita.
