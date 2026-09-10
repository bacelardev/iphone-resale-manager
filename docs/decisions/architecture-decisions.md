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

As decisões D-01 a D-15 foram aprovadas como versão final da Etapa D. A Etapa D está encerrada. Até esse encerramento, nenhuma implementação da Etapa E havia sido iniciada; a Etapa E começou somente após nova autorização explícita.

## Etapa E — Segurança, Autenticação e Hardening

Versão: **1.0 aprovada**  
Status: **Etapa E encerrada**

### E-01 — Bearer token opaco

**Decisão:** usar token opaco sem JWT, claims, payload de negócio ou refresh token.

### E-02 — Sessões persistidas

**Decisão:** a V2 cria `auth_session` em infraestrutura, guarda somente SHA-256 do token,
referencia `app_user` com `RESTRICT` e preserva histórico por revogação, não por delete.

### E-03 — Expiração

**Decisão:** sessões expiram em 12 horas por default, configuráveis por
`APP_AUTH_TOKEN_TTL`; duração não positiva falha no startup.

### E-04 — Geração e hash

**Decisão:** token tem 256 bits de `SecureRandom`, Base64 URL-safe sem padding e prefixo
`irs_`; somente seu SHA-256 hexadecimal é persistido.

### E-05 — Senha

**Decisão final autorizada:** senhas de 12–128 caracteres são armazenadas com Argon2id,
sem truncamento ou composição criptográfica própria. `PasswordHashService` desacopla
application; `Argon2PasswordHashService` usa `Argon2PasswordEncoder` (Spring Security
6.5.0) e Bouncy Castle `bcprov-jdk18on:1.80` na infraestrutura.

**Parâmetros:** Argon2id v=19, memória 19.456 KiB, 2 iterações, paralelismo 1, salt
aleatório de 16 bytes, hash de 32 bytes. Custos seguem o mínimo recomendado OWASP e
permanecem iguais nos testes. Encoding de 97 caracteres cabe em `password_hash
varchar(255)`; nenhuma migration adicional, V1 e V2 preservadas.

Username inexistente verifica dummy Argon2id com o mesmo custo, e inexistente, senha
incorreta ou usuário inativo recebem `401 AUTHENTICATION_FAILED` indistinguível.
Bootstrap mantém validação, advisory lock e criação somente na ausência de usuários,
sem reset. Preservada contagem UTF-16 Java de 12–128, sem limite artificial de bytes.
Detalhes e referências em `docs/security/etapa-e-seguranca-autenticacao.md`.

### E-06 — Stateless

**Decisão:** Spring Security não usa sessão HTTP, form login, Basic, remember-me,
request cache ou logout stateful.

### E-07 — Validação Bearer

**Decisão:** cada acesso valida forma, hash, existência, expiração, revogação e usuário
ativo antes de criar o SecurityContext.

### E-08 — Principal e auditoria

**Decisão:** principal contém apenas UUID, username e papel. A implementação real de
`CurrentUserIdProvider` integra esse UUID à auditoria JPA.

### E-09 — Contratos de autenticação

**Decisão:** implementar somente login, `/me` e logout. Logout ausente/malformado é 401;
token bem formado desconhecido, revogado ou expirado é 204; ativo é revogado e retorna 204.

### E-10 — Revogação

**Decisão:** preencher `revoked_at`, nunca apagar sessão, e manter operação reutilizável
de revogação de todas as sessões de um usuário.

### E-11 — Deny by default

**Decisão:** somente login e preflight são públicos. Logout possui liberação técnica para
a semântica especial do filtro; as demais rotas exigem autenticação por padrão.

### E-12 — Erros de segurança

**Decisão:** 401 e 403 usam o envelope comum; 401 inclui `WWW-Authenticate`; detalhes
internos e segredos não são enviados.

### E-13 — CORS e CSRF

**Decisão:** origens exatas, sem wildcard nem credentials; métodos e headers em allowlist.
CSRF fica desabilitado enquanto a credencial for Bearer explícita, e CORS não é autorização.

### E-14 — Segredos

**Decisão:** segredos existem somente no backend/ambiente e nunca em Git, logs, URL,
frontend ou `VITE_*`.

### E-15 — PostgreSQL e least privilege

