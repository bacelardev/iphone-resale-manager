# Registro de Decisões Arquiteturais

## Etapa D — Casos de Uso e Contratos REST

Versão: **1.0 aprovada**  
Status: **Etapa D encerrada**

As decisões abaixo detalham a fronteira HTTP e a orquestração dos casos de uso. Elas não alteram o domínio, a migration V1, as entidades JPA ou as fórmulas aprovadas nas Etapas A, B e C.

### D-01 — Convenções REST

**Decisão:** API JSON versionada em `/api/v1`, propriedades `camelCase`, UUIDs em texto, valores monetários em BRL e instantes ISO 8601. Criações usam `201 + Location`; comandos sem resposta usam `204`; não há hard delete de recursos históricos.

**Consequência:** clientes web e iOS usam o mesmo contrato estável. Mudança incompatível exige nova versão.

### D-02 — Separação DTO/Entity

**Decisão:** entidades JPA nunca são requests ou responses. A cadeia é Web DTO → comando/caso de uso → domínio/persistência → resultado da application → Web DTO.

**Consequência:** lazy loading, campos internos e mudanças do ORM não vazam para a API.

### D-03 — Identificação

**Decisão:** UUID é o identificador de rota. Nas escritas via Hibernate/JPA, `GenerationType.UUID` gera o UUID antes do `INSERT`; o PostgreSQL mantém `gen_random_uuid()` como default para SQL direto, cargas e integrações externas. `Device.internalCode` (`IPH-000001`) continua sendo gerado pelo PostgreSQL, é imutável, exibido e pesquisável, mas não substitui a chave técnica.

**Consequência:** não se expõe volume sequencial e a API permanece adequada a clientes móveis futuros.

### D-04 — Datas e timezone

**Decisão:** API recebe instantes com offset e responde em UTC. Intervalos são `[from,to)`. Presets civis são calculados em `America/Bahia`.

**Refinamento:** o request de manutenção usa `performedAt`, coerente com o domínio; `occurredAt` fica reservado ao ledger.

### D-05 — Autenticação

**Decisão:** endpoints protegidos usam `Authorization: Bearer`. Login devolve token opaco, tipo, expiração e usuário; não há refresh token no MVP. O formato interno do token e sua implementação Spring Security ficam para a etapa seguinte.

**Decisão adicional:** não existe cadastro público para o primeiro usuário. Bootstrap será operacional e seguro.

**Consequência:** o transporte HTTP fica formalizado sem antecipar JWT, sessão ou infraestrutura definitiva.

### D-06 — Usuários e catálogos

**Decisão:** todos os usuários têm papel `SOCIO`. Usuários e itens de catálogo são ativados/desativados por operações semânticas, nunca apagados. Códigos de catálogo são imutáveis; nomes são únicos sem diferenciar caixa.

**Refinamentos:** mudança de senha no PATCH só é aceita para o próprio usuário; não se pode desativar a própria credencial nem o último usuário ativo.

### D-07 — Aparelhos e fotos

**Decisão:** cadastro de aparelho é multipart e conclui aparelho, 2–4 fotos, saída de compra e auditoria em uma operação logicamente única. Uploads externos usam compensação se a transação SQL falhar.

**Refinamentos:** `batteryHealthPercent=0` é aceito como não aferido, mas sai como `null`; status não é alterado pelo PATCH; fotos têm remoção lógica; arquivamento usa endpoint semântico e é terminal. Não existe `UnarchiveDevice` no MVP, aparelho arquivado permanece imutável e eventual reativação exige decisão futura.

### D-08 — Manutenção

**Decisão:** manutenção é criada com um ou mais itens, tem total calculado e não aceita `maintenanceTotal` do cliente. Total positivo gera `MAINTENANCE/OUTFLOW`; total zero não gera lançamento. Correção cancela o conjunto e não edita itens.

### D-09 — Venda e cancelamento

**Decisão:** venda ocorre somente para aparelho disponível, é atômica com status, ledger e auditoria, e pode resultar em lucro negativo. Cancelamento preserva a venda, cria `SALE_REVERSAL` e retorna o aparelho a disponível.

**Consequência:** nunca há duas vendas ativas nem estado parcial.

### D-10 — Financeiro

**Decisão:** o resumo usa ledger para saldos, fontes ativas para métricas econômicas e snapshot atual para capital em estoque. `marginPercent` é `null` quando não há faturamento.

**Refinamentos:** `RegisterManualAdjustment` possui modo livre e modo de estorno. O modo de estorno só aceita lançamentos manuais; transações operacionais são revertidas pelo caso de uso de origem. A criação de `OPENING_BALANCE` protege a regra de apenas um registro não estornado com lock transacional, advisory lock ou estratégia equivalente, sem alterar a migration V1.

### D-11 — Auditoria

**Decisão:** auditoria é somente leitura pela API. Cada operação produz preferencialmente um evento principal legível; efeitos secundários são correlacionados em `changes` e `requestId`. `X-Request-Id` serve para correlação, rastreabilidade e auditoria, não como chave automática de idempotência. Idempotência futura de comandos críticos exigirá mecanismo próprio, como `Idempotency-Key`, e decisão específica.

**Consequência:** preserva rastreabilidade sem criar uma timeline ruidosa e sem expor segredo.

### D-12 — Paginação e filtros

**Decisão:** padrão `page=0`, `size=20`, máximo 100. `sort` usa allowlist por recurso e o formato `campo,direção`. Fotos ativas não são paginadas porque o limite é quatro.

**Consequência:** consultas são previsíveis e não permitem injeção de propriedade de ordenação.

### D-13 — Erros HTTP

**Decisão:** todos os erros usam `timestamp`, `status`, `code`, `message`, `path`, `requestId` e `fieldErrors`. O cliente decide por `code`, não por mensagem.

**Refinamento:** além dos códigos-base, uploads usam `413` e `415`. Conflitos de versão/unicidade usam `409`; regra de negócio usa `422`; estrutura inválida usa `400`.

### D-14 — Transações

**Decisão:** mutação, ledger e auditoria do mesmo caso confirmam ou falham juntos. Constraints diferíveis são verificadas antes do commit. Falha de auditoria causa rollback. Storage externo adota compensação explícita.

**Consequência:** não existe aparelho sem fotos/compra, manutenção paga sem saída, venda sem entrada ou status vendido sem venda ativa.

### D-15 — Concorrência

**Decisão:** mutações recebem `expectedVersion`; divergência retorna `409 CONCURRENT_MODIFICATION`. Venda e operações de coleção também bloqueiam a linha do aparelho durante a transação, enquanto índices e constraints do PostgreSQL permanecem como proteção final.

**Consequência:** entre duas vendas simultâneas, uma confirma e a outra falha de forma controlada; nunca há duas vendas ativas.

## Registro de aprovação

As decisões D-01 a D-15 foram aprovadas como versão final da Etapa D. A Etapa D está encerrada. Nenhuma implementação da Etapa E foi iniciada, e a próxima etapa depende de nova autorização explícita.
