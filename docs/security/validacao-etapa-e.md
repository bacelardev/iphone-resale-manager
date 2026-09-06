# Etapa E — Relatório de publicação e validação

Versão: **1.0 proposta**  
Status: **aguardando aprovação da Etapa E**  
Pendência de aceite: **E-05 — compatibilidade da faixa de senha**

## Resultado verificado

Em 06/09/2026, o código da Etapa E foi publicado no branch `main` do repositório
`bacelardev/iphone-resale-manager`. A autorização explícita do usuário permitiu criar
o commit e publicar backend, V2, testes, documentação e workflow.

- Implementação inicial: [`08acdd3`](https://github.com/bacelardev/iphone-resale-manager/commit/08acdd361aed32c310b043a57d721fc3d2c585ba).
- Código validado com correções: [`f31180c`](https://github.com/bacelardev/iphone-resale-manager/commit/f31180c0817a2bf46c6bcb5b77d581734a45a525).
- Execução: [Backend verify #34038974418](https://github.com/bacelardev/iphone-resale-manager/actions/runs/34038974418).
- Resultado do job `verify`: **success**.
- Comando: `mvn --batch-mode --no-transfer-progress clean verify`.
- Este relatório e sua referência na documentação são alterações documentais posteriores;
  não alteram o código validado no commit acima.

## Ambiente e gates

| Verificação | Evidência |
| --- | --- |
| JDK 21 | Eclipse Temurin 21.0.12.1 |
| Maven 3.9+ | Maven 3.9.16 |
| PostgreSQL real | PostgreSQL 16.15 em Testcontainers, imagem `postgres:16-alpine` |
| Flyway em banco vazio | V1 e V2 aplicadas; schema na versão 2 |
| Hibernate | Inicialização com `spring.jpa.hibernate.ddl-auto=validate` |
| Aplicação | Tomcat iniciou em portas HTTP reais durante os testes |
| Build | `BUILD SUCCESS` |
| Testes unitários | 17; zero falhas, erros ou ignorados |
| Testes de integração | 13; zero falhas, erros ou ignorados |
| Gate adicional | Workflow exige relatórios de integração e rejeita testes ignorados |
| Credencial automática | Autoconfiguração desabilitada; log final sem senha gerada |

O Maven local estava bloqueado por problemas de toolchain/cache e acesso às dependências.
A evidência de build, Flyway, startup e Hibernate acima vem do GitHub Actions, não de
uma execução local presumida. A checagem SQL direta anterior em PostgreSQL 16.13 também
verificou estrutura e restrições da V2, mas não foi usada como substituta do Flyway.

## Cobertura executada

| Suíte | Testes | Cobertura principal |
| --- | ---: | --- |
| `BCryptPasswordHashServiceTest` | 2 | Salt, custo 12, comparação e caracterização do limite de bytes |
| `SecurityPropertiesTest` | 3 | Wildcard CORS, TTL não positivo, representação sem segredo |
| `InMemoryLoginRateLimiterTest` | 2 | Limite, expiração, reinício e tamanho máximo |
| `SecureRandomAccessTokenGeneratorTest` | 1 | Formato, 256 bits e unicidade na amostra |
| `SpringSecurityCurrentUserIdProviderTest` | 2 | Principal autenticado, anônimo e estrangeiro |
| `Sha256AccessTokenHasherTest` | 1 | Vetor conhecido SHA-256 |
| `AuthenticateUserServiceTest` | 2 | Hash dummy e persistência somente do hash do token |
| `BootstrapFirstUserServiceTest` | 4 | Desabilitado, incompleto, usuário existente e criação normalizada |
| `AuthenticationFlowIT` | 11 | Flyway/JPA, autenticação, sessão, HTTP real, validação, CORS e headers |
| `LoginRateLimitIT` | 2 | 429, peer direto sem confiar em X-Forwarded-For e reinício após sucesso |

O fluxo real executado foi bootstrap → login → Bearer → `/auth/me` → logout →
401 na rota normal → logout repetido 204. Também foram verificados `no-store`,
ausência de cookie de sessão, rejeição de propriedades extras, normalização de username,
request ID inválido e reutilização do UUID válido para correlação.

As respostas 401 e 429 foram exercitadas; o caminho CORS proibido exercitou 403.
Isso não representa teste de uma futura matriz de autorização, que não faz parte do MVP.

## Ajustes da conferência

- Correção do conflito entre o accessor `allowed()` do record e a factory estática.
- Injeção explícita do construtor do rate limiter.
- Filtro Bearer registrado somente na cadeia de segurança.
- Credenciais Flyway e ciclo de vida dos contextos dos testes corrigidos.
- Exclusão da credencial automática do Spring Boot.
- Normalização de username antes da validação do request.
- Validação estrita de UUID no `X-Request-Id`, incluindo vazio e formato abreviado.
- Request de login com `toString()` redigido.
- Testes por HTTP real e verificação de toolchain no workflow.

## Integridade e exposição

A migration V1 permanece byte a byte igual à fundação aprovada:

```text
SHA-256 030304d45b973d114829265aca0db25301c9b4ec3934a0d86253adf4b1ade5b7
```

A comparação desde `d97208d` confirmou que nem a V1 nem o README alterado pelo usuário
foram incluídos nos commits da Etapa E. Não houve force push.

A inspeção dos arquivos alterados não encontrou credenciais produtivas, `.env`
versionado, segredo em `VITE_*` ou log de Authorization/body de login. As respostas
usam DTOs explícitos; o banco guarda SHA-256 do token e BCrypt da senha.
Essa inspeção não equivale a varredura completa do histórico ou de CVEs.

## Pendências e limites do aceite

**E-05 continua pendente.** BCrypt direto rejeita criação de hash para mais de 72 bytes
UTF-8. O contrato de 12–128 caracteres foi preservado, mas a implementação atual não
suporta toda essa faixa. O teste que caracteriza a rejeição é intencionalmente transparente:
um build verde não significa que o requisito de 128 caracteres está atendido.

A solução exige uma decisão explícita sobre compatibilidade do esquema de senha. Nenhum
pré-processamento criptográfico ou redução do contrato foi introduzido nesta entrega.

Além disso:

- Rate limit em memória é por processo e reinicia com a aplicação.
- TLS, rede privada e least privilege produtivos foram documentados; não houve deploy
  nem configuração de infraestrutura produtiva.
- Upload seguro é política futura documentada; storage e upload não foram implementados.
- Não há limpeza física de sessões, refresh token ou recuperação de senha.
- Não foram implementados casos de uso de negócio, API de negócio ou frontend.
- A Etapa F não foi iniciada.

## Arquivos da entrega

A implementação, os ajustes e este relatório totalizam 75 arquivos:
65 criados e 10 alterados. Os nomes completos abaixo permitem revisar o escopo.

### Criados

- `.github/workflows/backend-verify.yml`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/dto/auth/AuthSessionData.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/dto/auth/AuthUserData.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/dto/auth/CreateBootstrapUser.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/dto/auth/IssuedAccessToken.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/port/security/AccessTokenGenerator.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/port/security/AccessTokenHasher.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/port/security/AuthSessionStore.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/port/security/AuthUserStore.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/port/security/PasswordHashService.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/service/auth/AuthenticateAccessTokenService.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/service/auth/AuthenticateUserService.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/service/auth/AuthenticationFailedException.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/service/auth/BootstrapFirstUserService.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/service/auth/GetCurrentUserService.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/service/auth/LogoutUserService.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/service/auth/SessionRevocationService.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/application/service/auth/UnauthenticatedException.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/config/BootstrapConfiguration.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/config/SecurityConfiguration.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/config/properties/AuthProperties.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/config/properties/BootstrapProperties.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/config/properties/CorsProperties.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/persistence/repository/AppUserJpaRepository.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/AuthenticatedUserPrincipal.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/BCryptPasswordHashService.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/BearerTokenAuthenticationFilter.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/BearerTokenFormat.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/InMemoryLoginRateLimiter.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/RestAccessDeniedHandler.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/RestAuthenticationEntryPoint.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/SecureRandomAccessTokenGenerator.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/Sha256AccessTokenHasher.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/SpringSecurityCurrentUserIdProvider.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/persistence/AuthSessionEntity.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/persistence/AuthSessionJpaRepository.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/persistence/JpaAuthSessionStore.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/infrastructure/security/persistence/JpaAuthUserStore.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/controller/AuthController.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/dto/auth/LoginRequest.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/dto/auth/LoginResponse.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/dto/auth/UserResponse.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/dto/error/ApiErrorResponse.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/dto/error/FieldErrorResponse.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/exception/ApiErrorWriter.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/filter/LoginRateLimitFilter.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/filter/NoStoreFilter.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/filter/RequestIdFilter.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/web/filter/StrictCorsFilter.java`
- `backend/src/main/resources/db/migration/V2__opaque_auth_sessions.sql`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/application/service/auth/AuthenticateUserServiceTest.java`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/application/service/auth/BootstrapFirstUserServiceTest.java`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/infrastructure/security/BCryptPasswordHashServiceTest.java`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/infrastructure/security/InMemoryLoginRateLimiterTest.java`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/infrastructure/security/SecureRandomAccessTokenGeneratorTest.java`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/infrastructure/security/SecurityPropertiesTest.java`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/infrastructure/security/Sha256AccessTokenHasherTest.java`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/infrastructure/security/SpringSecurityCurrentUserIdProviderTest.java`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/web/AuthenticationFlowIT.java`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/web/LoginRateLimitIT.java`
- `backend/src/test/java/io/github/bacelardev/iphoneresale/web/PostgresIntegrationTest.java`
- `docs/security/etapa-e-seguranca-autenticacao.md`
- `docs/security/security-baseline.md`
- `docs/security/validacao-etapa-e.md`

### Alterados

- `CHANGELOG.md`
- `backend/.env.example`
- `backend/compose.yaml`
- `backend/pom.xml`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/IphoneResaleApplication.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/config/JpaAuditingConfiguration.java`
- `backend/src/main/java/io/github/bacelardev/iphoneresale/domain/model/AppUser.java`
- `backend/src/main/resources/application.yml`
- `docs/api/contratos-api.md`
- `docs/decisions/architecture-decisions.md`

## Decisões

E-01 a E-20 estão detalhadas em [segurança e autenticação](etapa-e-seguranca-autenticacao.md)
e no [registro arquitetural](../decisions/architecture-decisions.md).
O [security baseline](security-baseline.md) permanece obrigatório nas etapas seguintes.

A publicação e a validação técnica descritas estão concluídas. O encerramento da Etapa E
permanece condicionado à resolução de E-05 e à aprovação explícita do usuário.

