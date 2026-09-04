# Escopo do MVP — Versão 1.0

## 1. Autenticação e usuários

O sistema terá usuários identificados.

Dados mínimos:

- nome;
- senha;
- cargo fixo: `SÓCIO`.

Objetivo principal:

- identificar quem realizou cada alteração;
- evitar acesso casual não autorizado ao sistema.

Não há necessidade inicial de:

- telefone;
- e-mail;
- recuperação complexa de conta;
- múltiplos níveis de permissão.

## 2. Cadastro de iPhone

Campos obrigatórios:

- modelo;
- cor;
- capacidade de armazenamento;
- preço de compra;
- Face ID funcionando: sim/não;
- tela original: sim/não;
- bateria original: sim/não;
- saúde da bateria (%);
- entre 2 e 4 fotos;
- status inicial.

Campos adicionais podem ser incluídos posteriormente.

## 3. Modelos

O seletor deve contemplar os modelos utilizados pela operação, a partir da linha iPhone 11, incluindo variantes como:

- iPhone 11;
- iPhone 11 Pro;
- iPhone 11 Pro Max;
- gerações seguintes;
- Pro;
- Pro Max;
- demais variantes relevantes.

A lista deve ser centralizada e padronizada para evitar texto livre inconsistente.

## 4. Status do aparelho

Estados principais:

- `PENDENTE_MANUTENCAO`
- `DISPONIVEL_VENDA`
- `VENDIDO`

Regra:

- se o aparelho exigir reparo, entra como pendente;
- se não exigir reparo, pode entrar diretamente como disponível;
- ao registrar uma venda, o status muda automaticamente para vendido.

## 5. Fotos

Cada aparelho deverá possuir de 2 a 4 fotos.

Objetivos:

- registro visual;
- histórico;
- identificação do estado do aparelho.

As imagens devem ser armazenadas em storage, não diretamente como binário no PostgreSQL.

Pode haver compressão/conversão para formato web mais eficiente, desde que sem prejudicar a experiência.

## 6. Manutenção

O sistema deve permitir registrar zero ou várias peças substituídas.

Cada item de manutenção deve possuir:

- peça;
- custo;
- data;
- usuário responsável.

Exemplos:

- tela;
- bateria;
- tampa traseira;
- câmera;
- outros itens.

O sistema deve calcular automaticamente:

- total de manutenção;
- investimento total no aparelho.

## 7. Venda

Fluxo:

1. clicar em `Registrar venda`;
2. selecionar um aparelho ainda não vendido;
3. exibir automaticamente:
   - custo de compra;
   - custo de manutenção;
   - investimento total;
4. informar valor da venda;
5. calcular:
   - lucro;
   - margem;
6. confirmar;
7. alterar status para `VENDIDO`;
8. registrar usuário responsável e data.

## 8. Financeiro

O sistema deve permitir visualizar:

- saldo;
- faturamento;
- lucro;
- margem;
- custo de compra de aparelhos;
- custo de manutenção;
- capital investido em estoque.

Filtros:

- dia;
- semana;
- mês;
- ano;
- período personalizado.

## 9. Histórico e auditoria

Toda ação importante deve registrar:

- usuário;
- data/hora;
- ação;
- entidade afetada;
- informação suficiente para entender o evento.

Exemplos:

- aparelho cadastrado;
- manutenção adicionada;
- status alterado;
- venda concluída;
- registro editado.

## 10. Fora do MVP

Não fazem parte da versão 1.0:

- multiempresa;
- estoque multi-filial;
- emissão fiscal;
- integração com marketplace;
- integração com WhatsApp;
- pagamentos online;
- controle de funcionários;
- CRM;
- Android;
- sistema avançado de permissões;
- automações comerciais.
