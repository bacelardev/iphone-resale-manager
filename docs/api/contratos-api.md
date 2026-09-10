# Etapa D — Contratos da API REST

Versão: **1.0 aprovada**  
Status: **Etapa D encerrada**  
Base: **`/api/v1`**

## 1. Convenções gerais

### 1.1 Representação

- JSON usa UTF-8 e propriedades em `camelCase`.
- Requests e responses JSON usam `Content-Type: application/json`.
- Uploads usam `multipart/form-data`.
- IDs são UUIDs em texto; `internalCode` é apenas referência amigável.
- Valores monetários são números JSON com até duas casas decimais e representam BRL.
- Instantes usam ISO 8601 com offset, por exemplo `2026-09-04T18:00:00Z`.
- Intervalos temporais são `[from,to)`: início inclusivo e fim exclusivo.
- Persistência e respostas canônicas usam UTC; períodos civis são calculados em `America/Bahia`.
- Propriedades desconhecidas em requests retornam `400 MALFORMED_REQUEST` para detectar erros de integração.
- Propriedades de response podem ser adicionadas de forma compatível dentro da versão; remoção ou mudança semântica exige nova versão da API.

### 1.2 Autenticação e autorização

`POST /auth/login` é público. Todos os demais endpoints exigem:

```http
Authorization: Bearer <access-token>
```

O token é opaco para o cliente, possui prefixo público `irs_` e 256 bits aleatórios, mas não contém claims ou dados legíveis. Somente seu hash SHA-256 é persistido em sessão revogável; o valor bruto nunca é salvo. O TTL padrão é de 12 horas. Não há refresh token no MVP; credencial expirada exige novo login.

`POST /auth/logout` possui uma exceção técnica no matcher para permitir semântica idempotente, mas não é semanticamente público: exige header Bearer sintaticamente válido. Credencial ausente ou malformada retorna 401; token bem formado desconhecido, expirado ou já revogado retorna 204.

Todos os usuários do MVP têm papel `SOCIO`. Não há matriz complexa de permissões. O bootstrap do primeiro usuário é operacional e não será exposto como cadastro público.

### 1.3 Correlação

O cliente pode enviar:

```http
X-Request-Id: 7f1fead9-80cb-47c9-b4eb-09e03753d81c
```

O servidor valida o UUID ou gera um novo, devolve o cabeçalho e o usa em `AuditLog.requestId`.

`X-Request-Id` existe exclusivamente para correlação, rastreabilidade e auditoria. Repetir o mesmo valor não garante deduplicação nem repetição segura de um comando. Ele não deve ser tratado automaticamente como chave de idempotência. Se comandos críticos receberem idempotência no futuro, ela usará mecanismo próprio, como `Idempotency-Key`, após decisão arquitetural específica.

### 1.4 Concorrência otimista

Requests que alteram entidade versionada incluem `expectedVersion`. Ausência é erro de validação; diferença para a versão persistida retorna:

```json
{
  "timestamp": "2026-09-04T20:30:00Z",
  "status": 409,
  "code": "CONCURRENT_MODIFICATION",
  "message": "O registro foi alterado por outro usuário.",
  "path": "/api/v1/devices/7dc4f659-20be-4c00-bfa7-b3f89f695c85",
  "requestId": "7f1fead9-80cb-47c9-b4eb-09e03753d81c",
  "fieldErrors": []
}
```

### 1.5 Paginação

Parâmetros comuns:

| Parâmetro | Padrão | Regra |
| --- | --- | --- |
| `page` | `0` | Inteiro maior ou igual a zero. |
| `size` | `20` | Entre 1 e 100. |
| `sort` | Por recurso | Repetível no formato `campo,asc|desc`. |

O backend usa allowlist de campos por recurso. Campo ou direção inválidos retornam `INVALID_SORT`.

