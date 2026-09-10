# Etapa D — Casos de Uso

Versão: **1.0 aprovada**  
Status: **Etapa D encerrada**  
Dependências: **Etapas A, B e C 1.0 aprovadas e encerradas**

## 1. Objetivo e limites

Este documento transforma o domínio aprovado em operações explícitas da aplicação. Ele define atores, entradas, pré-condições, validações, saídas, transações, efeitos no ledger e auditoria.

Permanecem fora desta etapa: controllers, services de produção, Spring Data repositories, implementação de autenticação, storage real, frontend, seeds e deploy.

## 2. Regras transversais

- Todos os casos, exceto `AuthenticateUser`, exigem usuário autenticado e ativo com papel `SOCIO`.
- O primeiro usuário não é criado por endpoint público. O bootstrap seguro será definido na etapa de segurança, sem segredo em migration ou Git.
- IDs técnicos são UUIDs. O aparelho também possui `internalCode` gerado pelo sistema no formato `IPH-000001`.
- Instantes entram e saem em ISO 8601 com offset; são persistidos em UTC. Períodos civis usam `America/Bahia`.
- Valores monetários são `BigDecimal`/`numeric(14,2)`, em BRL, e nunca são calculados pelo cliente como fonte de verdade.
- DTOs não expõem entidades JPA. Senha, hash, chave de storage e dados de segurança nunca aparecem em respostas ou auditoria.
- Toda mutação de entidade versionada recebe `expectedVersion`. Divergência retorna conflito e não sobrescreve dados.
- Toda operação relevante usa um `requestId` único, recebido em `X-Request-Id` ou gerado pelo backend, e o propaga para correlação, rastreabilidade e auditoria.
- `X-Request-Id` não é chave de idempotência e não deve ser interpretado automaticamente dessa forma. Uma eventual idempotência de comandos críticos exigirá mecanismo próprio, como `Idempotency-Key`, e decisão arquitetural futura específica.
- O ledger e o `AuditLog` são append-only. Correção financeira cria um novo lançamento; nenhum lançamento é editado ou apagado.
- Uma operação de negócio gera preferencialmente um evento principal de auditoria. IDs dos efeitos secundários ficam em `changes` para evitar ruído.
- A fronteira transacional inclui todas as alterações PostgreSQL do caso de uso. Efeitos no object storage usam compensação, pois não participam da transação SQL.

## 3. Catálogo dos casos de uso

| ID | Caso de uso | Natureza |
| --- | --- | --- |
| UC-AUTH-01 | `AuthenticateUser` | Autenticação |
| UC-AUTH-02 | `GetCurrentUser` | Consulta |
| UC-AUTH-03 | `LogoutUser` | Autenticação |
| UC-USER-01 | `CreateUser` | Mutação |
| UC-USER-02 | `GetUser` | Consulta |
| UC-USER-03 | `ListUsers` | Consulta paginada |
| UC-USER-04 | `UpdateUser` | Mutação |
| UC-USER-05 | `ActivateUser` | Mutação |
| UC-USER-06 | `DeactivateUser` | Mutação |
| UC-CAT-01 | `CreateCatalogItem` | Mutação por catálogo |
| UC-CAT-02 | `GetCatalogItem` | Consulta por catálogo |
| UC-CAT-03 | `ListCatalogItems` | Consulta paginada por catálogo |
| UC-CAT-04 | `UpdateCatalogItem` | Mutação por catálogo |
| UC-CAT-05 | `ActivateCatalogItem` | Mutação por catálogo |
| UC-CAT-06 | `DeactivateCatalogItem` | Mutação por catálogo |
| UC-DEV-01 | `RegisterDevice` | Mutação crítica |
| UC-DEV-02 | `GetDevice` | Consulta |
| UC-DEV-03 | `ListDevices` | Consulta paginada |
| UC-DEV-04 | `UpdateDevice` | Mutação crítica quando altera compra |
| UC-DEV-05 | `MarkDevicePendingMaintenance` | Transição de estado |
| UC-DEV-06 | `MarkDeviceAvailable` | Transição de estado |
| UC-DEV-07 | `ArchiveDevice` | Mutação crítica e terminal |
| UC-PHOTO-01 | `AddDevicePhoto` | Mutação com storage |
| UC-PHOTO-02 | `ListDevicePhotos` | Consulta |
| UC-PHOTO-03 | `RemoveDevicePhoto` | Remoção lógica |
| UC-MAINT-01 | `RegisterMaintenance` | Mutação crítica |
| UC-MAINT-02 | `GetMaintenance` | Consulta |
| UC-MAINT-03 | `ListDeviceMaintenances` | Consulta paginada |
| UC-MAINT-04 | `CancelMaintenance` | Mutação crítica |
| UC-SALE-01 | `RegisterSale` | Mutação crítica |
| UC-SALE-02 | `GetDeviceSale` | Consulta |
| UC-SALE-03 | `CancelSale` | Mutação crítica |
| UC-FIN-01 | `GetFinancialSummary` | Consulta calculada |
| UC-FIN-02 | `ListFinancialTransactions` | Consulta paginada |
| UC-FIN-03 | `RegisterOpeningBalance` | Mutação financeira |
| UC-FIN-04 | `RegisterOwnerContribution` | Mutação financeira |
| UC-FIN-05 | `RegisterOwnerWithdrawal` | Mutação financeira |
| UC-FIN-06 | `RegisterManualAdjustment` | Mutação/correção financeira |
| UC-AUD-01 | `ListAuditEvents` | Consulta paginada |
| UC-AUD-02 | `GetAuditEvent` | Consulta |

