# Sprint 1 — Etapa A: Modelo de Domínio e Decisões

Versão: **1.0 aprovada**  
Status: **aprovada em 03/09/2026**  
Escopo desta etapa: domínio, atributos conceituais, relacionamentos, invariantes e decisões arquiteturais.  
Fora desta etapa: DDL PostgreSQL, diagrama ER físico, migration Flyway e código JPA.

## 1. Princípios adotados

Este modelo aprovado parte exclusivamente da documentação versão 1.0 e segue estes princípios:

- manter o MVP adequado a dois sócios;
- concentrar regras e cálculos financeiros no backend;
- preservar o histórico de compras, manutenções, vendas e alterações;
- evitar dados financeiros derivados inconsistentes;
- não acoplar o domínio ao Supabase ou a outro provedor de infraestrutura;
- permitir que um aplicativo iOS futuro consuma a mesma API;
- não incluir CRM, marketplace, pagamentos, emissão fiscal ou multiempresa.

As decisões A-01 a A-14 foram aprovadas. A-09 e A-10 integram o MVP.

## 2. Vocabulário do domínio

| Termo | Significado no sistema |
| --- | --- |
| Aparelho | Um iPhone físico adquirido para manutenção e/ou revenda. |
| Modelo | Nome comercial padronizado, por exemplo, iPhone 13 Pro Max. |
| Compra | Aquisição do aparelho; no MVP, seus dados pertencem ao próprio aparelho. |
| Manutenção | Evento realizado em um aparelho, contendo uma ou mais peças/serviços. |
| Investimento | Preço de compra mais o custo das manutenções válidas do aparelho. |
| Venda ativa | Venda concluída e não cancelada. |
| Lucro | Valor de venda menos investimento total. |
| Margem | Lucro dividido pelo valor de venda, multiplicado por 100. |
| Saldo operacional | Entradas menos saídas registradas pelo sistema. |
| Auditoria | Registro imutável de uma ação relevante e de seu autor. |

## 3. Fronteiras do domínio

### 3.1 Aparelho

O aparelho é a raiz principal do domínio. Ele controla identificação, características, preço de compra, fotos e estado operacional.

### 3.2 Manutenção

Cada manutenção representa um evento realizado em uma data e por um usuário. Seus itens são confirmados como um único conjunto. Se peças forem substituídas em dias diferentes, devem pertencer a manutenções diferentes.

### 3.3 Venda

A venda é concluída em uma transação única com a mudança do aparelho para `VENDIDO`, o registro financeiro e a auditoria.

### 3.4 Financeiro

O financeiro não altera as fórmulas dos demais módulos. Ele consolida compras, manutenções, vendas e ajustes explícitos de caixa por meio do ledger aprovado.

### 3.5 Auditoria

A auditoria é transversal, explícita e independente do log técnico da aplicação.

## 4. Entidades e atributos conceituais

Os tipos desta seção são tipos de domínio Java. Os tipos PostgreSQL serão definidos somente na Etapa B.

Campos comuns das entidades mutáveis:

| Atributo | Tipo | Regra |
| --- | --- | --- |
| `id` | `UUID` | Identificador técnico estável e imutável. |
| `createdAt` | `Instant` | Momento de criação. |
| `createdBy` | `User` | Autor da criação; nulo somente em carga inicial controlada pelo sistema. |
| `updatedAt` | `Instant` | Momento da última alteração. |
| `updatedBy` | `User` | Autor da última alteração. |
| `version` | `Long` | Controle de concorrência otimista. |

### 4.1 User

Representa uma pessoa autorizada a entrar no sistema.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Imutável. |
| `name` | `String` | Sim | Nome exibido na interface e auditoria. |
| `username` | `String` | Sim | Identificador de login único, sem diferenciar maiúsculas/minúsculas. |
| `passwordHash` | `String` | Sim | Nunca armazena senha em texto puro. |
| `role` | `UserRole` | Sim | No MVP, somente `SOCIO`. |
| `active` | `boolean` | Sim | Usuário inativo não autentica, mas permanece no histórico. |
| campos comuns | — | Sim | Auditoria de criação/alteração e versão. |

Decisão aprovada: separar `username` de `name`. O nome pode mudar; o identificador de login precisa ser único e previsível.

### 4.2 IphoneModel

