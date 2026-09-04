# Sprint 1 — Etapa C: Flyway, Pacotes Java e Entidades JPA

Versão: **1.0 aprovada**  
Status: **Etapa C encerrada em 03/09/2026 após validação integrada**  
Dependência: **Etapas A e B aprovadas**

## 1. Resultado da etapa

Esta etapa transforma o modelo PostgreSQL aprovado em uma base executável de projeto:

- migration Flyway `V1__initial_schema.sql`;
- projeto Maven para Java 21 e Spring Boot 3.5;
- configuração de PostgreSQL, Flyway, Hibernate e UTC;
- estrutura de pacotes por camadas;
- oito enums persistidos por nome;
- onze entidades JPA e três superclasses de mapeamento;
- auditoria JPA preparada desde o início;
- ambiente PostgreSQL local de referência com Docker Compose.

Não foram criados controllers, serviços de caso de uso, repositórios, autenticação nem frontend. Nenhum trabalho da fase seguinte foi iniciado durante a validação.

## 2. Artefatos principais

| Artefato | Finalidade |
| --- | --- |
| `backend/src/main/resources/db/migration/V1__initial_schema.sql` | Criação integral do schema PostgreSQL aprovado. |
| `backend/pom.xml` | Build Maven, Java 21 e dependências da fundação. |
| `backend/src/main/resources/application.yml` | Flyway ativo, Hibernate em `validate`, UTC e secrets por ambiente. |
| `backend/compose.yaml` | PostgreSQL 16 local para desenvolvimento. |
| `backend/src/main/java/.../domain/model` | Entidades e superclasses JPA. |
| `backend/src/main/java/.../domain/enums` | Conjuntos fechados persistidos como `VARCHAR`. |
| `backend/src/main/java/.../config/JpaAuditingConfiguration.java` | Ativação e integração da auditoria JPA. |

## 3. Migration `V1__initial_schema.sql`

A migration é a versão definitiva do DDL da Etapa B. Ela não inclui `BEGIN` ou `COMMIT`, pois o Flyway controla a fronteira transacional.

### 3.1 UUID

A estratégia oficial do banco é:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
id uuid PRIMARY KEY DEFAULT gen_random_uuid()
```

O ambiente deve usar PostgreSQL 15 ou superior e conceder permissão para habilitar `pgcrypto`, ou disponibilizar a extensão previamente. Nas escritas do Hibernate, `GenerationType.UUID` gera o UUID antes do `INSERT`; o default PostgreSQL continua obrigatório para SQL direto, cargas e futuras integrações fora do ORM.

### 3.2 Ajustes da aprovação da Etapa B

Os seis ajustes estão materializados na migration:

1. `pgcrypto` explícito e `gen_random_uuid()` em todas as PKs;
2. índices únicos `lower(name)` em `iphone_model`, `device_color` e `part_catalog`;
3. `storage_gb IN (64, 128, 256, 512, 1024, 2048)`;
4. `battery_health_percent BETWEEN 0 AND 100`;
5. constraint triggers `DEFERRABLE INITIALLY DEFERRED` para 2–4 fotos ativas e pelo menos um item por manutenção;
6. `num_nonnulls(device_id, maintenance_id, sale_id)` igual a um para lançamentos operacionais e zero para tipos manuais.

O valor `battery_health_percent = 0` representa **indisponível/não aferido**. Ele não representa uma bateria com saúde real de 0% e não deve ser exibido como `0%` pela API/interface.

### 3.3 Fonte de verdade do schema

Flyway é a única ferramenta autorizada a criar ou evoluir o schema. O Hibernate usa:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

Isso detecta incompatibilidades de mapeamento sem permitir que o ORM altere tabelas, constraints ou índices.

## 4. Estrutura de pacotes

Pacote raiz: `io.github.bacelardev.iphoneresale`.

```text
io.github.bacelardev.iphoneresale
├── application
│   ├── dto
│   ├── port.security
│   └── service
├── config
├── domain
│   ├── enums
│   └── model
├── infrastructure
│   ├── persistence.repository
│   └── storage
└── web
    ├── controller
    ├── dto
    └── exception