## 4. Autenticação

### UC-AUTH-01 — Autenticar usuário

**Ator:** visitante não autenticado.

**Entrada:** `username` e `password`.

**Pré-condições:** nenhuma.

**Fluxo principal:**

1. Normalizar o username com `trim` e minúsculas.
2. Localizar o usuário sem revelar se o username existe.
3. Verificar se está ativo e validar a senha contra o hash seguro.
4. Emitir credencial Bearer opaca ao cliente, com expiração.
5. Retornar a credencial e o usuário autenticado.

**Saída:** contexto autenticado contendo token, expiração e `UserResponse`.

**Erros:** `AUTHENTICATION_FAILED` para credenciais inválidas ou usuário inativo; `VALIDATION_ERROR` para payload inválido.

**Transação, ledger e auditoria:** não altera o domínio, não gera ledger nem `AuditLog`. Tentativas entram apenas no log de segurança, sem senha.

### UC-AUTH-02 — Consultar usuário atual

Valida a credencial vigente e retorna o usuário atual. Credencial ausente, expirada ou revogada retorna `UNAUTHORIZED`; usuário desativado invalida o acesso.

### UC-AUTH-03 — Encerrar sessão

Invalida a credencial atual quando o mecanismo adotado permitir e sempre retorna resultado idempotente. Não gera ledger nem evento de negócio.

## 5. Usuários

### UC-USER-01 — Criar usuário

**Ator:** `SOCIO` autenticado.

**Entrada:** nome, username e senha inicial. O papel é sempre `SOCIO` no MVP.

**Validações:**

- nome sem espaços externos, de 1 a 120 caracteres;
- username normalizado, de 3 a 50 caracteres, no padrão `[a-z0-9._-]`;
- username único;
- senha de 12 a 128 caracteres antes do hash;
- senha nunca é persistida ou auditada em texto puro.

**Fluxo:** validar, gerar hash, criar usuário ativo e registrar auditoria `CREATED/USER`.

**Saída:** usuário criado, sem senha ou hash.

**Erros:** `USERNAME_ALREADY_EXISTS`, `VALIDATION_ERROR`.

**Transação:** usuário e auditoria confirmam juntos. **Ledger:** nenhum.

### UC-USER-02 e UC-USER-03 — Consultar usuários

`GetUser` retorna um usuário por UUID. `ListUsers` aceita `search`, `active`, paginação e ordenação permitida. Username, nome, estado, papel, datas e versão são visíveis; credenciais não são.

**Erros:** `USER_NOT_FOUND`, parâmetros inválidos.

### UC-USER-04 — Atualizar usuário

**Entrada:** UUID, `expectedVersion` e ao menos um de `name`, `username` ou `newPassword`.

**Regras:**

- nome e username seguem as regras de criação;
- username continua único sem diferenciar caixa, pois é armazenado normalizado;
- mudança de senha somente é permitida para o próprio usuário autenticado no MVP;
- senha/hash não entra em `changes`; a auditoria registra apenas `passwordChanged: true`;
- papel e estado ativo não são alterados por este caso.

**Saída:** usuário atualizado. **Erros:** `USER_NOT_FOUND`, `USERNAME_ALREADY_EXISTS`, `PASSWORD_CHANGE_NOT_ALLOWED`, `CONCURRENT_MODIFICATION`.

**Transação:** atualização e auditoria `UPDATED/USER` confirmam juntas.

### UC-USER-05 — Ativar usuário