Catálogo padronizado de modelos aceitos pelo sistema.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Imutável. |
| `code` | `String` | Sim | Código técnico único, por exemplo `IPHONE_13_PRO_MAX`. |
| `name` | `String` | Sim | Nome de exibição único. |
| `active` | `boolean` | Sim | Modelos antigos são desativados, não excluídos. |
| `displayOrder` | `Integer` | Sim | Ordenação do seletor. |
| campos comuns | — | Sim | Auditoria e versão. |

Decisão aprovada: usar catálogo em tabela, não enum. Novos iPhones são lançados periodicamente e não devem exigir alteração do enum do domínio.

### 4.3 DeviceColor

Catálogo global de cores comerciais.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Imutável. |
| `code` | `String` | Sim | Código único normalizado. |
| `name` | `String` | Sim | Nome de exibição único. |
| `active` | `boolean` | Sim | Cores deixam de aparecer no cadastro sem afetar aparelhos antigos. |
| campos comuns | — | Sim | Auditoria e versão. |

Decisão aprovada: catálogo global simples, sem relacionamento modelo-cor no MVP. Isso impede texto livre inconsistente sem criar uma matriz de combinações desnecessária.

### 4.4 Device

Representa cada iPhone físico adquirido pela operação.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Identificador técnico. |
| `internalCode` | `String` | Sim | Código curto, único, gerado pelo sistema e imutável. |
| `model` | `IphoneModel` | Sim | Modelo ativo no momento do cadastro. |
| `color` | `DeviceColor` | Sim | Cor ativa no momento do cadastro. |
| `storageGb` | `Integer` | Sim | Uma das capacidades suportadas: 64, 128, 256, 512, 1024 ou 2048 GB. |
| `purchasePrice` | `BigDecimal` | Sim | Estritamente maior que zero; nunca `double`. |
| `purchasedAt` | `Instant` | Sim | Data efetiva da compra, independente do cadastro. |
| `faceIdWorking` | `boolean` | Sim | Estado no cadastro/última avaliação. |
| `originalScreen` | `boolean` | Sim | Originalidade da tela. |
| `originalBattery` | `boolean` | Sim | Originalidade da bateria. |
| `batteryHealthPercent` | `Integer` | Sim | Entre 0 e 100; `0` representa valor indisponível/não aferido, e não saúde real de 0%. |
| `status` | `DeviceStatus` | Sim | Nunca inicia como `VENDIDO`. |
| `archivedAt` | `Instant` | Não | Exclusão lógica administrativa. |
| `archivedBy` | `User` | Não | Obrigatório quando arquivado. |
| campos comuns | — | Sim | Auditoria e versão. |

Não foram adicionados IMEI, número de série, fornecedor, cliente ou garantia porque não pertencem ao escopo documentado do MVP.

### 4.5 DevicePhoto

Metadado de uma foto armazenada fora do PostgreSQL.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Identificador da foto. |
| `device` | `Device` | Sim | Aparelho proprietário. |
| `storageKey` | `String` | Sim | Chave única do objeto no storage; não é uma URL pública fixa. |
| `originalFilename` | `String` | Sim | Nome apenas para referência, sem uso como chave. |
| `mimeType` | `String` | Sim | Limitado aos formatos permitidos pela aplicação. |
| `sizeBytes` | `Long` | Sim | Deve respeitar o limite de upload. |
| `position` | `Integer` | Sim | De 1 a 4 e única dentro do aparelho. |
| `removedAt` | `Instant` | Não | Remoção lógica para preservar histórico. |
| `createdAt` | `Instant` | Sim | Momento do registro. |
| `createdBy` | `User` | Sim | Autor. |

O cadastro válido deve terminar com 2 a 4 fotos ativas. A aplicação valida a contagem na mesma operação de cadastro; o banco limitará posição e duplicidade na Etapa B.

### 4.6 PartCatalog

Catálogo padronizado de peças ou serviços de manutenção.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Imutável. |
| `code` | `String` | Sim | Código único, por exemplo `SCREEN`, `BATTERY`, `BACK_GLASS`, `CAMERA`, `OTHER`. |
| `name` | `String` | Sim | Nome exibido ao usuário. |
| `active` | `boolean` | Sim | Desativação preserva referências antigas. |
| campos comuns | — | Sim | Auditoria e versão. |

Decisão aprovada: catálogo em tabela, permitindo relatórios consistentes e inclusão futura de peças sem alterar o enum do domínio.

### 4.7 Maintenance