**Decisão:** Compose publica em loopback; produção usa rede privada, TLS e credenciais
distintas de migration/runtime quando possível, com runtime sem DDL.

### E-16 — Exposição explícita

**Decisão:** entities não são responses, DTOs não carregam hashes/segredos, JSON extra é
rejeitado e respostas da API usam `no-store`.

### E-17 — Input e queries

**Decisão:** validar no servidor, normalizar username, parametrizar consultas e manter
allowlist de sort; não concatenar input em SQL/JPQL.

### E-18 — Upload futuro

**Decisão:** formalizar política de MIME, magic bytes, limites, dimensões, storage key
aleatória e conteúdo seguro, sem implementar storage nesta etapa.

### E-19 — Hardening operacional

**Decisão:** headers defensivos, logging sem dado sensível, request ID sem semântica de
idempotência e rate limit de login bounded/expirável por peer direto. Não confiar em
`X-Forwarded-For` sem proxy configurado; solução distribuída fica para escala horizontal.

### E-20 — Testes

**Decisão:** segurança só é aceita com testes negativos, PostgreSQL real, Flyway V1+V2,
startup e Hibernate validate. Execução não realizada ou teste pulado deve ser declarado.

## Registro de aprovação da Etapa E

As decisões E-01 a E-20 são finais. E-05 foi resolvida com Argon2id, conforme autorização
explícita. O commit `9f920c4` passou no workflow `34041521491`: 46 testes, nenhum erro ou
ignorado, PostgreSQL 16, Flyway V1+V2, startup e Hibernate validate. A Etapa E está
encerrada em versão 1.0 aprovada em 06/09/2026. Nenhuma implementação da Etapa F foi
iniciada; é necessária nova autorização para avançar.


## Etapa F — Fundação do frontend e autenticação

Versão: 1.0 aprovada  
Status: Etapa F encerrada

