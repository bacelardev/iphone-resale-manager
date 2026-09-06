# Etapa E — Segurança, autenticação e hardening do backend

Versão: **1.0 proposta**  
Status: **aguardando aprovação da Etapa E**

## 1. Escopo entregue

A Etapa E implementa exclusivamente a fundação de autenticação e segurança do backend:

- `POST /api/v1/auth/login`;
- `GET /api/v1/auth/me`;
- `POST /api/v1/auth/logout`;
- bootstrap operacional do primeiro sócio;
- proteção default-deny das demais rotas;
- migration aditiva `V2__opaque_auth_sessions.sql`;
- hardening HTTP, CORS, validação, correlação e limitação de tentativas.

Não foram implementados CRUD de usuários, catálogos, aparelhos, manutenção, venda,
financeiro, storage, frontend, refresh token, OAuth, 2FA, Redis ou recursos da Etapa F.
A migration V1 permanece inalterada.

## 2. Decisões E-01 a E-20

### E-01 — Bearer token opaco

O acesso usa `Authorization: Bearer <access-token>`. O token não é JWT, não contém
claims nem informação legível de usuário ou negócio e não existe refresh token no MVP.
O prefixo público `irs_` apenas identifica o tipo da credencial; o cliente deve tratar o
valor completo como opaco.

### E-02 — Sessões persistidas

A V2 cria `auth_session` na infraestrutura de segurança. A tabela relaciona a sessão a
`app_user`, guarda somente o hash do token, registra criação, expiração e revogação e
usa `ON DELETE RESTRICT`. Sessões não são removidas: uma trigger permite apenas a
primeira transição de `revoked_at` de nulo para preenchido.

### E-03 — Expiração

O TTL padrão é de 12 horas e pode ser alterado por `APP_AUTH_TOKEN_TTL` em formato
`Duration`, com default `PT12H`. Duração nula, zero ou negativa falha na configuração.
Credencial expirada exige novo login.

### E-04 — Geração e hash do token

Cada token usa 32 bytes (256 bits) de `SecureRandom`, Base64 URL-safe sem padding e
prefixo `irs_`. Antes da persistência, o valor é transformado em SHA-256 hexadecimal
minúsculo de 64 caracteres. O valor bruto existe somente na memória da requisição de
login e na resposta correspondente; não é entidade, parâmetro de log ou dado do banco.

### E-05 — Senha com BCrypt

Senhas aceitas pelo contrato têm de 12 a 128 caracteres. A persistência recebe apenas
BCrypt com custo 12. Login de username inexistente executa comparação com um hash
BCrypt dummy gerado na inicialização, e username inexistente, senha errada e usuário
inativo produzem a mesma resposta pública `401 AUTHENTICATION_FAILED`.

**Pendência de aceite E-05:** o `BCryptPasswordEncoder` utilizado limita a criação do hash
a 72 **bytes UTF-8**, enquanto o contrato aceita até 128 caracteres. Portanto, esta
implementação direta não atende a toda a faixa aprovada: o bootstrap com senha acima
de 72 bytes falha. O teste `documentsTheOutstandingDirectBcryptByteLimit` caracteriza
essa limitação; sua aprovação não comprova suporte a 128 caracteres. A estratégia para
conciliar BCrypt e a faixa aprovada exige decisão explícita; não foi introduzido
pré-processamento criptográfico nem reduzido o contrato nesta entrega.

### E-06 — Spring Security stateless

A cadeia usa `SessionCreationPolicy.STATELESS`; form login, HTTP Basic, remember-me,
request cache e logout de sessão HTTP estão desabilitados. A aplicação não cria sessão
de servlet para autenticação.

A autoconfiguração `UserDetailsServiceAutoConfiguration` foi excluída para impedir a
criação e o log de credencial automática sem utilidade no fluxo Bearer. O filtro Bearer
é registrado apenas na cadeia Spring Security, sem registro duplicado no servlet.

### E-07 — Filtro Bearer

O filtro valida a forma da credencial e consulta uma sessão por hash. Autenticação só é
aceita quando a sessão existe, não foi revogada, não expirou e o usuário está ativo. Um
token ausente, malformado, desconhecido, revogado ou expirado retorna 401 nas rotas
normais.

### E-08 — Principal seguro e CurrentUserIdProvider

O principal contém somente `userId`, `username` e `role`. O
`SpringSecurityCurrentUserIdProvider` lê esse principal do `SecurityContext` e devolve
o UUID ao mecanismo de auditoria JPA. Senha, hash e token nunca entram no principal.

### E-09 — Login, `/me` e logout