Representa uma intervenção confirmada em um aparelho.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Identificador do evento. |
| `device` | `Device` | Sim | Aparelho ainda não vendido. |
| `performedAt` | `Instant` | Sim | Data efetiva da manutenção. |
| `responsibleUser` | `User` | Sim | Usuário responsável informado no evento. |
| `status` | `MaintenanceStatus` | Sim | `ACTIVE` ou `CANCELLED`. |
| `cancelledAt` | `Instant` | Não | Preenchido no cancelamento. |
| `cancelledBy` | `User` | Não | Autor do cancelamento. |
| `cancellationReason` | `String` | Não | Obrigatório no cancelamento. |
| campos comuns | — | Sim | Auditoria e versão. |

O total da manutenção é a soma dos itens e não é armazenado como campo duplicado.

### 4.8 MaintenanceItem

Representa uma peça ou serviço dentro de uma manutenção.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Identificador do item. |
| `maintenance` | `Maintenance` | Sim | Cabeçalho proprietário. |
| `part` | `PartCatalog` | Sim | Peça padronizada. |
| `details` | `String` | Condicional | Obrigatório para `OTHER`; opcional nos demais casos. |
| `cost` | `BigDecimal` | Sim | Maior ou igual a zero. |
| `position` | `Integer` | Sim | Ordem única dentro da manutenção. |

Uma manutenção confirmada possui pelo menos um item. Os itens não existem fora da manutenção.

### 4.9 Sale

Representa uma tentativa de venda concluída ou posteriormente cancelada.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Identificador da venda. |
| `device` | `Device` | Sim | Aparelho vendido. |
| `salePrice` | `BigDecimal` | Sim | Estritamente maior que zero. |
| `soldAt` | `Instant` | Sim | Data efetiva da venda. |
| `responsibleUser` | `User` | Sim | Usuário que registrou a venda. |
| `status` | `SaleStatus` | Sim | `ACTIVE` ou `CANCELLED`. |
| `cancelledAt` | `Instant` | Não | Preenchido no cancelamento. |
| `cancelledBy` | `User` | Não | Autor do cancelamento. |
| `cancellationReason` | `String` | Não | Obrigatório no cancelamento. |
| campos comuns | — | Sim | Auditoria e versão. |

Lucro, margem e investimento não são persistidos na venda nesta modelagem. Eles são calculados no backend a partir de dados válidos e imutáveis para um aparelho vendido.

Decisão aprovada: permitir várias vendas históricas canceladas para o mesmo aparelho, mas no máximo uma venda ativa. Uma nova venda somente pode ocorrer após cancelamento explícito da anterior.

### 4.10 FinancialTransaction

Ledger simples e imutável para que “saldo” signifique dinheiro controlado pelo sistema, e não apenas lucro acumulado.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Identificador do lançamento. |
| `direction` | `FinancialDirection` | Sim | `INFLOW` ou `OUTFLOW`. |
| `type` | `FinancialTransactionType` | Sim | Origem econômica do lançamento. |
| `amount` | `BigDecimal` | Sim | Sempre maior que zero; o sinal vem de `direction`. |
| `occurredAt` | `Instant` | Sim | Momento econômico usado nos filtros. |
| `device` | `Device` | Condicional | Presente em compra de aparelho. |
| `maintenance` | `Maintenance` | Condicional | Presente em custo/reversão de manutenção. |
| `sale` | `Sale` | Condicional | Presente em venda/reversão de venda. |
| `reversalOf` | `FinancialTransaction` | Não | Liga um estorno ao lançamento original. |
| `description` | `String` | Condicional | Obrigatória em saldo inicial ou ajuste manual. |
| `createdAt` | `Instant` | Sim | Imutável. |
| `createdBy` | `User` | Sim | Autor do evento que gerou o lançamento. |

Lançamentos automáticos propostos:

- compra do aparelho: saída;
- manutenção confirmada: saída;
- venda confirmada: entrada;
- cancelamento: lançamento inverso, sem apagar o original.

Saldo inicial, aportes, retiradas e ajustes manuais auditados integram o MVP. A tela correspondente deve ser incorporada ao mapa de telas em uma revisão documental posterior.

### 4.11 AuditLog

Registro imutável de ação relevante do negócio.

| Atributo | Tipo | Obrigatório | Regra |
| --- | --- | --- | --- |
| `id` | `UUID` | Sim | Identificador do evento. |
| `occurredAt` | `Instant` | Sim | Instante da ação. |
| `actorUser` | `User` | Não | Nulo somente para ação automática claramente identificada. |
| `action` | `AuditAction` | Sim | Tipo padronizado da ação. |
| `entityType` | `AuditedEntityType` | Sim | Tipo lógico da entidade afetada. |
| `entityId` | `UUID` | Sim | ID da entidade afetada. |
| `entityReference` | `String` | Não | Código legível, por exemplo o `internalCode` do aparelho. |
| `summary` | `String` | Sim | Descrição curta para a timeline. |
| `changes` | `Map<String, Object>` | Não | Alterações estruturadas, sem senha ou segredo. |
| `requestId` | `UUID` | Não | Correlação técnica entre eventos da mesma requisição. |