Resposta:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "sort": ["createdAt,desc"]
}
```

Não é exposto `Page<T>` do Spring.

### 1.6 Sucesso e erro

| Código | Uso |
| --- | --- |
| `200 OK` | Consulta, atualização ou comando com corpo de resposta. |
| `201 Created` | Recurso criado; inclui `Location`. |
| `204 No Content` | Logout idempotente e remoção lógica de foto. |
| `400 Bad Request` | JSON, parâmetro ou campo estruturalmente inválido. |
| `401 Unauthorized` | Credencial ausente, inválida, expirada ou revogada. |
| `403 Forbidden` | Usuário autenticado sem permissão para a ação. |
| `404 Not Found` | Recurso não existe no escopo da rota. |
| `409 Conflict` | Unicidade, duplicidade, estado concorrente ou versão divergente. |
| `413 Payload Too Large` | Arquivo acima do limite. |
| `415 Unsupported Media Type` | Tipo de mídia não aceito. |
| `422 Unprocessable Entity` | Regra de negócio semanticamente inválida. |

Formato único de erro:

```json
{
  "timestamp": "2026-09-04T20:30:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Existem campos inválidos.",
  "path": "/api/v1/devices",
  "requestId": "7f1fead9-80cb-47c9-b4eb-09e03753d81c",
  "fieldErrors": [
    {
      "field": "purchasePrice",
      "message": "deve ser maior que zero"
    }
  ]
}
```

`message` é legível e não substitui `code` na lógica do cliente. Erros não expõem SQL, constraint, stack trace ou dados sensíveis.

## 2. DTOs compartilhados

### 2.1 Referências

```json
{
  "id": "559b191b-cf88-4346-8261-ec01e9627559",
  "name": "Bacelar"
}
```

`UserReference` possui `id` e `name`. `CatalogReference` possui `id`, `code` e `name`.

### 2.2 UserResponse

```json
{
  "id": "559b191b-cf88-4346-8261-ec01e9627559",
  "name": "Bacelar",
  "username": "bacelar",
  "role": "SOCIO",
  "active": true,
  "createdAt": "2026-09-04T12:00:00Z",
  "updatedAt": "2026-09-04T12:00:00Z",
  "version": 0
}
```

### 2.3 Catalog responses

`IphoneModelResponse`:

```json
{
  "id": "c7b8ac6e-ff23-41f1-8a24-186798e7dc68",
  "code": "IPHONE_15_PRO",
  "name": "iPhone 15 Pro",
  "active": true,
  "displayOrder": 150,
  "createdAt": "2026-09-04T12:00:00Z",
  "updatedAt": "2026-09-04T12:00:00Z",
  "version": 0
}
```

`DeviceColorResponse` e `PartCatalogResponse` usam a mesma forma, sem `displayOrder`.

### 2.4 DevicePhotoResponse

```json
{
  "id": "ab45b04b-7c24-4e81-bcbc-8ac1579084f7",
  "url": "https://storage.example/signed-url",
  "originalFilename": "frente.webp",
  "mimeType": "image/webp",
  "sizeBytes": 842315,
  "position": 1,
  "createdAt": "2026-09-04T13:02:00Z",
  "createdBy": {
    "id": "559b191b-cf88-4346-8261-ec01e9627559",
    "name": "Bacelar"
  }
}
```

`url` é temporária. `storageKey` e fotos removidas não são expostas.

### 2.5 DeviceSummaryResponse

```json
{
  "id": "7dc4f659-20be-4c00-bfa7-b3f89f695c85",
  "internalCode": "IPH-000001",
  "model": {
    "id": "c7b8ac6e-ff23-41f1-8a24-186798e7dc68",
    "code": "IPHONE_15_PRO",
    "name": "iPhone 15 Pro"
  },
  "color": {
    "id": "9b8a3c26-3734-4812-ae60-559a173f4f43",
    "code": "NATURAL_TITANIUM",
    "name": "Titânio natural"
  },
  "storageGb": 256,
  "purchasePrice": 1800.00,
  "maintenanceTotal": 250.00,
  "investmentTotal": 2050.00,
  "purchasedAt": "2026-09-04T13:00:00Z",
  "status": "DISPONIVEL_VENDA",
  "archived": false,
  "coverPhotoUrl": "https://storage.example/signed-url",
  "updatedAt": "2026-09-04T15:00:00Z",
  "version": 2
}
```

### 2.6 DeviceDetailResponse

Acrescenta ao resumo:

```json
{
  "faceIdWorking": true,
  "originalScreen": true,
  "originalBattery": false,
  "batteryHealthPercent": 87,
  "photos": [],
  "createdAt": "2026-09-04T13:00:01Z",
  "createdBy": {
    "id": "559b191b-cf88-4346-8261-ec01e9627559",
    "name": "Bacelar"
  },
  "updatedBy": {
    "id": "559b191b-cf88-4346-8261-ec01e9627559",
    "name": "Bacelar"
  },
  "archivedAt": null,
  "archivedBy": null
}
```

Quando o banco contém `battery_health_percent=0`, a API retorna `batteryHealthPercent: null`; a interface não exibe `0%`.

### 2.7 MaintenanceResponse

```json
{
  "id": "384697ae-b5cd-4643-9124-09b2df05a71f",
  "deviceId": "7dc4f659-20be-4c00-bfa7-b3f89f695c85",
  "performedAt": "2026-09-04T15:00:00Z",
  "responsibleUser": {
    "id": "559b191b-cf88-4346-8261-ec01e9627559",
    "name": "Bacelar"
  },
  "status": "ACTIVE",
  "items": [
    {
      "id": "f2ebf1ef-56a6-4c86-99d0-099a2b490b17",
      "position": 1,
      "part": {
        "id": "5e1cf60c-b4d7-4a88-a1af-f6139956aab3",
        "code": "SCREEN",
        "name": "Tela"
      },
      "details": null,
      "cost": 250.00
    }
  ],
  "total": 250.00,
  "cancelledAt": null,
  "cancelledBy": null,
  "cancellationReason": null,
  "createdAt": "2026-09-04T15:01:00Z",
  "version": 0
}
```

### 2.8 SaleResponse

```json
{
  "id": "85174321-b102-4f16-a148-50c63e370e41",
  "deviceId": "7dc4f659-20be-4c00-bfa7-b3f89f695c85",
  "salePrice": 3200.00,
  "soldAt": "2026-09-04T18:00:00Z",
  "responsibleUser": {
    "id": "559b191b-cf88-4346-8261-ec01e9627559",
    "name": "Bacelar"
  },
  "status": "ACTIVE",
  "purchasePrice": 1800.00,
  "maintenanceTotal": 250.00,
  "investmentTotal": 2050.00,
  "profit": 1150.00,
  "marginPercent": 35.9375,
  "cancelledAt": null,
  "cancelledBy": null,
  "cancellationReason": null,
  "createdAt": "2026-09-04T18:00:01Z",
  "version": 0
}
```

### 2.9 FinancialTransactionResponse

```json
{
  "id": "082d1294-a4d9-4555-bdb2-80fe06f677ae",
  "direction": "INFLOW",
  "type": "SALE",
  "amount": 3200.00,
  "occurredAt": "2026-09-04T18:00:00Z",
  "description": null,
  "source": {
    "type": "SALE",
    "id": "85174321-b102-4f16-a148-50c63e370e41"
  },
  "reversalOfId": null,
  "createdAt": "2026-09-04T18:00:01Z",
  "createdBy": {
    "id": "559b191b-cf88-4346-8261-ec01e9627559",
    "name": "Bacelar"
  }
}
```

`source` é `null` nos tipos manuais. Um lançamento aponta para no máximo uma origem operacional.

### 2.10 AuditEventResponse

```json
{
  "id": "0f6bf74c-f05c-4c57-9330-cddcff329e8a",
  "occurredAt": "2026-09-04T18:00:01Z",
  "actorUser": {
    "id": "559b191b-cf88-4346-8261-ec01e9627559",
    "name": "Bacelar"
  },
  "action": "SALE_REGISTERED",
  "entityType": "SALE",
  "entityId": "85174321-b102-4f16-a148-50c63e370e41",
  "entityReference": "IPH-000001",
  "summary": "Venda registrada para o aparelho IPH-000001.",
  "changes": {
    "salePrice": 3200.00,
    "deviceStatus": {"from": "DISPONIVEL_VENDA", "to": "VENDIDO"}
  },
  "requestId": "7f1fead9-80cb-47c9-b4eb-09e03753d81c"
}
```

## 3. Mapa de endpoints

| Método | Rota | Sucesso | Autenticação |
| --- | --- | --- | --- |
| POST | `/auth/login` | 200 | Pública |
| GET | `/auth/me` | 200 | `SOCIO` |
| POST | `/auth/logout` | 204 | `SOCIO` |
| POST | `/users` | 201 | `SOCIO` |
| GET | `/users` | 200 | `SOCIO` |
| GET | `/users/{id}` | 200 | `SOCIO` |
| PATCH | `/users/{id}` | 200 | `SOCIO` |
| POST | `/users/{id}/activate` | 200 | `SOCIO` |
| POST | `/users/{id}/deactivate` | 200 | `SOCIO` |
| GET/POST | `/models` | 200/201 | `SOCIO` |
| GET/PATCH | `/models/{id}` | 200 | `SOCIO` |
| POST | `/models/{id}/activate` | 200 | `SOCIO` |
| POST | `/models/{id}/deactivate` | 200 | `SOCIO` |
| GET/POST | `/colors` | 200/201 | `SOCIO` |
| GET/PATCH | `/colors/{id}` | 200 | `SOCIO` |
| POST | `/colors/{id}/activate` | 200 | `SOCIO` |
| POST | `/colors/{id}/deactivate` | 200 | `SOCIO` |
| GET/POST | `/parts` | 200/201 | `SOCIO` |
| GET/PATCH | `/parts/{id}` | 200 | `SOCIO` |
| POST | `/parts/{id}/activate` | 200 | `SOCIO` |
| POST | `/parts/{id}/deactivate` | 200 | `SOCIO` |
| POST | `/devices` | 201 | `SOCIO` |
| GET | `/devices` | 200 | `SOCIO` |
| GET/PATCH | `/devices/{id}` | 200 | `SOCIO` |
| POST | `/devices/{id}/mark-pending-maintenance` | 200 | `SOCIO` |
| POST | `/devices/{id}/mark-available` | 200 | `SOCIO` |
| POST | `/devices/{id}/archive` | 200 | `SOCIO` |
| POST/GET | `/devices/{id}/photos` | 201/200 | `SOCIO` |
| DELETE | `/devices/{id}/photos/{photoId}` | 204 | `SOCIO` |
| POST/GET | `/devices/{deviceId}/maintenances` | 201/200 | `SOCIO` |
| GET | `/devices/{deviceId}/maintenances/{maintenanceId}` | 200 | `SOCIO` |
| POST | `/devices/{deviceId}/maintenances/{maintenanceId}/cancel` | 200 | `SOCIO` |
| POST/GET | `/devices/{deviceId}/sale` | 201/200 | `SOCIO` |
| POST | `/devices/{deviceId}/sale/cancel` | 200 | `SOCIO` |
| GET | `/financial/summary` | 200 | `SOCIO` |
| GET | `/financial/transactions` | 200 | `SOCIO` |
| POST | `/financial/opening-balance` | 201 | `SOCIO` |
| POST | `/financial/contributions` | 201 | `SOCIO` |
| POST | `/financial/withdrawals` | 201 | `SOCIO` |
| POST | `/financial/adjustments` | 201 | `SOCIO` |
| GET | `/audit` | 200 | `SOCIO` |
| GET | `/audit/{id}` | 200 | `SOCIO` |

## 4. Autenticação

### POST `/auth/login`

Request:

```json
{
  "username": "bacelar",
  "password": "senha-nao-exibida"
}
```

Response `200`:

```json
{
  "accessToken": "irs_token-opaco-para-o-cliente",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-04T22:30:00Z",
  "user": {}
}
```

Erros: `400 VALIDATION_ERROR`, `401 AUTHENTICATION_FAILED`, `429 LOGIN_RATE_LIMITED`. A resposta 401 é igual para username inexistente, senha incorreta ou usuário inativo. O rate limit padrão permite cinco tentativas por minuto por peer direto, é reiniciado no sucesso e inclui `Retry-After` ao bloquear.

### GET `/auth/me`

Response `200`: `UserResponse`. Erros: `401 UNAUTHORIZED`.

### POST `/auth/logout`

Não recebe body. Bearer ausente ou malformado retorna `401 UNAUTHORIZED`. Bearer sintaticamente válido sempre retorna `204`: se a sessão estiver ativa, preenche `revokedAt`; se for desconhecida, expirada ou já revogada, não altera dados. Assim, repetição não revela a existência da sessão. O token revogado passa a retornar 401 nas rotas normais.

As três rotas de autenticação usam `Cache-Control: no-store`, devolvem `X-Request-Id` e nunca expõem `passwordHash`, `tokenHash` ou entidade JPA. Respostas 401 incluem `WWW-Authenticate: Bearer realm="iphone-resale"`.

## 5. Usuários

### POST `/users`

```json
{
  "name": "Segundo Sócio",
  "username": "segundo.socio",
  "password": "senha-inicial-segura"
}
```

Resposta: `201 UserResponse` e `Location: /api/v1/users/{id}`.

Erros: `400 VALIDATION_ERROR`, `409 USERNAME_ALREADY_EXISTS`.

### GET `/users`

Query: `search`, `active`, `page`, `size`, `sort`. Ordenação permitida: `name`, `username`, `createdAt`, `updatedAt`; padrão `name,asc`.

Resposta: `200 PageResponse<UserResponse>`.

### GET `/users/{id}`

Resposta: `200 UserResponse`. Erros: `400 INVALID_UUID`, `404 USER_NOT_FOUND`.

### PATCH `/users/{id}`

```json
{
  "expectedVersion": 2,
  "name": "Nome atualizado",
  "username": "novo.username",
  "newPassword": "nova-senha-segura"
}
```

Campos opcionais, mas ao menos um deve ser enviado. `newPassword` só é aceito para o próprio usuário. `null` não apaga campos.

Resposta: `200 UserResponse`. Erros: `404 USER_NOT_FOUND`, `409 USERNAME_ALREADY_EXISTS`, `409 CONCURRENT_MODIFICATION`, `422 PASSWORD_CHANGE_NOT_ALLOWED`.

### POST `/users/{id}/activate` e `/deactivate`

```json
{"expectedVersion": 2}
```

Resposta: `200 UserResponse`. A operação já satisfeita é idempotente. Erros adicionais de desativação: `422 CANNOT_DEACTIVATE_CURRENT_USER`, `422 LAST_ACTIVE_USER_REQUIRED`.

## 6. Catálogos

As três famílias compartilham paginação e ativação sem hard delete.

### Listagem

`GET /models`, `GET /colors`, `GET /parts` aceitam `search`, `active`, `page`, `size`, `sort`.

- modelos: sort permitido `name`, `code`, `displayOrder`, `createdAt`; padrão `displayOrder,asc`;
- cores e peças: `name`, `code`, `createdAt`; padrão `name,asc`.

`active` ausente retorna ativos e inativos; seletores devem enviar `active=true`.

### Consulta individual

`GET /models/{id}`, `GET /colors/{id}`, `GET /parts/{id}` retornam o DTO correspondente ou `MODEL_NOT_FOUND`, `COLOR_NOT_FOUND`, `PART_NOT_FOUND`.

### Criação

`POST /models`:

```json
{
  "code": "IPHONE_15_PRO",
  "name": "iPhone 15 Pro",
  "displayOrder": 150
}
```

`POST /colors` e `POST /parts`:

```json
{
  "code": "NATURAL_TITANIUM",
  "name": "Titânio natural"
}
```

Resposta: `201`, DTO criado e `Location`. O backend normaliza o código para maiúsculas antes de validar.

Erros: `409 CATALOG_CODE_ALREADY_EXISTS`, `409 CATALOG_NAME_ALREADY_EXISTS`.

### Atualização

`PATCH /models/{id}`:

```json
{
  "expectedVersion": 1,
  "name": "iPhone 15 Pro",
  "displayOrder": 151
}
```

`PATCH /colors/{id}` e `PATCH /parts/{id}` aceitam `expectedVersion` e `name`. Código e `active` são rejeitados como campos desconhecidos.

### Ativação e desativação

`POST /{catalog}/{id}/activate` e `/deactivate` recebem:

```json
{"expectedVersion": 1}
```

Resposta `200` com o DTO atualizado. Desativação não falha por haver referências históricas.

## 7. Aparelhos

### POST `/devices`

Content-Type: `multipart/form-data`.

Partes:

| Nome | Content-Type | Cardinalidade | Conteúdo |
| --- | --- | --- | --- |
| `device` | `application/json` | 1 | `RegisterDeviceRequest` |
| `photos` | imagem | 2–4 | Arquivos na ordem das posições 1–4 |

`RegisterDeviceRequest`:

```json
{
  "modelId": "c7b8ac6e-ff23-41f1-8a24-186798e7dc68",
  "colorId": "9b8a3c26-3734-4812-ae60-559a173f4f43",
  "storageGb": 256,
  "purchasePrice": 1800.00,
  "purchasedAt": "2026-09-04T13:00:00Z",
  "faceIdWorking": true,
  "originalScreen": true,
  "originalBattery": false,
  "batteryHealthPercent": 87,
  "initialStatus": "PENDENTE_MANUTENCAO"
}
```

`batteryHealthPercent=0` significa não aferido. Tipos aceitos: `image/jpeg`, `image/png`, `image/webp`; máximo 10 MiB por arquivo. O backend verifica assinatura real e não apenas o header.

Resposta: `201 DeviceDetailResponse`, `Location: /api/v1/devices/{id}`.

Nas escritas deste endpoint via Hibernate/JPA, `GenerationType.UUID` gera o UUID do `Device` antes do `INSERT`. O default PostgreSQL `gen_random_uuid()` permanece disponível para SQL direto, cargas e integrações externas. O `internalCode` é gerado pelo PostgreSQL.

Erros: `404 MODEL_NOT_FOUND`, `404 COLOR_NOT_FOUND`, `422 CATALOG_ITEM_INACTIVE`, `422 PHOTO_MINIMUM_VIOLATION`, `422 PHOTO_LIMIT_EXCEEDED`, `413 FILE_TOO_LARGE`, `415 UNSUPPORTED_IMAGE_TYPE`.

### GET `/devices`

Query:

| Parâmetro | Tipo | Regra |
| --- | --- | --- |
| `search` | string | Código interno prioritariamente; também modelo/cor. |
| `status` | enum repetível | Um ou mais `DeviceStatus`. |
| `modelId` | UUID | Modelo. |
| `colorId` | UUID | Cor. |
| `storageGb` | integer | Capacidade aprovada. |
| `purchasedFrom` | instant | Inclusivo. |
| `purchasedTo` | instant | Exclusivo. |
| `archived` | boolean | Padrão `false`. |

Ordenação permitida: `internalCode`, `purchasePrice`, `purchasedAt`, `createdAt`, `updatedAt`; padrão `createdAt,desc`.

Resposta: `200 PageResponse<DeviceSummaryResponse>`.

### GET `/devices/{id}`

Resposta: `200 DeviceDetailResponse`, inclusive para arquivado. Erros: `404 DEVICE_NOT_FOUND`.

### PATCH `/devices/{id}`

```json
{
  "expectedVersion": 2,
  "modelId": "c7b8ac6e-ff23-41f1-8a24-186798e7dc68",
  "colorId": "9b8a3c26-3734-4812-ae60-559a173f4f43",
  "storageGb": 256,
  "purchasePrice": 1850.00,
  "purchasedAt": "2026-09-04T13:00:00Z",
  "faceIdWorking": true,
  "originalScreen": true,
  "originalBattery": false,
  "batteryHealthPercent": 88
}
```

Todos os campos, exceto `expectedVersion`, são opcionais; ao menos um deve existir. `status`, `internalCode` e arquivamento não são aceitos.

Resposta: `200 DeviceDetailResponse`. Se compra/data mudar, a resposta só ocorre após estorno e novo lançamento confirmados.

Erros: `404 DEVICE_NOT_FOUND`, `422 DEVICE_ARCHIVED`, `422 DEVICE_PURCHASE_LOCKED_BY_SALE`, `422 CATALOG_ITEM_INACTIVE`, `409 CONCURRENT_MODIFICATION`.

### POST `/devices/{id}/mark-pending-maintenance`

### POST `/devices/{id}/mark-available`

Request comum:

```json
{"expectedVersion": 2}
```

Resposta: `200 DeviceDetailResponse`. Erros: `404 DEVICE_NOT_FOUND`, `422 INVALID_DEVICE_STATUS_TRANSITION`, `422 DEVICE_ARCHIVED`, `409 CONCURRENT_MODIFICATION`.

### POST `/devices/{id}/archive`

```json
{
  "expectedVersion": 2,
  "reason": "Cadastro de compra inválido confirmado pelos sócios."
}
```

Resposta: `200 DeviceDetailResponse`. A resposta só é emitida após cancelamentos/estornos necessários e arquivamento atômico.

O arquivamento é terminal no MVP: não existe `UnarchiveDevice`, o aparelho arquivado permanece imutável e qualquer reativação futura dependerá de nova decisão arquitetural.

Erros: `404 DEVICE_NOT_FOUND`, `409 DEVICE_ALREADY_ARCHIVED`, `422 DEVICE_HAS_ACTIVE_SALE`, `409 CONCURRENT_MODIFICATION`.

## 8. Fotos

### POST `/devices/{id}/photos`

Content-Type: `multipart/form-data`.

| Parte | Tipo | Obrigatória |
| --- | --- | --- |
| `file` | arquivo | Sim |
| `position` | inteiro 1–4 | Sim |

Resposta: `201 DevicePhotoResponse` e `Location: /api/v1/devices/{id}/photos/{photoId}`.

Erros: `404 DEVICE_NOT_FOUND`, `422 DEVICE_ARCHIVED`, `409 PHOTO_POSITION_OCCUPIED`, `422 PHOTO_LIMIT_EXCEEDED`, `413 FILE_TOO_LARGE`, `415 UNSUPPORTED_IMAGE_TYPE`.

### GET `/devices/{id}/photos`

Resposta: `200` com array de `DevicePhotoResponse`, ordenado por posição. Não é paginado porque o máximo ativo é quatro.

### DELETE `/devices/{id}/photos/{photoId}`

Efetua remoção lógica e retorna `204` sem body.

Erros: `404 DEVICE_NOT_FOUND`, `404 PHOTO_NOT_FOUND`, `422 PHOTO_MINIMUM_VIOLATION`.

## 9. Manutenções

### POST `/devices/{deviceId}/maintenances`

```json
{
  "performedAt": "2026-09-04T15:00:00Z",
  "items": [
    {
      "partId": "5e1cf60c-b4d7-4a88-a1af-f6139956aab3",
      "details": null,
      "cost": 250.00
    },
    {
      "partId": "fa27accc-eea7-48c6-91d7-80fd1abfb654",
      "details": "Limpeza técnica",
      "cost": 0.00
    }
  ]
}
```

O nome final é `performedAt`, coerente com o domínio. A ordem do array determina `position`. O responsável é o usuário autenticado; `maintenanceTotal` não é aceito.

Resposta: `201 MaintenanceResponse`, `Location: /api/v1/devices/{deviceId}/maintenances/{id}`.

Erros: `404 DEVICE_NOT_FOUND`, `404 PART_NOT_FOUND`, `422 DEVICE_ARCHIVED`, `422 DEVICE_ALREADY_SOLD`, `422 CATALOG_ITEM_INACTIVE`, `422 MAINTENANCE_ITEM_REQUIRED`, `422 DETAILS_REQUIRED_FOR_OTHER`, `422 MAINTENANCE_DATE_BEFORE_PURCHASE`.

### GET `/devices/{deviceId}/maintenances`

Query: `status`, `from`, `to`, paginação e sort. Sort permitido: `performedAt`, `createdAt`, `status`; padrão `performedAt,desc`.

Resposta: `200 PageResponse<MaintenanceResponse>`.

### GET `/devices/{deviceId}/maintenances/{maintenanceId}`

Resposta: `200 MaintenanceResponse`. Um ID que não pertence ao aparelho retorna `404 MAINTENANCE_NOT_FOUND`.

### POST `/devices/{deviceId}/maintenances/{maintenanceId}/cancel`

```json
{
  "expectedVersion": 0,
  "reason": "Lançamento duplicado."
}
```

Resposta: `200 MaintenanceResponse`. Erros: `404 MAINTENANCE_NOT_FOUND`, `409 MAINTENANCE_ALREADY_CANCELLED`, `422 DEVICE_ALREADY_SOLD`, `409 CONCURRENT_MODIFICATION`.

## 10. Venda

### POST `/devices/{deviceId}/sale`

```json
{
  "deviceVersion": 3,
  "salePrice": 3200.00,
  "soldAt": "2026-09-04T18:00:00Z"
}
```

Resposta: `201 SaleResponse`, `Location: /api/v1/devices/{deviceId}/sale`.

Erros: `404 DEVICE_NOT_FOUND`, `422 DEVICE_ARCHIVED`, `422 DEVICE_NOT_AVAILABLE_FOR_SALE`, `409 SALE_ALREADY_EXISTS`, `422 SALE_DATE_BEFORE_PURCHASE`, `409 CONCURRENT_MODIFICATION`.

A resposta só é emitida após venda, status `VENDIDO`, entrada no ledger e auditoria confirmarem juntos.

### GET `/devices/{deviceId}/sale`

Retorna a venda ativa: `200 SaleResponse`. Sem venda ativa: `404 SALE_NOT_FOUND`.

### POST `/devices/{deviceId}/sale/cancel`

```json
{
  "saleVersion": 0,
  "deviceVersion": 4,
  "reason": "Venda desfeita e valor devolvido ao comprador."
}
```

Resposta: `200 SaleResponse` com `status=CANCELLED`. Erros: `404 SALE_NOT_FOUND`, `409 SALE_ALREADY_CANCELLED`, `409 CONCURRENT_MODIFICATION`, `422 INVALID_DEVICE_STATUS_TRANSITION`.

## 11. Financeiro

### GET `/financial/summary`

Query opcional: `from`, `to`. Ambos devem ser enviados juntos; `to` deve ser posterior a `from`. Ausentes, os limites são o mês atual em `America/Bahia`.

Response `200`:

```json
{
  "period": {
    "from": "2026-09-01T03:00:00Z",
    "to": "2026-10-01T03:00:00Z",
    "businessTimezone": "America/Bahia"
  },
  "openingBalance": 10000.00,
  "closingBalance": 11150.00,
  "revenue": 3200.00,
  "devicePurchaseCost": 1800.00,
  "maintenanceCost": 250.00,
  "profit": 1150.00,
  "marginPercent": 35.9375,
  "stockCapital": 0.00,
  "calculatedAt": "2026-09-04T20:30:00Z"
}
```

`marginPercent` é `null` quando `revenue=0`.

### GET `/financial/transactions`

Query: `from`, `to`, `type` repetível, `direction` repetível, paginação e sort. Sort permitido: `occurredAt`, `createdAt`, `amount`, `type`, `direction`; padrão `occurredAt,desc`.

Resposta: `200 PageResponse<FinancialTransactionResponse>`.

### POST `/financial/opening-balance`

### POST `/financial/contributions`

### POST `/financial/withdrawals`

Request comum:

```json
{
  "amount": 10000.00,
  "occurredAt": "2026-09-01T12:00:00Z",
  "description": "Capital inicial dos sócios."
}
```

Resposta: `201 FinancialTransactionResponse` e `Location: /api/v1/financial/transactions/{id}`.

Saldo inicial adicional retorna `409 OPENING_BALANCE_ALREADY_EXISTS`. Valores zero ou negativos retornam `400 VALIDATION_ERROR`.

A implementação deve tornar atômica a verificação e a criação do saldo inicial, usando lock transacional, advisory lock ou estratégia equivalente, para que requisições simultâneas não criem dois `OPENING_BALANCE` não estornados. A migration V1 não será alterada por esta decisão.

### POST `/financial/adjustments`

Modo ajuste livre:

```json
{
  "direction": "INFLOW",
  "amount": 50.00,
  "occurredAt": "2026-09-04T19:00:00Z",
  "description": "Correção após conferência de caixa."
}
```

Modo estorno de lançamento manual:

```json
{
  "reversalOfTransactionId": "082d1294-a4d9-4555-bdb2-80fe06f677ae",
  "occurredAt": "2026-09-04T19:00:00Z",
  "description": "Estorno de lançamento manual duplicado."
}
```

Os modos são exclusivos. No estorno, `direction` e `amount` não são aceitos; o backend os deriva do original.

Resposta: `201 FinancialTransactionResponse`. Erros: `404 FINANCIAL_TRANSACTION_NOT_FOUND`, `409 FINANCIAL_TRANSACTION_ALREADY_REVERSED`, `422 OPERATIONAL_REVERSAL_NOT_ALLOWED`, `422 INVALID_FINANCIAL_OPERATION`.

Não existem endpoints manuais para `DEVICE_PURCHASE`, `MAINTENANCE`, `SALE` ou seus estornos.

## 12. Auditoria

### GET `/audit`

Query:

| Parâmetro | Tipo |
| --- | --- |
| `userId` | UUID |
| `entityType` | `AuditedEntityType` repetível |
| `entityId` | UUID |
| `action` | `AuditAction` repetível |
| `from` | instante inclusivo |
| `to` | instante exclusivo |
| `page`, `size`, `sort` | paginação |

Sort permitido: `occurredAt`; padrão `occurredAt,desc`.

Resposta: `200 PageResponse<AuditEventResponse>`.

### GET `/audit/{id}`

Resposta: `200 AuditEventResponse`. Erros: `404 AUDIT_EVENT_NOT_FOUND`.

Não existem `POST`, `PATCH` ou `DELETE` para auditoria.

## 13. Matriz de erros

| Código | HTTP | Contexto |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | Campos inválidos. |
| `MALFORMED_REQUEST` | 400 | JSON/estrutura inválida ou campo desconhecido. |
| `INVALID_UUID` | 400 | UUID malformado. |
| `INVALID_QUERY_PARAMETER` | 400 | Filtro inválido. |
| `INVALID_SORT` | 400 | Ordenação fora da allowlist. |
| `AUTHENTICATION_FAILED` | 401 | Login não aceito sem revelar o motivo. |
| `UNAUTHORIZED` | 401 | Credencial ausente, inválida, expirada ou revogada. |
| `FORBIDDEN` | 403 | Operação não autorizada. |
| `USER_NOT_FOUND` | 404 | Usuário inexistente. |
| `MODEL_NOT_FOUND` | 404 | Modelo inexistente. |
| `COLOR_NOT_FOUND` | 404 | Cor inexistente. |
| `PART_NOT_FOUND` | 404 | Peça inexistente. |
| `DEVICE_NOT_FOUND` | 404 | Aparelho inexistente. |
| `PHOTO_NOT_FOUND` | 404 | Foto inexistente no aparelho. |
| `MAINTENANCE_NOT_FOUND` | 404 | Manutenção inexistente no aparelho. |
| `SALE_NOT_FOUND` | 404 | Venda ativa inexistente. |
| `FINANCIAL_TRANSACTION_NOT_FOUND` | 404 | Lançamento inexistente. |
| `AUDIT_EVENT_NOT_FOUND` | 404 | Evento inexistente. |
| `USERNAME_ALREADY_EXISTS` | 409 | Username duplicado. |
| `CATALOG_CODE_ALREADY_EXISTS` | 409 | Código de catálogo duplicado. |
| `CATALOG_NAME_ALREADY_EXISTS` | 409 | Nome de catálogo duplicado sem diferenciar caixa. |
| `CONCURRENT_MODIFICATION` | 409 | Versão obsoleta ou corrida de gravação. |
| `PHOTO_POSITION_OCCUPIED` | 409 | Posição ativa já utilizada. |
| `MAINTENANCE_ALREADY_CANCELLED` | 409 | Cancelamento repetido. |
| `SALE_ALREADY_EXISTS` | 409 | Aparelho já possui venda ativa. |
| `SALE_ALREADY_CANCELLED` | 409 | Cancelamento repetido. |
| `DEVICE_ALREADY_ARCHIVED` | 409 | Arquivamento repetido. |
| `FINANCIAL_TRANSACTION_ALREADY_REVERSED` | 409 | Original já possui estorno. |
| `OPENING_BALANCE_ALREADY_EXISTS` | 409 | Já há saldo inicial não estornado. |
| `PASSWORD_CHANGE_NOT_ALLOWED` | 422 | Tentativa de alterar senha de outro usuário. |
| `CANNOT_DEACTIVATE_CURRENT_USER` | 422 | Autodesativação bloqueada. |
| `LAST_ACTIVE_USER_REQUIRED` | 422 | Desativação eliminaria o último acesso. |
| `CATALOG_ITEM_INACTIVE` | 422 | Item inativo em novo uso. |
| `DEVICE_ARCHIVED` | 422 | Mutação proibida em aparelho arquivado. |
| `DEVICE_HAS_ACTIVE_SALE` | 422 | Arquivamento exige cancelamento prévio da venda. |
| `DEVICE_ALREADY_SOLD` | 422 | Manutenção proibida com venda ativa. |
| `DEVICE_PURCHASE_LOCKED_BY_SALE` | 422 | Compra não pode mudar com venda ativa. |
| `INVALID_DEVICE_STATUS_TRANSITION` | 422 | Transição não permitida. |
| `DEVICE_NOT_AVAILABLE_FOR_SALE` | 422 | Status impede venda. |
| `PHOTO_LIMIT_EXCEEDED` | 422 | Mais de quatro fotos ativas. |
| `PHOTO_MINIMUM_VIOLATION` | 422 | Menos de duas fotos ativas. |
| `MAINTENANCE_ITEM_REQUIRED` | 422 | Manutenção sem item. |
| `DETAILS_REQUIRED_FOR_OTHER` | 422 | Peça `OTHER` sem detalhes. |
| `MAINTENANCE_DATE_BEFORE_PURCHASE` | 422 | Data de manutenção anterior à compra. |
| `SALE_DATE_BEFORE_PURCHASE` | 422 | Data de venda anterior à compra. |
| `INVALID_FINANCIAL_OPERATION` | 422 | Tipo/direção/origem inválidos. |
| `OPERATIONAL_REVERSAL_NOT_ALLOWED` | 422 | Tentativa manual de estornar operação automática. |
| `FILE_TOO_LARGE` | 413 | Imagem maior que 10 MiB. |
| `UNSUPPORTED_IMAGE_TYPE` | 415 | Tipo ou assinatura de imagem não aceitos. |

## 14. Regras de implementação futura derivadas do contrato

- Controllers apenas traduzem HTTP; casos de uso controlam regras e transações.
- DTOs web são distintos de comandos/resultados da application e de entidades JPA.
- Erros PostgreSQL esperados são traduzidos para os códigos acima; mensagens do banco não vazam.
- Cálculos usam `BigDecimal`; margem usa escala de quatro casas e arredondamento explícito.
- Listas evitam N+1 e sempre aplicam paginação, exceto fotos ativas.
- Endpoints de escrita não aceitam campos derivados, autoria, status interno de cancelamento ou IDs de lançamento automático.
- O contrato não autoriza alteração da migration V1 nem implementação antecipada da Etapa E.

## 15. Registro de encerramento

Este contrato e os casos de uso correspondentes foram aprovados como Etapa D versão 1.0. A Etapa D está encerrada, a migration V1 permanece inalterada e nenhuma implementação da Etapa E foi iniciada.


---

## 16. Refinamento da Etapa G — catálogos, aparelhos e implantação em preparação

A Etapa G implementa os contratos autenticados em `/api/v1` para:

- `/models` e `/colors`: listar, criar, consultar, editar, ativar e desativar, com paginação, busca, sort allowlist e `expectedVersion`;
- `/business-initialization`: consultar, iniciar `PREPARING`, alterar o `cutoffAt` antes da primeira importação e consultar o preview;
- `/devices`: cadastrar compra operacional multipart, listar, filtrar, consultar, editar, alterar status e arquivar;
- `/devices/initial-import`: importar estoque existente usando o `cutoffAt` persistido, sem criar `DEVICE_PURCHASE`;
- `/devices/{id}/photos`: listar, adicionar e remover logicamente fotos, preservando 2–4 ativas;
- `/device-photos/content/{id}`: servir mídia somente mediante URL temporária assinada.

O JSON do aparelho nunca aceita `registrationOrigin`, `internalCode`, autoria ou arquivamento. A origem é definida pelo endpoint e permanece imutável. `PATCH` e comandos mutáveis exigem `expectedVersion`. A conclusão da implantação, manutenção, venda e financeiro completo continuam fora do contrato executável desta etapa.

## 17. Refinamento executável da Etapa H

### Catálogo de peças

| Método e rota | Semântica |
| --- | --- |
| `GET /parts` | Busca por `search`, `active`, paginação e sort `name|code|createdAt`. |
| `POST /parts` | Recebe `code` e `name`; responde `201 + Location`. |
| `GET /parts/{id}` | Consulta item ativo ou inativo. |
| `PATCH /parts/{id}` | Recebe `expectedVersion` e `name`; código não é editável. |
| `POST /parts/{id}/activate` | Recebe `expectedVersion`. |
| `POST /parts/{id}/deactivate` | Recebe `expectedVersion`; preserva histórico. |

### Manutenções

`POST /devices/{deviceId}/maintenances` cria origem `OPERATIONAL` e
`POST /devices/{deviceId}/maintenances/initial-import` cria origem `INITIAL_IMPORT`.
Ambos recebem somente:

```json
{
  "performedAt": "2026-09-09T18:00:00Z",
  "items": [
    { "partId": "uuid", "details": null, "cost": 250.00 }
  ]
}
```

O cliente não envia total, posição, status, origem nem responsável. O backend retorna
`registrationOrigin`, itens ordenados, total calculado e `financialImpact`, sem expor ID
interno do ledger ou entidade JPA.

| Estado | `financialImpact` |
| --- | --- |
| `ACTIVE + OPERATIONAL + total > 0` | `OUTFLOW_CREATED` |
| `CANCELLED + OPERATIONAL + total > 0` | `OUTFLOW_REVERSED` |
| `OPERATIONAL + total = 0` | `NO_FINANCIAL_COST` |
| `INITIAL_IMPORT` | `HISTORICAL_COST_ONLY` |

No detalhe do aparelho, `maintenanceTotal` é o total das manutenções `ACTIVE`.
A quantidade ativa é obtida por `GET /maintenances?status=ACTIVE&size=1` usando
`totalElements`; cada item da lista inclui `responsibleUser`.

`GET /devices/{deviceId}/maintenances` aceita `status`, `from`, `to`, `page`, `size` e
sort allowlist `performedAt|createdAt|status`; padrão `performedAt,desc`.
`GET /devices/{deviceId}/maintenances/{maintenanceId}` exige vínculo com o aparelho.

O cancelamento usa `POST /devices/{deviceId}/maintenances/{maintenanceId}/cancel`:

```json
{
  "expectedVersion": 0,
  "reason": "Lançamento duplicado."
}
```

`expectedVersion` é `Long`, obrigatório e não negativo. Não existe PATCH de manutenção.

Erros específicos implementados: `PART_NOT_FOUND`, `CATALOG_ITEM_INACTIVE`,
`DETAILS_REQUIRED_FOR_OTHER`, `MAINTENANCE_DATE_BEFORE_PURCHASE`,
`MAINTENANCE_NOT_FOUND`, `MAINTENANCE_ALREADY_CANCELLED`,
`INITIAL_MAINTENANCE_IMPORT_CLOSED`,
`INITIAL_MAINTENANCE_REQUIRES_IMPORTED_DEVICE`,
`INITIAL_MAINTENANCE_INVALID_DATE`, `MAINTENANCE_REQUIRES_OPERATIONAL_PERIOD` e
`MAINTENANCE_LEDGER_MISSING`.