Exige UUID e `expectedVersion`. Altera apenas `active=false` para `true`; se já ativo, retorna o estado atual sem criar auditoria duplicada. Registra `ACTIVATED/USER` quando houver mudança.

### UC-USER-06 — Desativar usuário

Exige UUID e `expectedVersion`. Não permite desativar o usuário da própria credencial nem o último usuário ativo. Usuário já inativo produz sucesso idempotente sem novo evento.

**Erros:** `USER_NOT_FOUND`, `CANNOT_DEACTIVATE_CURRENT_USER`, `LAST_ACTIVE_USER_REQUIRED`, `CONCURRENT_MODIFICATION`.

**Transação:** bloqueia logicamente a verificação do último usuário; alteração e auditoria `DEACTIVATED/USER` confirmam juntas.

## 6. Catálogos

Os casos abaixo se aplicam a `IphoneModel`, `DeviceColor` e `PartCatalog`.

### UC-CAT-01 — Criar item de catálogo

**Entrada comum:** `code`, `name`. Modelo também recebe `displayOrder`.

**Validações:** código em maiúsculas no padrão `[A-Z0-9_]+`, dentro do limite da tabela; nome sem espaços externos e único sem diferenciar caixa; `displayOrder >= 0`. O item nasce ativo.

**Regras:** códigos são identificadores estáveis e não podem ser alterados. `OTHER` é o código reservado para peça genérica e exige `details` no item de manutenção.

**Saída:** item criado. **Erros:** `CATALOG_CODE_ALREADY_EXISTS`, `CATALOG_NAME_ALREADY_EXISTS`, `VALIDATION_ERROR`.

**Transação:** item e auditoria `CREATED` com o tipo lógico correspondente confirmam juntos.

### UC-CAT-02 e UC-CAT-03 — Consultar catálogo

Consulta individual por UUID e listagem por `search`, `active`, paginação e ordenação. Seletores de cadastro chamam a lista com `active=true`.

**Erros específicos:** `MODEL_NOT_FOUND`, `COLOR_NOT_FOUND` ou `PART_NOT_FOUND`.

### UC-CAT-04 — Atualizar item de catálogo

Recebe `expectedVersion` e campos mutáveis. Modelo aceita `name` e `displayOrder`; cor e peça aceitam `name`. Código e estado ativo não fazem parte do PATCH.

**Erros:** item inexistente, nome duplicado, validação ou `CONCURRENT_MODIFICATION`.

**Transação:** atualização e auditoria `UPDATED` confirmam juntas.

### UC-CAT-05 e UC-CAT-06 — Ativar/desativar item

Recebem `expectedVersion`. São idempotentes e registram `ACTIVATED` ou `DEACTIVATED` somente quando há transição. Desativar preserva referências históricas e apenas impede novos usos.

## 7. Aparelhos

### UC-DEV-01 — Cadastrar aparelho

**Ator:** `SOCIO` autenticado.

**Entrada:** modelo, cor, armazenamento, preço e data de compra, condições do aparelho, status inicial e de duas a quatro fotos.

**Pré-condições:** modelo e cor existem e estão ativos.

**Validações:**

- `storageGb` pertence a `64, 128, 256, 512, 1024, 2048`;
- `purchasePrice > 0`, com no máximo duas casas decimais;
- `purchasedAt` é um instante válido;
- `batteryHealthPercent` fica entre 0 e 100; zero significa não aferido/indisponível;
- status inicial é `PENDENTE_MANUTENCAO` ou `DISPONIVEL_VENDA`;
- há de 2 a 4 arquivos válidos e posições únicas de 1 a 4;
- tipo real e tamanho de cada imagem respeitam a política do contrato.

**Fluxo principal:**

1. Validar payload, catálogo e arquivos antes de qualquer escrita.
2. Enviar as imagens ao storage com chaves temporárias/únicas.
3. Abrir transação PostgreSQL.
4. Criar `Device`; nas escritas via Hibernate/JPA, `GenerationType.UUID` gera o UUID antes do `INSERT`. O PostgreSQL mantém `gen_random_uuid()` como default para SQL direto, cargas e integrações externas. O `internalCode` continua sendo gerado pelo PostgreSQL.
5. Criar de 2 a 4 metadados `DevicePhoto` ativos.
6. Criar `FinancialTransaction` `DEVICE_PURCHASE/OUTFLOW`, com valor e data iguais à compra.
7. Criar auditoria principal `CREATED/DEVICE`, incluindo IDs das fotos e do lançamento em `changes`.
8. Confirmar a transação.
9. Se a transação falhar, remover os objetos enviados como compensação. Falha de compensação é registrada para limpeza operacional.