`AuditLog` nunca é editado ou excluído pelo fluxo normal. A referência genérica à entidade não possui cascata de exclusão.

## 5. Relacionamentos e cardinalidades

| Origem | Relação | Destino | Justificativa |
| --- | --- | --- | --- |
| `IphoneModel` | 1:N | `Device` | Vários aparelhos físicos compartilham o mesmo modelo. |
| `DeviceColor` | 1:N | `Device` | A cor é padronizada e reutilizável. |
| `Device` | 1:N | `DevicePhoto` | Um aparelho possui de 2 a 4 fotos ativas e pode manter fotos removidas no histórico. |
| `Device` | 1:N | `Maintenance` | Um aparelho pode não ter manutenção ou ter várias. |
| `Maintenance` | 1:N | `MaintenanceItem` | Uma manutenção confirmada agrega uma ou mais peças/serviços. |
| `PartCatalog` | 1:N | `MaintenanceItem` | A peça é padronizada para filtros e relatórios. |
| `Device` | 1:N histórico | `Sale` | No máximo uma venda ativa; canceladas permanecem para auditoria. |
| `User` | 1:N | entidades mutáveis | Identifica criação, edição e responsabilidade. |
| `User` | 1:N | `AuditLog` | Identifica o autor, sem permitir apagar o histórico. |
| entidades financeiras | 1:N | `FinancialTransaction` | Um evento pode gerar lançamento original e estorno. |

Não há cascata conceitual de exclusão de `Device` para manutenção, venda, financeiro ou auditoria. A única composição com remoção conjunta durante uma operação ainda não confirmada é `Maintenance` → `MaintenanceItem`.

## 6. Enums de domínio

### 6.1 UserRole

- `SOCIO`

### 6.2 DeviceStatus

- `PENDENTE_MANUTENCAO`
- `DISPONIVEL_VENDA`
- `VENDIDO`

### 6.3 MaintenanceStatus

- `ACTIVE`
- `CANCELLED`

### 6.4 SaleStatus

- `ACTIVE`
- `CANCELLED`

### 6.5 FinancialDirection

- `INFLOW`
- `OUTFLOW`

### 6.6 FinancialTransactionType

- `OPENING_BALANCE`
- `DEVICE_PURCHASE`
- `DEVICE_PURCHASE_REVERSAL`
- `MAINTENANCE`
- `MAINTENANCE_REVERSAL`
- `SALE`
- `SALE_REVERSAL`
- `OWNER_CONTRIBUTION`
- `OWNER_WITHDRAWAL`
- `MANUAL_ADJUSTMENT`

Os três últimos tipos integram o MVP conforme a aprovação das decisões A-09 e A-10.

### 6.7 AuditAction

Conjunto inicial:

- `CREATED`
- `UPDATED`
- `ARCHIVED`
- `ACTIVATED`
- `DEACTIVATED`
- `STATUS_CHANGED`
- `PHOTO_ADDED`
- `PHOTO_REMOVED`
- `MAINTENANCE_REGISTERED`
- `MAINTENANCE_CANCELLED`
- `SALE_REGISTERED`
- `SALE_CANCELLED`
- `FINANCIAL_TRANSACTION_CREATED`

Tentativas de autenticação pertencem ao log de segurança da aplicação, sem senha ou segredo, e não à timeline comum do negócio.

### 6.8 AuditedEntityType

- `USER`
- `IPHONE_MODEL`
- `DEVICE_COLOR`
- `DEVICE`
- `DEVICE_PHOTO`
- `PART_CATALOG`
- `MAINTENANCE`
- `SALE`
- `FINANCIAL_TRANSACTION`

## 7. Invariantes e regras de integridade

### 7.1 Cadastro de aparelho

1. Todos os campos obrigatórios documentados devem estar presentes.
2. `batteryHealthPercent` deve estar entre 0 e 100.
3. `storageGb` deve ser 64, 128, 256, 512, 1024 ou 2048 GB.
4. `purchasePrice` deve ser estritamente maior que zero.
5. O modelo e a cor devem estar ativos no momento do cadastro.
6. Devem existir de 2 a 4 fotos ativas ao concluir o cadastro.
7. O status inicial pode ser `PENDENTE_MANUTENCAO` ou `DISPONIVEL_VENDA`, nunca `VENDIDO`.
8. O código interno é gerado uma vez e não muda.