Login recebe DTO validado e devolve `accessToken`, `tokenType`, `expiresAt` e um
`UserResponse` explícito. `/me` recarrega o usuário ativo pelo identificador autenticado.
Logout exige um Bearer sintaticamente válido: ausente ou malformado retorna 401;
conhecido e ativo é revogado; desconhecido, expirado ou já revogado retorna 204. Esse
comportamento reduz enumeração de sessões e torna a repetição segura.

### E-10 — Revogação

Logout preenche `revoked_at`, sem delete. O caso de uso reutilizável
`revokeAllSessionsForUser(UUID)` está disponível para futura troca de senha ou
desativação de usuário, mas esses fluxos não foram antecipados nesta etapa.

### E-11 — Rotas deny by default

Somente login e preflight CORS são públicos. Logout é liberado no matcher apenas para
que seu filtro aplique a semântica idempotente, mas continua exigindo Bearer válido na
forma. Qualquer outra rota exige autenticação por padrão.

### E-12 — 401 e 403 padronizados

Entry point, access-denied handler, CORS e advice usam o envelope aprovado com
`timestamp`, `status`, `code`, `message`, `path`, `requestId` e `fieldErrors`.
Respostas 401 incluem `WWW-Authenticate: Bearer realm="iphone-resale"` e nenhuma
resposta expõe causa interna, SQL, constraint ou stack trace.

### E-13 — Browser: CORS e CSRF

`APP_CORS_ALLOWED_ORIGINS` é uma allowlist exata; wildcard é rejeitado ao iniciar. São
aceitos apenas os métodos `GET`, `POST`, `PATCH`, `DELETE`, `OPTIONS` e os headers
`Authorization`, `Content-Type`, `X-Request-Id`. Credenciais CORS permanecem falsas.
CSRF está desabilitado porque a credencial não é cookie automático: o cliente a envia
explicitamente no header Bearer. CORS não substitui autenticação nem autorização.

### E-14 — Segredos e chaves

Credenciais entram somente por ambiente. `.env.example` contém placeholders locais e
não inclui segredo produtivo. Nada secreto pode usar prefixo `VITE_`: toda variável com
esse prefixo é incorporada ao bundle do navegador. Token, senha, hash, Authorization,
service-role key e credenciais de banco não podem ser registrados em logs.

### E-15 — Banco com tranca e least privilege

O Compose publica PostgreSQL apenas em `127.0.0.1`. A configuração admite usuário de
migration separado (`DB_MIGRATION_USERNAME`/`DB_MIGRATION_PASSWORD`) do usuário
runtime. Em produção, banco e aplicação devem permanecer em rede privada, exigir TLS,
restringir origem por firewall/security group e negar acesso direto de browser.

### E-16 — Rota não entrega dado indevido

Requests e responses são DTOs explícitos; nenhuma entidade JPA é serializada. O
`UserResponse` não contém `passwordHash`; a sessão não é exposta; propriedades JSON
desconhecidas falham. Toda resposta da API recebe `Cache-Control: no-store` e
`Pragma: no-cache`.

### E-17 — Input validado e queries seguras

Bean Validation controla presença, tamanho e formato do login. Username é normalizado
com `trim`, lowercase e `Locale.ROOT`. Repositories usam parâmetros nomeados/derivados,
sem concatenação de input. As etapas futuras devem manter allowlist de campos de sort e
escapar output pelo renderer do cliente; backend não considera sanitização de HTML uma
substituta para validação contextual.

### E-18 — Upload seguro como baseline

Esta etapa não implementa storage ou endpoint de upload. A política obrigatória futura
é: allowlist de MIME e extensão, inspeção de assinatura real do arquivo, limites por
arquivo e requisição, dimensões máximas, nomes de storage aleatórios, nome original
tratado apenas como metadado, rejeição de conteúdo ativo, reprocessamento seguro da
imagem quando aplicável e compensação transacional aprovada na Etapa D. Os limites
iniciais do multipart são configuráveis e não autorizam qualquer upload por si só.

### E-19 — Headers, logs e brute force

A API aplica `nosniff`, `DENY` para frames, CSP restritiva, Referrer-Policy,
Permissions-Policy e HSTS em conexões HTTPS, além de `no-store`. Logging de segurança
fica em INFO sem body/header sensível. `X-Request-Id` aceita UUID ou gera um; valor
inválido produz 400 com novo ID interno. Ele serve para correlação e auditoria, nunca
como idempotency key.

O login possui rate limit por endereço do peer direto: 5 tentativas por minuto por
default, janela expirável e mapa com no máximo 10.000 clientes. Sucesso limpa a janela.
`X-Forwarded-For` não é confiado; `server.forward-headers-strategy=none`. A proteção é
deliberadamente local ao processo e deve ser substituída por mecanismo distribuído se
houver mais de uma instância.

### E-20 — Testes como critério de aceite