**Saída:** `DeviceDetailResponse` criado.

**Erros:** catálogo inexistente/inativo, `PHOTO_MINIMUM_VIOLATION`, `PHOTO_LIMIT_EXCEEDED`, `UNSUPPORTED_IMAGE_TYPE`, `FILE_TOO_LARGE`, validação e conflito inesperado de storage.

**Transação:** obrigatória. Nenhum aparelho pode ficar persistido com menos de duas fotos ou sem a saída de compra correspondente.

**Ledger:** `DEVICE_PURCHASE/OUTFLOW`. **Auditoria:** `CREATED/DEVICE`.

### UC-DEV-02 — Consultar aparelho

Retorna características, fotos ativas, totais calculados, status, estado de arquivamento e versão. `batteryHealthPercent` sai como `null` quando o valor persistido é zero. Aparelho inexistente retorna `DEVICE_NOT_FOUND`.

### UC-DEV-03 — Listar aparelhos

Aceita `search`, `status`, `modelId`, `colorId`, `storageGb`, `purchasedFrom`, `purchasedTo`, `archived`, paginação e ordenação. `search` prioriza correspondência pelo `internalCode` e também pode procurar nomes de modelo/cor. Por padrão, `archived=false`.

Os totais de manutenção e investimento são calculados no backend, sem colunas duplicadas.

### UC-DEV-04 — Atualizar aparelho

**Entrada:** UUID, `expectedVersion` e ao menos um campo mutável: modelo, cor, armazenamento, preço/data de compra ou condições físicas.

**Regras:**

- `internalCode`, status, arquivamento e metadados de auditoria não são editáveis;
- modelo/cor novos devem estar ativos;
- aparelho arquivado é imutável;
- com venda ativa, preço e data de compra não podem mudar;
- status muda somente por operações semânticas;
- se preço ou data de compra mudar, o lançamento original é estornado e um novo `DEVICE_PURCHASE` é criado na mesma transação;
- o estorno de correção usa a data econômica do lançamento original; o novo lançamento usa o novo `purchasedAt`.

**Saída:** aparelho atualizado e totais recalculados.

**Erros:** `DEVICE_NOT_FOUND`, `CATALOG_ITEM_INACTIVE`, `DEVICE_ARCHIVED`, `DEVICE_PURCHASE_LOCKED_BY_SALE`, `CONCURRENT_MODIFICATION`.

**Transação:** atualização, eventual estorno/novo lançamento e auditoria `UPDATED/DEVICE` confirmam juntos.

### UC-DEV-05 — Marcar como pendente de manutenção

Transição exclusiva `DISPONIVEL_VENDA -> PENDENTE_MANUTENCAO`. Recebe `expectedVersion`. Não aceita aparelho vendido ou arquivado e não altera o ledger. Registra `STATUS_CHANGED/DEVICE`.

### UC-DEV-06 — Marcar como disponível

Transição exclusiva `PENDENTE_MANUTENCAO -> DISPONIVEL_VENDA`. Recebe `expectedVersion`. Não exige manutenção registrada, pois um diagnóstico pode concluir que nenhum reparo é necessário. Não altera o ledger e registra `STATUS_CHANGED/DEVICE`.

### UC-DEV-07 — Arquivar aparelho

**Entrada:** UUID, `expectedVersion` e motivo.

**Pré-condições:** aparelho existe, não está arquivado e não possui venda ativa. Venda deve ser cancelada antes deste caso.

**Fluxo:**

1. Validar versão e bloquear o aparelho durante a operação.
2. Cancelar cada manutenção ativa com o motivo informado e criar seu estorno quando o total for positivo.
3. Estornar a compra do aparelho.
4. Preencher `archivedAt` e `archivedBy`.
5. Registrar um evento principal `ARCHIVED/DEVICE` com os IDs dos efeitos secundários.
6. Confirmar tudo junto.

**Regras:** arquivamento é terminal no MVP; não representa venda, perda ou descarte; fotos permanecem no histórico. Não existe `UnarchiveDevice` no MVP, o aparelho arquivado permanece imutável e uma eventual reativação exigirá decisão arquitetural futura específica.

**Erros:** `DEVICE_NOT_FOUND`, `DEVICE_ALREADY_ARCHIVED`, `DEVICE_HAS_ACTIVE_SALE`, `CONCURRENT_MODIFICATION`.

## 8. Fotos

Operações de foto serializam a coleção pelo aparelho para preservar o limite entre duas e quatro fotos ativas sob concorrência.

### UC-PHOTO-01 — Adicionar foto