### 7.2 Transições de status — aprovada

Transições permitidas:

- cadastro → `PENDENTE_MANUTENCAO`;
- cadastro → `DISPONIVEL_VENDA`;
- `PENDENTE_MANUTENCAO` → `DISPONIVEL_VENDA`;
- `DISPONIVEL_VENDA` → `PENDENTE_MANUTENCAO`, quando um defeito for descoberto antes da venda;
- `DISPONIVEL_VENDA` → `VENDIDO`, exclusivamente ao confirmar uma venda;
- `VENDIDO` → `DISPONIVEL_VENDA`, exclusivamente ao cancelar a venda ativa.

Não é permitido marcar manualmente um aparelho como `VENDIDO`.

### 7.3 Manutenção

1. Uma manutenção confirmada deve possuir ao menos um item.
2. Cada item deve possuir peça e custo não negativo.
3. O item `OTHER` exige descrição.
4. Não se adiciona, altera ou cancela manutenção enquanto o aparelho possui venda ativa.
5. O total de manutenção é calculado pela soma dos itens de manutenções ativas.
6. Cancelamento exige motivo e produz auditoria; se houver ledger, também produz estorno.

### 7.4 Venda

1. Somente aparelho `DISPONIVEL_VENDA` pode ser vendido.
2. O aparelho não pode possuir outra venda ativa.
3. O valor de venda deve ser maior que zero.
4. Confirmação da venda, mudança para `VENDIDO`, lançamento financeiro e auditoria devem ocorrer atomicamente no backend.
5. Lucro: `salePrice - investmentTotal`.
6. Margem: `(profit / salePrice) * 100`.
7. Venda não é apagada.
8. Correção aprovada: cancelar com motivo e registrar nova venda; não editar silenciosamente uma venda ativa.
9. Enquanto houver venda ativa, não se altera preço de compra nem qualquer outro dado que mude o investimento histórico; uma correção exige cancelar a venda primeiro.

### 7.5 Financeiro

1. Valores monetários usam `BigDecimal` e escala de duas casas para moeda.
2. Custo de manutenção e investimento são derivados, não digitados.
3. Lançamento financeiro nunca é apagado ou alterado; correções geram estorno.
4. Saldo no período: saldo anterior + entradas do período - saídas do período.
5. Faturamento considera somente vendas ativas dentro do período.
6. Lucro considera somente vendas ativas dentro do período da venda.
7. Capital em estoque soma compra e manutenções ativas dos aparelhos não vendidos e não arquivados. Arquivar um aparelho com investimento registrado exige o tratamento financeiro correspondente.
8. Os filtros usam a data econômica (`purchasedAt`, `performedAt`, `soldAt`), não `createdAt`.
9. Alteração de um valor que já originou lançamento gera estorno e novo lançamento na mesma transação; o lançamento original permanece intacto.

### 7.6 Concorrência

As entidades mutáveis usam versão otimista. Se os dois sócios editarem o mesmo registro a partir de versões diferentes, a segunda gravação falha de forma controlada em vez de sobrescrever silenciosamente a primeira.

## 8. Identificadores

Proposta: `UUID` para chaves técnicas e um código sequencial amigável somente para `Device`.

Justificativa:

- o UUID não expõe volume de registros na API;
- é estável para futura sincronização com aplicativo móvel;
- evita depender do banco para conhecer o ID antes de persistir;
- o baixo volume do MVP torna irrelevante a diferença prática de índice para `bigint`;
- o usuário não precisa digitar UUID porque utiliza `internalCode` na interface.

Não será adicionada biblioteca exclusiva para UUID ordenável na primeira versão. A geração exata será fechada na Etapa B/C.

## 9. Datas e fuso horário

Decisão aprovada:

- momentos de negócio e auditoria são representados como `Instant` no Java;
- PostgreSQL usará timestamp com fuso, definido na Etapa B;
- API recebe e devolve ISO 8601;
- backend persiste o instante em UTC;
- filtros de dia, semana, mês e ano são convertidos conforme um fuso de negócio configurável;
- configuração inicial sugerida: `America/Bahia`;
- a interface exibe datas no fuso do negócio.

`createdAt` informa quando o registro entrou no sistema. `purchasedAt`, `performedAt` e `soldAt` informam quando o evento econômico realmente ocorreu.

