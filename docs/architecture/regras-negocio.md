# Regras de Negócio

## Aparelhos

Cada aparelho deve ter um identificador interno único.

O modelo do aparelho não deve ser usado como identificador único.

Um aparelho pode ter:

- zero ou várias manutenções;
- zero ou uma venda;
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

É a soma dos itens de manutenção registrados.

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