Valida aparelho ativo, arquivo, posição livre e limite máximo. Envia o objeto, cria o metadado em transação e audita `PHOTO_ADDED/DEVICE_PHOTO`; em falha SQL, compensa o upload. Retorna a foto criada.

**Erros:** `DEVICE_NOT_FOUND`, `DEVICE_ARCHIVED`, `PHOTO_LIMIT_EXCEEDED`, `PHOTO_POSITION_OCCUPIED`, `UNSUPPORTED_IMAGE_TYPE`, `FILE_TOO_LARGE`.

### UC-PHOTO-02 — Listar fotos ativas

Retorna apenas fotos com `removedAt=null`, ordenadas por posição. A URL de leitura é temporária; `storageKey` nunca é exposta.

### UC-PHOTO-03 — Remover foto

Valida que a foto pertence ao aparelho e que a remoção deixará pelo menos duas fotos ativas. Preenche `removedAt`, sem hard delete e sem reordenar as demais. O objeto pode permanecer no storage conforme a política de retenção. Registra `PHOTO_REMOVED/DEVICE_PHOTO`.

**Erros:** `DEVICE_NOT_FOUND`, `PHOTO_NOT_FOUND`, `PHOTO_MINIMUM_VIOLATION`.

## 9. Manutenção

### UC-MAINT-01 — Registrar manutenção

**Entrada:** `deviceId`, `performedAt` e um ou mais itens com `partId`, `details` e `cost`.

**Pré-condições:** aparelho existe, não está arquivado nem vendido; peças existem e estão ativas; `performedAt >= purchasedAt`.

**Validações:** ao menos um item; custos maiores ou iguais a zero; no máximo duas casas decimais; posição derivada da ordem do array; peça `OTHER` exige detalhes de 1 a 255 caracteres.

**Fluxo:**

1. Bloquear o aparelho para impedir venda concorrente.
2. Criar manutenção ativa e seus itens como uma composição.
3. Somar os custos no backend.
4. Se o total for positivo, criar `MAINTENANCE/OUTFLOW` com `occurredAt=performedAt`; se zero, não criar lançamento.
5. Registrar `MAINTENANCE_REGISTERED/MAINTENANCE` com itens, total e ID do lançamento quando existir.
6. Confirmar tudo junto.

**Saída:** manutenção completa e total calculado.

**Erros:** `DEVICE_NOT_FOUND`, `DEVICE_ARCHIVED`, `DEVICE_ALREADY_SOLD`, `PART_NOT_FOUND`, `CATALOG_ITEM_INACTIVE`, `MAINTENANCE_ITEM_REQUIRED`, `MAINTENANCE_DATE_BEFORE_PURCHASE`.

### UC-MAINT-02 e UC-MAINT-03 — Consultar manutenções

Consulta individual garante que a manutenção pertence ao aparelho. A lista é paginada, ordenada por `performedAt desc`, e aceita `status`, `from` e `to`. Itens e total calculado são retornados; lançamentos financeiros internos não são embutidos.

### UC-MAINT-04 — Cancelar manutenção

**Entrada:** IDs, `expectedVersion` e motivo de 1 a 500 caracteres.

**Pré-condições:** manutenção ativa, pertencente ao aparelho; aparelho não possui venda ativa.

**Fluxo:** bloquear aparelho e manutenção, marcar `CANCELLED`, preencher autoria/data/motivo, criar `MAINTENANCE_REVERSAL/INFLOW` quando houver saída original, auditar e confirmar.

Cancelamento não apaga cabeçalho nem itens. Manutenção total zero não gera estorno. O estorno usa o instante do cancelamento.

**Erros:** `MAINTENANCE_NOT_FOUND`, `MAINTENANCE_ALREADY_CANCELLED`, `DEVICE_ALREADY_SOLD`, `CONCURRENT_MODIFICATION`.

## 10. Venda

### UC-SALE-01 — Registrar venda

**Entrada:** `deviceId`, `deviceVersion`, `salePrice` e `soldAt`.

**Pré-condições:** aparelho existe, não está arquivado, está `DISPONIVEL_VENDA`, não possui venda ativa, `salePrice > 0` e `soldAt >= purchasedAt`.

**Fluxo principal:**

1. Carregar e bloquear o aparelho; conferir a versão recebida.
2. Validar disponibilidade e inexistência de venda ativa.
3. Somar itens de manutenções ativas.
4. Calcular `investmentTotal`, `profit` e `marginPercent`.
5. Criar a venda ativa com o usuário autenticado como responsável.
6. Alterar o aparelho para `VENDIDO`.
7. Criar `SALE/INFLOW` pelo preço e data da venda.
8. Criar `SALE_REGISTERED/SALE` com os valores calculados e IDs correlatos.
9. Confirmar a transação.

