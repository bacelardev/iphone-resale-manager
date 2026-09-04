# Mapa de Telas

## Fluxo principal

```text
Login
  |
  v
Dashboard
  |
  +--> Aparelhos
  |      |
  |      +--> Cadastrar iPhone
  |      |
  |      +--> Detalhes do aparelho
  |              |
  |              +--> Registrar manutenção
  |              |
  |              +--> Registrar venda
  |
  +--> Financeiro
  |
  +--> Histórico
  |
  +--> Usuários / Configurações
```

## 1. Login

Elementos:

- nome;
- senha;
- botão entrar.

## 2. Dashboard

Responder rapidamente:

- quanto dinheiro existe;
- quanto foi o lucro;
- quanto está investido em estoque;
- quantos aparelhos estão disponíveis;
- quantos estão pendentes de manutenção.

Também deve exibir:

- gráfico simples;
- atividade recente.

## 3. Aparelhos

Elementos:

- busca;
- filtros;
- lista/cards;
- status;
- valor investido;
- informações resumidas;
- botão cadastrar.

## 4. Cadastrar iPhone

Campos:

- modelo;
- cor;
- capacidade;
- preço de compra;
- Face ID;
- tela original;
- bateria original;
- saúde da bateria;
- fotos;
- status.

## 5. Detalhes

Exibir:

- fotos;
- informações principais;
- resumo financeiro;
- características;
- histórico;
- ações.

Ações:

- editar;
- registrar manutenção;
- registrar venda;
- alterar status.

## 6. Registrar manutenção

Permitir múltiplos itens.

Cada linha:

- peça;
- custo;
- remover/adicionar.

Exibir total automaticamente.

## 7. Registrar venda

Exibir:

- aparelho;
- custo de compra;
- manutenção;
- investimento total;
- valor da venda;
- lucro;
- margem.

## 8. Financeiro

Filtros:

- dia;
- semana;
- mês;
- ano;
- personalizado.

Indicadores:

- faturamento;
- lucro;
- custo de compra;
- custo de manutenção;
- saldo inicial;
- saldo final;
- capital em estoque.

## 9. Histórico

Timeline de auditoria com:

- data;
- usuário;
- ação;
- aparelho ou entidade envolvida.

## 10. Usuários / Configurações

MVP:

- visualizar usuários;
- alterar configurações mínimas;
- sem sistema complexo de permissões.
