# Regras de Negócio

## Aparelhos

Cada aparelho deve ter um identificador interno único.

O modelo do aparelho não deve ser usado como identificador único.

Um aparelho pode ter:

- zero ou várias manutenções;
- várias vendas históricas, no máximo uma ativa;
- várias fotos;
- vários eventos de auditoria.

## Cadastro

Para cadastrar um aparelho, são obrigatórios:

- modelo;
- cor;
- armazenamento;
- preço de compra;
- condição do Face ID;
- originalidade da tela;
- originalidade da bateria;
- saúde da bateria;
- fotos mínimas;
- status inicial.

## Status

Transições básicas:

```text
PENDENTE_MANUTENCAO
        |
        v
DISPONIVEL_VENDA
        |
        v
VENDIDO
```

Também pode existir:

```text
CADASTRO SEM MANUTENÇÃO
        |
        v
DISPONIVEL_VENDA
```

Não permitir vender aparelho já vendido.

## Custos

### Custo de compra

É o valor pago na aquisição do aparelho.

### Custo de manutenção

É a soma dos itens das manutenções `ACTIVE`; canceladas não compõem investimento.

### Investimento total

```text
investimento_total = custo_compra + custo_manutencao
```

## Venda

### Lucro

```text
lucro = valor_venda - investimento_total
```

### Margem

No MVP, usar:

```text
margem_percentual = lucro / valor_venda * 100
```

Essa fórmula deve ser documentada e mantida consistente em todo o sistema.

## Capital em estoque

Representa o valor investido em aparelhos ainda não vendidos.

```text
capital_em_estoque =
soma(custo_compra + manutencao)
dos aparelhos não vendidos
```

## Histórico

Registros de auditoria não devem ser apagados como parte do fluxo normal.

## Exclusão

Preferir exclusão lógica ou bloqueio de exclusão para registros financeiros relevantes.

Vendas concluídas e movimentações financeiras não devem desaparecer por exclusão acidental.


## Refinamento da Etapa G — cutover e estoque inicial

- `cutoffAt` é persistido quando a implantação entra em `PREPARING`, antes de qualquer carga inicial.
- Após o primeiro aparelho `INITIAL_IMPORT`, o cutoff fica imutável.
- Aparelho `OPERATIONAL` representa compra dentro do período controlado e gera `DEVICE_PURCHASE / OUTFLOW`.
- Aparelho `INITIAL_IMPORT` já pertencia ao estoque, preserva preço/data/custo e não gera nova saída de caixa.
- A API de importação lê o cutoff persistido e exige `purchasedAt <= cutoffAt`.
- `stockCapital` soma compra e manutenções ativas; na G, `maintenanceCapital = 0` no preview porque a importação histórica de manutenção pertence à H.
- Arquivamento é terminal e torna o aparelho imutável.
- Fotos ficam fora do PostgreSQL, com 2–4 ativas, remoção lógica, validação de MIME/magic bytes/tamanho e URL assinada.
- `maintenance.registration_origin`, `financial_transaction.owner_user_id` e `owner_capital_opening` são somente preparação de schema/JPA nesta etapa.

## Refinamento da Etapa H — manutenção e peças

- Manutenção tem ao menos um item, responsável autenticado, origem imutável e total
  sempre recalculado no backend com `BigDecimal`/`numeric(14,2)`.
- `OPERATIONAL` exige período controlado: após o cutoff quando houver implantação, ou
  não antes da compra quando ainda não houver. Total positivo gera saída; zero não gera.
- `INITIAL_IMPORT` exige implantação `PREPARING`, aparelho importado e intervalo entre
  compra e cutoff, inclusive. Preserva custo histórico e nunca gera saída de caixa.
- Peças são catálogo real, sem seed silencioso e sem hard delete. `OTHER` exige detalhes
  com 1–255 caracteres após trim.
- Manutenção confirmada é imutável. Correção ocorre por cancelamento e novo registro.
- Cancelamento operacional positivo cria estorno; operacional zero e histórico não.
- Manutenções canceladas permanecem consultáveis e deixam de compor o investimento.
- Arquivamento cancela todas as manutenções ativas e seus efeitos necessários em commit
  único. Ordem canônica de locks: aparelho, implantação quando aplicável e manutenções
  por UUID.
- V4 protege no PostgreSQL a fronteira temporal e alterações conflitantes do cutoff;
  V1, V2 e V3 permanecem imutáveis.
- Venda, conclusão da implantação e Etapa I continuam fora do escopo.

## Refinamento da Etapa I — vendas

- Venda é operacional, inclusive sobre aparelho importado; `PREPARING` permite venda após cutoff.
- Exige aparelho disponível, não arquivado, sem outra venda ativa e versão atual.
- `soldAt >= purchasedAt`, `soldAt >= max(performedAt ACTIVE)` e, com implantação, `soldAt > cutoffAt`.
- `investmentTotal = purchasePrice + maintenanceTotal ACTIVE`; `profit = salePrice - investmentTotal`.
- `marginPercent = profit / salePrice * 100`, quatro casas `HALF_UP`; prejuízo permitido.
- Venda/status/entrada/auditoria confirmam juntos. Cancelamento estorna sem editar/apagar o original.
- Cancelamento retorna somente a disponível; permite nova venda e preserva a venda anterior.
- Lock canônico começa por Device, compartilhado com manutenção/arquivamento.
- V5 protege estado, cronologia, imutabilidade e mudança conflitante de cutoff sem alterar V1–V4.
- A Etapa I está aprovada e encerrada na versão 1.0; financeiro completo e conclusão da implantação pertencem à J e não foram iniciados.