**Fórmulas:**

```text
maintenanceTotal = soma dos itens de manutenções ACTIVE
investmentTotal  = purchasePrice + maintenanceTotal
profit           = salePrice - investmentTotal
marginPercent    = profit / salePrice * 100
```

Margem usa quatro casas decimais na resposta. Lucro negativo é permitido e informado; não existe regra aprovada que proíba venda com prejuízo.

**Erros:** `DEVICE_NOT_FOUND`, `DEVICE_ARCHIVED`, `DEVICE_NOT_AVAILABLE_FOR_SALE`, `SALE_ALREADY_EXISTS`, `SALE_DATE_BEFORE_PURCHASE`, `CONCURRENT_MODIFICATION`.

**Transação:** obrigatória; falha em qualquer passo produz rollback integral.

### UC-SALE-02 — Consultar venda ativa do aparelho

Retorna a venda ativa e os cálculos oficiais. Se não houver venda ativa, retorna `SALE_NOT_FOUND`. Vendas canceladas continuam preservadas na auditoria e no banco, mas a rota singular representa somente a venda vigente.

### UC-SALE-03 — Cancelar venda

**Entrada:** `deviceId`, `saleVersion`, `deviceVersion` e motivo.

**Fluxo:** bloquear aparelho e venda; validar versões; marcar venda `CANCELLED`; preencher autoria/data/motivo; criar `SALE_REVERSAL/OUTFLOW` referindo o lançamento original; mudar aparelho para `DISPONIVEL_VENDA`; criar `SALE_CANCELLED/SALE`; confirmar.

O estorno usa o instante do cancelamento. A venda original e sua entrada permanecem. Uma nova venda só pode ser registrada após o commit do cancelamento.

**Erros:** `SALE_NOT_FOUND`, `SALE_ALREADY_CANCELLED`, `INVALID_DEVICE_STATUS_TRANSITION`, `CONCURRENT_MODIFICATION`.

## 11. Financeiro

### UC-FIN-01 — Consultar resumo financeiro

**Entrada:** intervalo opcional `[from,to)`. Sem parâmetros, usa o mês civil corrente em `America/Bahia` convertido para UTC.

**Saída e semântica:**

| Campo | Definição |
| --- | --- |
| `period.from` / `period.to` | Limites efetivos, início inclusivo e fim exclusivo. |
| `openingBalance` | Soma de entradas menos saídas com `occurredAt < from`. |
| `closingBalance` | Saldo inicial mais o movimento dentro do período. |
| `revenue` | Soma das vendas atualmente ativas cujo `soldAt` está no período. |
| `devicePurchaseCost` | Soma de compras não estornadas com `purchasedAt` no período. |
| `maintenanceCost` | Soma de manutenções ativas com `performedAt` no período. |
| `profit` | Soma, por venda ativa no período, de `salePrice - investmentTotal`. |
| `marginPercent` | `profit / revenue * 100`; `null` quando `revenue = 0`. |
| `stockCapital` | Snapshot atual do investimento em aparelhos não vendidos e não arquivados. |
| `calculatedAt` | Instante do snapshot de estoque e da consulta. |

O caixa pode ficar negativo; o domínio aprovado não define bloqueio por saldo insuficiente.

### UC-FIN-02 — Listar lançamentos

Aceita `from`, `to`, `type`, `direction`, paginação e ordenação. Retorna eventos imutáveis, sua origem operacional quando houver e `reversalOfId`. Não fornece operações de edição ou exclusão.

### UC-FIN-03 — Registrar saldo inicial

Recebe `amount > 0`, `occurredAt` e descrição. Cria `OPENING_BALANCE/INFLOW`, sem origem operacional, e `FINANCIAL_TRANSACTION_CREATED`. Apenas um saldo inicial não estornado pode existir. A implementação deve proteger a verificação e a criação contra requisições simultâneas com lock transacional, advisory lock ou estratégia equivalente. Saldo inicial zero não cria linha e deve ser tratado pelo cliente como ausência de operação. Essa definição não exige alteração da migration V1.

### UC-FIN-04 — Registrar aporte

Recebe valor positivo, data econômica e descrição. Cria `OWNER_CONTRIBUTION/INFLOW`, sem origem operacional, e auditoria.

### UC-FIN-05 — Registrar retirada