| ID | Tema | Decisão e consequência |
| --- | --- | --- |
| F-01 | Stack frontend | React 19, TypeScript estrito, Vite 8 e Tailwind 4; componentes internos no padrão shadcn/ui com Radix, Tabler, Query, Router, RHF e Zod. Node 24 LTS. Sem Redux/Next.js. |
| F-02 | Estrutura | app compõe providers/query; features/auth concentra autenticação; lib/api centraliza transporte; lib/auth isola token; layouts, pages, routes, types e components/ui possuem fronteiras simples. |
| F-03 | Design System | `visual-prototype-v2.svg` no desktop e `mobile-prototype-v1.svg` no mobile são o baseline oficial, subordinado a A–E, backend e segurança. Paleta #080808 / #101010 / #151515 / #1B1B1B, borda #262626, textos #F5F5F7 / #A1A1A6 / #6E6E73 e cores apenas semânticas. Motion 150/180/200–220ms e reduced-motion. |
| F-04 | Componentes | Button, Input, Select, Card, Badge, Dialog, Drawer, BottomSheet, Skeleton, Spinner, FormField, PageHeader, EmptyState e ErrorState em components/ui; Radix encapsulado. Select não contém catálogo inventado; Drawer é usado no App Shell e BottomSheet prepara o padrão mobile. |
| F-05 | Roteamento | /login e /dashboard funcionais. /devices, /devices/new, /devices/:id, /financial, /history e /settings/users protegidas com placeholders. Fallback vai ao dashboard, passando pelo guard. |
| F-06 | App Shell | Sidebar fixa desktop, header discreto e Drawer Radix no mobile, com navegação por links reais e identificação do sócio. A identidade é contínua entre breakpoints e não há indicador financeiro fictício. |
| F-07 | Cliente HTTP | apiRequest<T> concentra base URL, JSON/FormData, Bearer, UUID, 204, envelope de erro, rede, cancelamento e timeout 15s. Sem cookies, redirects ou cache HTTP; somente caminhos /api/v1 na origem configurada. |
| F-08 | ApiError | Envelope REST tipado e validado com Zod; lógica decide por code. Mensagens públicas mapeadas, sem renderizar causas internas ou payloads desconhecidos. Erro sem JSON recebe fallback seguro. |
| F-09 | Request ID | UUID novo por request; referência discreta em erros. Não é idempotência. Retry-After pode ser lido quando exposto pelo backend; mensagem 429 funciona sem depender desse header. |
| F-10 | sessionStorage | AuthTokenStore concentra get/set/clear. Apenas token opaco é persistido por aba. Não há localStorage, usuário persistido, persistência do cache Query ou refresh token. |
| F-11 | Segurança do token | Token somente no store e header Bearer; nunca URL, logs, console, analytics ou markup. Não há dangerouslySetInnerHTML, scripts externos, senhas no Query ou segredos VITE. sessionStorage não protege contra XSS. |
| F-12 | Login real | Composição central limpa e sem hero de marketing. RHF+Zod validam UX; POST real ao backend, guarda token, recebe DTO explícito e navega ao dashboard. Senha 12–128 unidades UTF-16 conforme backend, sem truncar ou normalizar. 401/429/rede/validação têm estados e botão sem resize. |
| F-13 | Restauração | Sem token, visitante; com token, GET /auth/me via Query sem retry automático. 200 restaura; 401 remove; falha de rede preserva token e oferece retry, sem mostrar conteúdo protegido. |
| F-14 | Estado de autenticação | AuthProvider/useAuth com checking/authenticated/unauthenticated/error, user, login, logout e retry. Contador de geração evita que resposta antiga restaure contexto encerrado. Efeitos compatíveis com StrictMode. |
| F-15 | ProtectedRoute | Guard impede flash durante checking e error; visitante vai ao login; autenticado entra. /login redireciona autenticado ao dashboard. Autorização de negócio continua no backend. |
| F-16 | Logout | POST real; finally remove token, usuário e queries privadas, cancelando consultas e redirecionando. Falha de rede encerra só a aba e informa que a sessão remota pode permanecer até expirar; sem promessa falsa de revogação. |
| F-17 | 401 global | Chamada protegida com 401 invalida o contexto atual, limpa cache e leva ao login. Login é exceção. Resposta associada a token antigo não invalida login novo; não há retry infinito. |
| F-18 | TanStack Query + forms | Query para estado do servidor (/me), sem credenciais nas chaves/cache. RHF+Zod para formulário, sem inserir senha/resposta com token no cache de mutation. Backend continua sendo a validação final. |
| F-19 | Responsividade + acessibilidade | 375, 430, 768, 1024 e 1440px; labels, alvos de 44px, foco visível, skip link, teclado, trap/restauração de foco Radix, sem overflow e reduced-motion. Playwright + axe verificam WCAG A/AA; não equivalem a auditoria assistiva completa. |
| F-20 | Qualidade, testes e CI | ESLint 10, Prettier, Husky/lint-staged, Vitest/RTL e workflow frontend separado: npm ci, lint, format, typecheck, tests, build e Playwright contra Spring Boot/PostgreSQL reais. Gate exige pelo menos 11 E2E sem skips, incluindo tokens e motion do baseline. Backend CI preservado. |

Implementação e critérios de validação em `docs/frontend/etapa-f-fundacao-frontend.md`.
A evidência principal da versão final é o commit
`5c4546658f19e7cb24dc5f3799edbb6f5ea12a61`, aprovado com status `success` no
workflow `34258007670`: 33/33 testes Vitest em 5 arquivos e 11/11 testes Playwright,
sem falhas ou skips, com PostgreSQL 16, Flyway V1+V2, Hibernate `ddl-auto=validate`,
backend real, autenticação/CORS, axe e as cinco larguras responsivas. O complemento
visual oficial permanece versionado em `docs/design/prototypes/`.
A Etapa F está encerrada em versão 1.0 aprovada. As decisões A–E, security baseline e
migrations permanecem preservadas. Não foi iniciada a Etapa G.


## Decisões da Etapa G — versão 1.0 aprovada

