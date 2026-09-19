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

## Interfaces derivadas do baseline visual

O mapa visual oficial também prevê os estados abaixo. Eles não representam
necessariamente páginas React independentes: podem ser Dialog, Drawer, BottomSheet,
lightbox ou estado da própria tela.

11. Editar iPhone;
12. Alterar status;
13. Visualizador de fotos;
14. Arquivar aparelho;
15. Detalhes da manutenção;
16. Cancelar manutenção;
17. Detalhes da venda;
18. Cancelar venda;
19. Nova movimentação manual;
20. Extrato financeiro detalhado;
21. Detalhes da auditoria;
22. Meu perfil / sessão.

Na Etapa F somente Login, App Shell, dashboard estrutural, navegação, autenticação,
estados globais e placeholders protegidos são implementados. As demais interfaces
permanecem mapeadas para etapas futuras, sem antecipar regras de negócio.


## Telas implementadas na Etapa G

- `/devices`: estoque real, busca, filtros, paginação, cards responsivos e drawer mobile.
- `/devices/new`: cadastro operacional com impacto financeiro explícito.
- `/devices/import`: importação inicial separada, cutoff visível e aviso de ausência de nova saída.
- `/devices/:id`: detalhe, lightbox, edição, status, fotos e arquivamento terminal.
- `/settings/catalogs`: administração real de modelos e cores.
- estado de preparação: CTA quando `NOT_STARTED` e banner discreto quando `PREPARING`.

O seletor de modelo é combobox pesquisável no desktop e bottom sheet no mobile. Caixa/capital, manutenção, venda e dashboard financeiro continuam reservados às etapas seguintes.

## Telas implementadas na proposta da Etapa H

- `/settings/catalogs`: terceira seção real para Peças, com criação, busca, edição de
  nome, ativação e desativação.
- `/devices/:deviceId`: histórico técnico, total ativo, quantidade, origem, responsável,
  status e ações elegíveis.
- `/devices/:deviceId/maintenances/new`: formulário operacional com itens dinâmicos e
  total de preview.
- `/devices/:deviceId/maintenances/import`: aviso histórico, compra/cutoff e ausência de
  nova saída de caixa.
- `/devices/:deviceId/maintenances/:maintenanceId`: detalhe imutável, itens, custo,
  origem, impacto financeiro e cancelamento em Dialog/BottomSheet.

PartPicker reutiliza o CatalogPicker aprovado: busca por nome/código, combobox semântico,
listbox com setas/Home/End/Enter, trap de foco, Escape, restauração e sheet mobile. As
cinco larguras oficiais permanecem 375, 430, 768, 1024 e 1440 px. Venda não foi iniciada.

## Telas da Etapa I — versão 1.0 aprovada

| Tela | Rota/estado | Comportamento |
| --- | --- | --- |
| 07 — Registrar venda | `/devices/:deviceId/sale/new` | Compra, manutenção ativa, investimento, preço/data local e preview de lucro/margem; prejuízo permitido. |
| 17 — Detalhes da venda | `/devices/:deviceId/sale` | Valores oficiais, status, responsável, data econômica, criação e entrada gerada. |
| 18 — Cancelar venda | Dialog desktop / bottom sheet mobile | Resumo, motivo, aviso de estorno, envio único, foco/Escape/restauração. |
| Detalhe do aparelho | `/devices/:id` | Registrar venda somente disponível/não arquivado; Ver venda quando vendido. |
| Estoque | `/devices` | Cache invalidado após venda/cancelamento para refletir vendido/disponível. |

Após cancelamento, a navegação retorna ao aparelho disponível com feedback de sucesso.
Lucro é verde, prejuízo/cancelamento vermelho e CTA principal branco. Datas usam o
helper local aprovado. Nenhuma tela financeira da Etapa J foi implementada.


## Etapa J — telas ativas

### Dashboard

Cards reais para saldo atual, faturamento, lucro/prejuízo, margem, capital em estoque e contagens
por estado do aparelho. Financeiro e Aparelhos deixam de exibir “Em breve”; Histórico permanece
reservado.

### Financeiro

Presets Hoje/Semana/Mês/Ano/Personalizado; cards oficiais; explicação entre caixa e estoque;
ledger filtrável; tabela fluida/lista mobile; aporte, retirada, ajuste e estorno em dialog central
no desktop e bottom sheet no mobile.

### Conclusão da implantação

Durante `PREPARING`, exibe data de corte, aparelhos importados, capital em estoque, caixa real,
capital histórico por sócio e aviso irreversível:
“Após concluir, aparelhos e manutenções históricas não poderão mais ser importados.”