Recebe valor positivo, data econômica e descrição. Cria `OWNER_WITHDRAWAL/OUTFLOW`, sem origem operacional, e auditoria. Não há bloqueio por saldo insuficiente aprovado no MVP.

### UC-FIN-06 — Registrar ajuste manual

Possui dois modos mutuamente exclusivos:

1. **Ajuste livre:** recebe direção, valor positivo, data e descrição; cria `MANUAL_ADJUSTMENT` sem origem.
2. **Estorno manual:** recebe `reversalOfTransactionId`, data e descrição; o backend deriva direção oposta e mesmo valor. Somente `OPENING_BALANCE`, `OWNER_CONTRIBUTION`, `OWNER_WITHDRAWAL` ou outro `MANUAL_ADJUSTMENT` original e ainda não estornado pode ser alvo.

Lançamentos operacionais só são estornados pelos casos de aparelho, manutenção ou venda. Ajuste e auditoria confirmam juntos.

**Erros financeiros:** `INVALID_FINANCIAL_OPERATION`, `FINANCIAL_TRANSACTION_NOT_FOUND`, `FINANCIAL_TRANSACTION_ALREADY_REVERSED`, `OPERATIONAL_REVERSAL_NOT_ALLOWED`, `OPENING_BALANCE_ALREADY_EXISTS`.

## 12. Auditoria

### UC-AUD-01 — Listar eventos

Consulta somente leitura por `userId`, `entityType`, `entityId`, `action`, `from`, `to` e paginação. Ordenação padrão: `occurredAt desc`. `changes` é sanitizado e nunca contém senha, hash, token, segredo ou chave interna de storage.

### UC-AUD-02 — Consultar evento

Retorna um evento imutável por UUID. Evento inexistente retorna `AUDIT_EVENT_NOT_FOUND`.

Não existem casos de criar, editar ou remover auditoria pela API. Eventos nascem dentro das transações dos casos de negócio.

## 13. Matriz transacional, ledger e auditoria

| Operação | Ledger | Auditoria principal | Fronteira atômica |
| --- | --- | --- | --- |
| Criar usuário/catálogo | — | `CREATED` | Entidade + auditoria |
| Atualizar/ativar/desativar | — | `UPDATED`/`ACTIVATED`/`DEACTIVATED` | Entidade + auditoria |
| Cadastrar aparelho | `DEVICE_PURCHASE/OUTFLOW` | `CREATED/DEVICE` | Aparelho + fotos + compra + auditoria |
| Corrigir compra | Reversão + nova compra | `UPDATED/DEVICE` | Aparelho + dois lançamentos + auditoria |
| Mudar status | — | `STATUS_CHANGED/DEVICE` | Aparelho + auditoria |
| Adicionar/remover foto | — | `PHOTO_ADDED`/`PHOTO_REMOVED` | Metadado + auditoria; storage compensável |
| Registrar manutenção paga | `MAINTENANCE/OUTFLOW` | `MAINTENANCE_REGISTERED` | Cabeçalho + itens + lançamento + auditoria |
| Registrar manutenção gratuita | — | `MAINTENANCE_REGISTERED` | Cabeçalho + itens + auditoria |
| Cancelar manutenção paga | `MAINTENANCE_REVERSAL/INFLOW` | `MAINTENANCE_CANCELLED` | Cancelamento + estorno + auditoria |
| Registrar venda | `SALE/INFLOW` | `SALE_REGISTERED` | Venda + status + lançamento + auditoria |
| Cancelar venda | `SALE_REVERSAL/OUTFLOW` | `SALE_CANCELLED` | Cancelamento + estorno + status + auditoria |
| Arquivar aparelho | Reversões necessárias | `ARCHIVED/DEVICE` | Cancelamentos + estornos + arquivo + auditoria |
| Caixa manual | Tipo manual correspondente | `FINANCIAL_TRANSACTION_CREATED` | Lançamento + auditoria |

## 14. Concorrência

- Entidades mutáveis usam `expectedVersion`; valor diferente do persistido retorna `CONCURRENT_MODIFICATION` e HTTP 409.
- Venda e cancelamento também bloqueiam a linha do aparelho durante a transação. A constraint única de venda ativa é a última barreira contra duplicidade.
- Cadastro/remoção de fotos serializa por aparelho; as constraints diferíveis validam a cardinalidade ao final.
- Manutenção serializa por aparelho para não competir com uma venda.
- Saldo inicial deve usar lock transacional, advisory lock ou estratégia equivalente para impedir que requisições simultâneas criem dois `OPENING_BALANCE` não estornados; a migration V1 permanece inalterada.
- Violações de unicidade concorrentes são traduzidas para códigos de conflito estáveis; detalhes de constraint SQL não vazam para o cliente.