Testes unitários cobrem entropia/formato, SHA-256, rate limit, principal/auditoria,
bootstrap e emissão de sessão. Testes de integração com PostgreSQL 16 via Testcontainers
cobrem V1+V2, bootstrap, login, respostas genéricas, hash persistido, `/me`, credenciais
inválidas/inativas/expiradas, logout e repetição, CORS, input desconhecido, request ID,
cache, headers e 429. A suíte de integração é desabilitada explicitamente apenas quando
Docker não existe; isso deve ser informado, nunca contado como execução bem-sucedida.

## 3. Migration V2

`auth_session` possui:

| Elemento | Definição |
| --- | --- |
| PK | `id uuid DEFAULT gen_random_uuid()` |
| FK | `user_id → app_user(id)`, update/delete `RESTRICT` |
| Segredo persistido | `token_hash varchar(64) UNIQUE`, regex hexadecimal SHA-256 |
| Tempo | `created_at`, `expires_at`, `revoked_at`, todos `timestamptz(6)` |
| Checks | expiração posterior à criação; revogação nula ou posterior à criação |
| Índices | hash único; sessões ativas por usuário/expiração; expirações ativas |
| Imutabilidade | sem delete; somente primeira revogação é mutável |

A V2 depende da extensão/função UUID já oficializada na V1; a V1 não foi modificada.

## 4. Bootstrap do primeiro usuário

O bootstrap é executado por `ApplicationRunner` dentro de transação:

1. se `APP_BOOTSTRAP_ENABLED=false`, não consulta nem altera usuários;
2. quando habilitado, nome, username e senha são validados antes de acesso ao banco;
3. um advisory lock transacional serializa inicializações simultâneas;
4. somente tabela `app_user` vazia permite criar um `SOCIO` ativo com BCrypt;
5. se já existe usuário, nada é alterado e senha nenhuma é resetada.

Após a criação, o operador deve desabilitar/remover as variáveis de bootstrap e reiniciar
a aplicação. Não existe endpoint público de cadastro nem senha default.

## 5. Configuração por ambiente

| Variável | Default local | Produção |
| --- | --- | --- |
| `APP_AUTH_TOKEN_TTL` | `PT12H` | duração positiva explícita |
| `APP_AUTH_LOGIN_MAX_ATTEMPTS` | `5` | calibrar com observabilidade |
| `APP_AUTH_LOGIN_WINDOW` | `PT1M` | calibrar com observabilidade |
| `APP_AUTH_LOGIN_MAX_TRACKED_CLIENTS` | `10000` | limitado; usar solução distribuída em cluster |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | origens HTTPS exatas, nunca `*` |
| `APP_BOOTSTRAP_ENABLED` | `false` | habilitar apenas no primeiro start |
| `DB_MIGRATION_USERNAME/PASSWORD` | mesmos dados locais | credencial DDL separada |
| `DB_USERNAME/PASSWORD` | desenvolvimento local | credencial runtime sem DDL |

## 6. Validação e limitações

Execução verificada no GitHub Actions: **30 testes, zero falhas, erros ou ignorados**,
com JDK 21, Maven 3.9.16, PostgreSQL 16.15, Flyway V1+V2, startup HTTP real e Hibernate
`validate`. Consulte o [relatório com evidências e arquivos](validacao-etapa-e.md).
Esse resultado não encerra a pendência de compatibilidade E-05.

O comando obrigatório é `mvn clean verify`. Os integration tests usam PostgreSQL 16
real e validam Flyway e `ddl-auto=validate`; não substituem PostgreSQL por H2.

Limitações assumidas nesta versão:

- E-05 permanece pendente para senhas acima de 72 bytes UTF-8, conforme detalhado acima;
- rate limit em memória não agrega tentativas entre réplicas e reinicia com o processo;
- não existe limpeza física de sessões; retenção/purga segura exige decisão futura e
  eventual evolução da trigger;
- não há rotação automática de credenciais, 2FA, OAuth ou refresh token;
- autorização do MVP possui somente o papel `SOCIO`;
- política de upload está documentada, mas storage/upload não foi implementado;
- confiança em reverse proxy permanece desligada até que proxies confiáveis sejam
  configurados explicitamente por ambiente.

## 7. Separação entre auditoria e security log

O ledger de auditoria de negócio não recebe eventos artificiais de autenticação nesta
etapa. Logs operacionais podem registrar sucesso/falha em nível agregado com request ID,
mas nunca senha, token, hash, Authorization ou body de login. `X-Request-Id` continua
sendo correlação, não idempotência.

## 8. Registro da entrega

Versão: **1.0 proposta**  
Status: **aguardando aprovação da Etapa E**

A aprovação e o encerramento só podem ser registrados após revisão explícita. Nenhum
trabalho da Etapa F foi iniciado.