## 10. Auditoria

Será usada uma estratégia dupla:

1. campos `createdAt`, `createdBy`, `updatedAt`, `updatedBy` nas entidades mutáveis;
2. `AuditLog` explícito para ações relevantes e para a timeline.

Auditoria automática do JPA, sozinha, não informa adequadamente ação, entidade, resumo e alterações. Logs de aplicação também não substituem auditoria do negócio.

Regras:

- nenhuma senha, hash, token, segredo ou conteúdo binário entra em `changes`;
- eventos de auditoria são gravados na mesma transação da mutação;
- falha ao auditar uma mutação relevante faz a transação falhar;
- usuário referenciado é desativado, nunca apagado enquanto possuir histórico;
- a aplicação não expõe endpoint comum para alterar ou excluir auditoria.

## 11. Estratégia de exclusão

| Entidade | Estratégia aprovada |
| --- | --- |
| `User` | Desativação por `active=false`. |
| Catálogos | Desativação por `active=false`. |
| `Device` | Arquivamento lógico; nunca remover se houver manutenção, venda ou financeiro. |
| `DevicePhoto` | Remoção lógica e política posterior de limpeza do objeto no storage. |
| `Maintenance` | Cancelamento com motivo; não apagar após confirmação. |
| `Sale` | Cancelamento com motivo; não apagar. |
| `FinancialTransaction` | Imutável; corrigir com estorno. |
| `AuditLog` | Imutável e não excluível no fluxo normal. |

Não será aplicado um `deleted` genérico a todas as tabelas. Cada entidade possui uma semântica adequada: desativar, arquivar ou cancelar.

## 12. Decisões aprovadas

| ID | Tema | Recomendação | Alternativa e impacto |
| --- | --- | --- | --- |
| A-01 | Login | `name` + `username` único | Usar somente `name` exige nome único e mistura exibição com autenticação. |
| A-02 | Modelos | Catálogo `IphoneModel` | Enum exige nova versão do backend a cada modelo. |
| A-03 | Cores | Catálogo global `DeviceColor` | Texto validado é simples, mas favorece duplicidades; enum é rígido. |
| A-04 | Peças | Catálogo `PartCatalog` com `OTHER` | Enum é menor, porém exige deploy para incluir nova peça. |
| A-05 | Data de compra | `purchasedAt` obrigatório | Usar `createdAt` distorce relatórios quando o cadastro é atrasado. |
| A-06 | Status | Permitir `DISPONIVEL_VENDA` → `PENDENTE_MANUTENCAO`; vender somente disponível | Fluxo apenas para frente não cobre defeito descoberto antes da venda. |
| A-07 | Correção de venda | Cancelar com motivo + nova venda; no máximo uma ativa | Venda totalmente imutável exige correção administrativa fora do sistema. |
| A-08 | Manutenção | Cancelar com motivo, sem excluir | Exclusão física prejudica histórico e financeiro. |
| A-09 | Saldo | Ledger `FinancialTransaction` simples e imutável | Sem ledger, mostrar apenas “saldo operacional calculado” desde zero; não representa necessariamente o caixa real. |
| A-10 | Caixa manual | Permitir saldo inicial, aporte, retirada e ajuste com descrição/auditoria | Sem esses lançamentos, mesmo com ledger o saldo pode divergir do dinheiro real. |
| A-11 | Campos derivados | Não persistir total de manutenção, investimento, lucro e margem | Persistir snapshots simplifica consultas, mas cria risco de divergência e exige regras extras de sincronização. |
| A-12 | IDs | UUID técnico + código amigável do aparelho | `bigint` é mais simples, porém expõe sequência e é menos conveniente para clientes offline futuros. |
| A-13 | Fuso | UTC no armazenamento + fuso configurável, inicialmente `America/Bahia` | Usar somente horário local cria ambiguidades e dificulta integrações. |
| A-14 | Exclusão | Sem hard delete para registros financeiros; usar desativação/arquivamento/cancelamento | Exclusão física simplifica tabelas, mas contraria histórico e auditoria. |

## 13. Registro da aprovação

As decisões A-01 a A-14 foram aprovadas integralmente.

Ajuste solicitado na aprovação:

- `purchasePrice` deve ser estritamente maior que zero.

Confirmações adicionais:

- o saldo representa o caixa real controlado pelo sistema;
- saldo inicial, aportes, retiradas e ajustes manuais auditados fazem parte do MVP;
- a Etapa B pode transformar este domínio em modelo lógico PostgreSQL.