## 15. Registro de encerramento

Os fluxos, entradas, erros, transações, ledger, auditoria e regras de concorrência deste artefato foram aprovados em conjunto com `contratos-api.md` e com as decisões finais D-01 a D-15. A Etapa D versão 1.0 está encerrada. Nenhuma implementação da Etapa E foi iniciada.


---

## 16. Refinamento da Etapa G — fluxos executáveis

### UC-G-01 — Iniciar preparação

O sócio informa `cutoffAt`; a aplicação adquire proteção transacional, garante o singleton e persiste `PREPARING`. Duas inicializações não podem coexistir.

### UC-G-02 — Cadastrar aparelho operacional

Valida catálogo ativo, capacidade, preço, data, características e 2–4 fotos. Aparelho, fotos, auditoria e `DEVICE_PURCHASE / OUTFLOW` são confirmados atomicamente. Falha posterior ao upload aciona compensação do storage.

### UC-G-03 — Importar aparelho existente

Exige `PREPARING`, bloqueia a inicialização, lê o `cutoffAt` persistido e aceita somente `purchasedAt <= cutoffAt` e status `PENDENTE_MANUTENCAO` ou `DISPONIVEL_VENDA`. Preserva custo e fotos, registra auditoria e não cria lançamento financeiro.

### UC-G-04 — Administrar estoque

Listagem, filtros, detalhe, edição permitida, transições manuais, arquivamento terminal e fotos usam DTOs, `expectedVersion`, locks/constraints e códigos de erro estáveis. Aparelho arquivado é imutável e não existe reativação no MVP.

### UC-G-05 — Alterar cutoff

Permitido somente em `PREPARING` e antes do primeiro `INITIAL_IMPORT`. PATCH concorrente com a primeira importação é serializado; após ela, retorna `INITIALIZATION_CUTOFF_LOCKED`.

A transição para `COMPLETED`, manutenções históricas e capital/caixa inicial não são casos de uso da Etapa G.

## 17. Refinamento da Etapa H — manutenções executáveis

### UC-H-01 — Administrar catálogo de peças

Cria, lista, consulta, renomeia, ativa e desativa peças sem hard delete. `code` é
normalizado em maiúsculas e permanece imutável; nome é único sem diferenciar caixa.
Mutações versionadas exigem `expectedVersion`.

### UC-H-02 — Registrar manutenção operacional

Bloqueia o aparelho, garante que não esteja vendido ou arquivado e aceita um ou mais
itens ordenados. O backend deriva responsável, origem `OPERATIONAL`, posições e total.
Quando existe implantação, exige `performedAt > cutoffAt`; sem implantação, exige
`performedAt >= purchasedAt`. Total positivo cria `MAINTENANCE/OUTFLOW` na mesma
transação; total zero não cria ledger.

### UC-H-03 — Importar manutenção histórica

Exige implantação `PREPARING`, aparelho `INITIAL_IMPORT` e
`purchasedAt <= performedAt <= cutoffAt`. A origem é `INITIAL_IMPORT`, o custo entra no
investimento e no preview de capital histórico, mas nunca cria saída financeira.

### UC-H-04 — Consultar histórico e detalhe

Lista por aparelho com `status`, intervalo `[from,to)`, paginação e sort allowlist.
Detalhe exige vínculo com o aparelho e continua disponível após cancelamento ou
arquivamento.

### UC-H-05 — Cancelar manutenção

Bloqueia aparelho e manutenção, exige versão e motivo, preserva todos os dados e marca
`CANCELLED`. Manutenção operacional positiva cria `MAINTENANCE_REVERSAL/INFLOW`;
operacional zero e histórica não criam reversão. Cancelamento repetido é conflito.

### UC-H-06 — Arquivar aparelho com manutenções ativas

Em uma única transação, bloqueia o aparelho, cancela manutenções ativas em ordem
determinística, cria somente as reversões necessárias, estorna a compra operacional e
arquiva. A auditoria principal do aparelho resume os cancelamentos automáticos, sem um
evento ruidoso por manutenção.

### UC-H-07 — Calcular investimento e preview

`maintenanceTotal` soma itens de manutenções `ACTIVE`, independentemente da origem;
`investmentTotal = purchasePrice + maintenanceTotal`. O preview soma apenas
manutenções `ACTIVE/INITIAL_IMPORT` em `maintenanceCapital` e deriva `stockCapital`.

A venda e a Etapa I permanecem fora deste refinamento.