- **G-01:** implantação possui lifecycle `PREPARING|COMPLETED`; somente `PREPARING` é usado na G.
- **G-02:** `cutoffAt` é persistido antes de qualquer importação.
- **G-03:** o cutoff fica bloqueado após o primeiro `INITIAL_IMPORT`.
- **G-04:** importação inicial preserva custo e não cria ledger de compra.
- **G-05:** cadastro operacional cria `DEVICE_PURCHASE / OUTFLOW` atomicamente.
- **G-06:** `registrationOrigin` é definido pelo endpoint e imutável.
- **G-07:** V3 é aditiva; V1 e V2 permanecem byte a byte fora do escopo.
- **G-08:** `maintenance.registration_origin` é schema/JPA-only na G; comportamento pertence à H.
- **G-09:** `owner_user_id` e `owner_capital_opening` são schema/JPA-only; comportamento pertence à J.
- **G-10:** modelos e cores são catálogos reais, versionados e sem hard delete.
- **G-11:** CatalogPicker usa catálogo real e combobox pesquisável dentro de Dialog/BottomSheet acessível, com focus trap, Escape, restauração de foco e teclado.
- **G-12:** fotos usam storage externo ao banco, chave aleatória e URL HMAC temporária.
- **G-13:** cada aparelho mantém de 2 a 4 fotos ativas com validação de conteúdo.
- **G-14:** listagens aplicam paginação, filtros e sort allowlist.
- **G-15:** locks, constraints e `expectedVersion` protegem concorrência.
- **G-16:** operações relevantes geram auditoria legível sem segredos.
- **G-17:** a UI preserva baseline V2/Mobile-first e funciona de 375 a 1440 px.
- **G-18:** fluxos de estoque atendem axe/WCAG 2 AA e reduced motion.
- **G-19:** CI valida PostgreSQL 16, Flyway V1+V2+V3, Hibernate, unitários, integração e navegador real.
- **G-20:** conclusão da implantação, manutenção funcional, venda e financeiro não são antecipados; a Etapa H exige nova autorização.

**Versão:** 1.0 aprovada  
**Status:** Etapa G encerrada


## Registro de aprovação da Etapa G

As decisões G-01 a G-20 são finais. O head técnico `2c56075a` e o fechamento `4525b092` foram aprovados após validação integral. A Etapa G está encerrada em versão 1.0 aprovada em 09/09/2026. `maintenance.registration_origin` permanece somente como preparação de schema/JPA; nenhuma funcionalidade da Etapa H foi iniciada.

## Decisões da Etapa H — versão 1.0 proposta

- **H-01:** manutenção operacional com total positivo cria `MAINTENANCE/OUTFLOW` atomicamente.
- **H-02:** manutenção operacional de custo zero não cria ledger.
- **H-03:** manutenção `INITIAL_IMPORT` nunca cria ledger, ainda que possua custo.
- **H-04:** nova importação histórica existe somente durante `PREPARING`.
- **H-05:** manutenção histórica só pode pertencer a aparelho `INITIAL_IMPORT`.
- **H-06:** com implantação persistida, manutenção operacional exige `performedAt > cutoffAt`.
- **H-07:** manutenção histórica exige `purchasedAt <= performedAt <= cutoffAt`.
- **H-08:** `registrationOrigin` é derivada do endpoint e imutável.
- **H-09:** manutenção e itens são imutáveis; correção ocorre por cancelamento e novo registro.
- **H-10:** peça reservada `OTHER` exige detalhes com 1–255 caracteres após trim.
- **H-11:** total é calculado no backend; preview do cliente não é fonte de verdade.
- **H-12:** cancelamento operacional positivo cria `MAINTENANCE_REVERSAL/INFLOW`.
- **H-13:** cancelamento histórico ou operacional zero não cria reversão.
- **H-14:** preview incorpora custos ativos `INITIAL_IMPORT` em `maintenanceCapital`.
- **H-15:** arquivamento cancela manutenções e reversões necessárias atomicamente.
- **H-16:** peças são catálogo real, versionado, pesquisável e sem hard delete.
- **H-17:** nenhum seed de peças é criado sem decisão explícita de dados oficiais.
- **H-18:** UI preserva os baselines V2/Mobile-first e reutiliza Dialog/BottomSheet acessível.
- **H-19:** gates usam PostgreSQL 16, Flyway V1–V4, Hibernate validate, Vitest, Playwright e axe.
- **H-20:** venda e qualquer funcionalidade da Etapa I não foram iniciadas.

Interpretações registradas: V4 é obrigatória para a última barreira de cutover; locks
seguem aparelho → implantação → manutenções ordenadas; cancelamentos automáticos são
resumidos na auditoria principal de arquivamento; PartPicker adapta o modal aprovado da G.

**Versão:** 1.0 proposta

**Status:** aguardando aprovação da Etapa H