```

Responsabilidades:

| Camada | Responsabilidade | Dependências permitidas |
| --- | --- | --- |
| `domain` | Estado do domínio, enums e invariantes locais. | Java/JPA; não depende da web ou de storage. |
| `application` | Casos de uso, transações, DTOs internos e ports. | Domínio e abstrações próprias. |
| `infrastructure` | JPA repositories, storage e integrações. | Application e domain. |
| `web` | REST, validação de entrada e tradução HTTP. | Application; entidades não são respostas da API. |
| `config` | Composição Spring, segurança e auditoria. | Todas apenas para wiring. |

Os `package-info.java` tornam explícita a intenção dos pacotes ainda vazios. Isso fixa a arquitetura sem antecipar código de etapas posteriores.

## 5. Enums

| Enum Java | Valores |
| --- | --- |
| `UserRole` | `SOCIO` |
| `DeviceStatus` | `PENDENTE_MANUTENCAO`, `DISPONIVEL_VENDA`, `VENDIDO` |
| `MaintenanceStatus` | `ACTIVE`, `CANCELLED` |
| `SaleStatus` | `ACTIVE`, `CANCELLED` |
| `FinancialDirection` | `INFLOW`, `OUTFLOW` |
| `FinancialTransactionType` | `OPENING_BALANCE`, compra/estorno, manutenção/estorno, venda/estorno, aporte, retirada e ajuste manual |
| `AuditAction` | Ações padronizadas do histórico. |
| `AuditedEntityType` | Tipos lógicos aceitos pelo histórico. |

Todos usam `@Enumerated(EnumType.STRING)`. Os nomes coincidem exatamente com os `CHECK` da migration; a ordem ordinal nunca é persistida.

## 6. Entidades e mapeamentos

| Entidade | Tabela | Base | Observação principal |
| --- | --- | --- | --- |
| `AppUser` | `app_user` | `AuditableEntity` | Usuário desativável; hash, nunca senha. |
| `IphoneModel` | `iphone_model` | `AuditableEntity` | Catálogo evolutivo. |
| `DeviceColor` | `device_color` | `AuditableEntity` | Catálogo global. |
| `PartCatalog` | `part_catalog` | `AuditableEntity` | Catálogo de peça/serviço. |
| `Device` | `device` | `AuditableEntity` | Código interno gerado pelo banco. |
| `DevicePhoto` | `device_photo` | `CreatedOnlyEntity` | Metadados imutáveis, exceto remoção lógica. |
| `Maintenance` | `maintenance` | `AuditableEntity` | Composição de itens; apenas `PERSIST`, sem cascade delete. |
| `MaintenanceItem` | `maintenance_item` | `BaseUuidEntity` | Imutável depois de confirmado. |
| `Sale` | `sale` | `AuditableEntity` | Dados econômicos imutáveis; cancelamento explícito. |
| `FinancialTransaction` | `financial_transaction` | `CreatedOnlyEntity` | `@Immutable`; ledger append-only. |
| `AuditLog` | `audit_log` | `BaseUuidEntity` | `@Immutable`; alterações em JSONB. |

### 6.1 Superclasses

- `BaseUuidEntity`: ID UUID, geração JPA compatível e igualdade segura para proxies Hibernate.
- `AuditableEntity`: criação, alteração, autores e `@Version`.
- `CreatedOnlyEntity`: criação e autor para registros append-only.

Associações são `LAZY` por padrão explícito. Não foi usado Lombok para manter o modelo legível, controlar mutações e evitar `equals`, `hashCode` ou `toString` perigosos em associações.

### 6.2 Integridade no JPA e no PostgreSQL

O mapeamento JPA expressa nulabilidade, comprimentos, precisão, escala, relacionamentos e imutabilidade. Regras que exigem índices funcionais, índices parciais, contagens entre linhas ou coordenação transacional permanecem na migration, que é a fonte de verdade.

Não foi configurado `CascadeType.REMOVE` nem `orphanRemoval = true`. Essa decisão corresponde às FKs `ON DELETE RESTRICT` e à preservação histórica aprovada.

## 7. Auditoria desde o início

`JpaAuditingConfiguration` habilita `@EnableJpaAuditing`. O port `CurrentUserIdProvider` desacopla a auditoria da futura implementação Spring Security. O `AuditorAware<AppUser>` converte o UUID autenticado em uma referência JPA sem consulta antecipada.

Até a autenticação ser implementada, um provider de fallback retorna vazio. Isso só atende bootstrap controlado de tabelas cuja autoria pode ser nula. As tabelas operacionais mantêm `created_by`/`updated_by NOT NULL` no PostgreSQL e não aceitam gravação anônima.

Além desses metadados, `audit_log` é a trilha de negócio imutável. O listener JPA não substitui a criação explícita de `AuditLog` em cada caso de uso.

## 8. Configuração operacional

- credenciais somente por `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`;
- fuso de persistência JDBC e JSON em UTC;
- fuso do negócio configurável, inicialmente `America/Bahia`;
- `open-in-view: false` para evitar consultas acidentais na camada web;
- PostgreSQL 16 no Compose, mantendo compatibilidade mínima declarada com PostgreSQL 15+;
- nenhum segredo real versionado; `.env.example` contém apenas valores locais de desenvolvimento.

## 9. Validação integrada realizada

A validação final foi executada em um ambiente isolado e compatível:

| Componente | Versão validada |
| --- | --- |
| JDK | Eclipse Temurin `21.0.12.1` LTS |
| Maven | `3.9.11` |
| PostgreSQL | `16.13` |
| Spring Boot | `3.5.0` |
| Flyway | `11.7.2` |
| Hibernate ORM | `6.6.15.Final` |

### 9.1 Resultado das verificações

| Verificação | Resultado | Evidência principal |
| --- | --- | --- |
| Compilação Java 21 | **Aprovada** | 36 arquivos compilados com `javac --release 21`. |
| `mvn clean verify` | **Aprovada** | `BUILD SUCCESS`; JAR executável gerado. O Surefire registrou `No tests to run`, coerente com o escopo desta etapa. |
| Banco vazio | **Aprovada** | Flyway identificou `Empty Schema`. |
| Execução da V1 | **Aprovada** | Uma migration validada e aplicada; schema ficou em `v1`. |
| Inicialização Spring Boot | **Aprovada** | Aplicação atingiu o estado `Started`. |
| `ddl-auto=validate` | **Aprovada** | `EntityManagerFactory` inicializada sem erro de schema. |
| `pgcrypto` | **Aprovada** | Extensão `pgcrypto` versão `1.3` instalada pela V1. |
| Estrutura física | **Aprovada** | As 11 tabelas de negócio foram encontradas. |
| Índices de catálogo | **Aprovada** | Os três índices únicos case-insensitive foram encontrados. |
| Checks solicitados | **Aprovada** | Capacidade, bateria e origem financeira presentes. |
| Triggers diferíveis | **Aprovada** | 11 constraint triggers, todas `DEFERRABLE` e `INITIALLY DEFERRED`. |

Também permaneceram aprovadas as verificações estáticas de correspondência entre tabelas, entidades, colunas e enums; presença dos seis ajustes; equivalência entre o DDL de referência e a migration; XML/YAML válidos; e integridade do pacote ZIP.

O primeiro JDK portátil obtido pelo instalador auxiliar apresentou uma imagem de módulos truncada. Ele foi descartado e substituído pela distribuição oficial Temurin, cujo checksum SHA-256 foi validado antes da extração. A compilação e as verificações acima foram executadas somente com a distribuição íntegra. Esse incidente não exigiu alteração no projeto.

## 10. Decisões da Etapa C aprovadas

| ID | Decisão | Consequência |
| --- | --- | --- |
| C-01 | Flyway é a fonte exclusiva do schema. | Hibernate apenas valida. |
| C-02 | UUID tem default PostgreSQL e geração antecipada no JPA. | SQL direto e ORM permanecem seguros. |
| C-03 | Enums são strings com `CHECK`. | Evolução explícita e sem ordinal. |
| C-04 | Associações são `LAZY`. | Evita carga e serialização acidental. |
| C-05 | Não há cascade delete. | Histórico permanece protegido. |
| C-06 | Ledger e auditoria usam `@Immutable`. | Correções são novos eventos. |
| C-07 | Auditor atual entra por um port da application. | Segurança não contamina o domínio. |
| C-08 | Entidades não são contratos REST. | DTOs serão definidos na etapa de casos de uso/API. |
| C-09 | Lombok não é usado no domínio. | Mutabilidade e identidade ficam explícitas. |
| C-10 | Não há seed de usuário/catálogos na V1. | Bootstrap será projetado com credenciais e dados aprovados, sem segredo na migration. |

## 11. Registro de encerramento

O usuário aprovou a migration definitiva, a estrutura de pacotes, os enums, as entidades, as superclasses e as decisões C-01 a C-10. As condições técnicas restantes foram satisfeitas pela validação integrada da seção 9.

Com isso, a **Etapa C versão 1.0 está aprovada e encerrada**. Nenhuma implementação de casos de uso, API ou frontend foi iniciada. A próxima fase permanece dependente de autorização explícita.
