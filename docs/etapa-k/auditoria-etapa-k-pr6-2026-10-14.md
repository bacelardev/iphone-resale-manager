# Auditoria final da Etapa K — PR #6 (Delarte Control)

- Repositório: `bacelardev/iphone-resale-manager`
- Data da auditoria: 2026-10-14
- Auditor: Claude Code (sessão autônoma), a pedido de Bacelar
- Natureza: auditoria pós-integração, somente leitura. Nenhuma alteração no repositório, nenhum teste executado localmente, nenhum deploy ou recurso de produção tocado.

## 1. Resultado

**Aprovado tecnicamente, como auditoria pós-integração, com ressalvas não impeditivas.**

O PR #6 já estava integrado em `main` antes desta sessão (merge em 2026-10-13 04:33:49Z). O usuário confirmou que esse merge foi autorizado. A seção "merge após confirmação" do roteiro original ficou sem objeto. O usuário optou por relatório apenas, sem correções nesta execução.

## 2. PR, branches e SHAs

| Item | Valor |
| --- | --- |
| PR | #6 — `feat(stage-k): complete V1 production readiness` |
| Branch-base | `main` (em `4984b987a45974cf98318f755dc2037718c30320` no momento do merge) |
| Branch de origem | `codex/etapa-k-production-readiness` (preservada em `9c20e52`) |
| HEAD auditado | `9c20e52036c174ecb26511eee23c80efe18e8dd9` |
| Merge commit | `7ccedeb6c88bc56036e058edf23c2c9d2c46acb9` (pais `4984b98` + `9c20e52`) |
| Tree integrada | `d618e50fc86166bd9e0b39ea39ebf2878195b8c5`, idêntica à tree de `9c20e52` |
| Ready for review | 2026-10-13 04:33:32Z por `bacelardev` |
| Merge | 2026-10-13 04:33:49Z por `bacelardev` |
| Proteção de `main` | nenhuma; rulesets: nenhum |
| Revisões | 1 review "commented" do autor; 0 comentários de linha; 1 comentário de encerramento |
| Working tree local | limpa; `main` igual a `origin/main` |

Diferenças em relação ao relatório anterior: o HEAD mudou de `37cf300` para `9c20e52` (terceiro commit `test(stage-k): cover storage and user safeguards`, 2026-10-13 04:28Z, +373/−49, reorganizando `ImageKitGateway`, `ImageKitSdkGateway` e `ImageKitPhotoStorage` e adicionando `UserServiceTest` e `ImageKitPhotoStorageTest`); o PR deixou de ser Draft e foi integrado.

## 3. Resumo do diff (`4984b98..9c20e52`)

55 arquivos, +2970/−96. Todos pertinentes à Etapa K.

| Área | Arquivos principais |
| --- | --- |
| K-01 Auditoria | `AuditQueryService`, `AuditController`, `AuditEventResponse`, `AuditLogJpaRepository`, `HistoryPage.tsx`, `features/audit/api.ts` |
| K-02 Usuários | `UserService`, `UserController`, `CreateUserRequest`, `UpdateUserRequest`, `UserResponse`, `AppUser`, `AppUserJpaRepository`, `UsersPage.tsx`, `features/users/api.ts`, `types/stage-k.ts` |
| K-03 Storage | `ImageKitPhotoStorage`, `ImageKitGateway`, `ImageKitSdkGateway`, `PhotoValidator`, `ValidatedPhoto`, `ImageKitProperties`, `LocalFilePhotoStorage`, `DeviceService` (6 linhas), `pom.xml` (`io.imagekit:image-kit-java:3.5.0`) |
| K-04 Produção | `ProductionConfigurationValidator`, `HealthController`, `SecurityConfiguration` (1 linha), `application.yml`, `backend/Dockerfile`, `backend/.dockerignore`, `render.yaml`, `netlify.toml`, `.env.example` |
| K-05 Hardening | `GlobalExceptionHandler` (405), `SecurityConfiguration` |
| Docs | `docs/etapa-k/etapa-k-fechamento-v1.md`, `docs/production/etapa-k-readiness.md`, `docs/architecture/roadmap-implementacao.md`, `CHANGELOG.md` |
| Testes | `StageKFlowIT`, `UserServiceTest`, `ImageKitPhotoStorageTest`, `PhotoValidatorTest`, `ImageKitPropertiesTest`, `audit/api.test.ts`, `users/api.test.ts`, `e2e/stage-k.spec.ts`, ajustes de 5 linhas em `StageG/H/I/JFlowIT` |

Nenhum código temporário, duplicação ou alteração fora de escopo identificada.

## 4. SOCIO, ADMIN e OPERATOR

Hipótese confirmada: `SOCIO` é o único papel. Não há inconsistência a corrigir.

| Camada | Evidência |
| --- | --- |
| Enum Java | `domain/enums/UserRole.java` contém apenas `SOCIO` |
| Entidade | `AppUser.createSocio` fixa `SOCIO` |
| Banco | `V1__initial_schema.sql`: `DEFAULT 'SOCIO'` e `CHECK (role IN ('SOCIO'))`; nenhuma migration posterior altera |
| DTOs | `CreateUserRequest` e `UpdateUserRequest` sem campo `role`; `fail-on-unknown-properties: true` |
| Autorização | `anyRequest().authenticated()`; sem method security, coerente com papel único |
| Frontend | `types/stage-k.ts` `UserRole = 'SOCIO'`; `features/auth/api.ts` `z.literal('SOCIO')` |
| Testes e docs | apenas `SOCIO` / `ROLE_SOCIO` |
| ADMIN/OPERATOR | aparecem somente em `docs/etapa-k/etapa-k-fechamento-v1.md:16-19`, como divergência registrada |

## 5. Checks de CI por SHA

Todos os steps concluíram com sucesso, incluindo os gates "Require PostgreSQL integration tests" (zero skips no failsafe) e "Require browser tests without skips".

| SHA | Backend verify | Frontend verify |
| --- | --- | --- |
| `9c20e52` (HEAD) | #104 (push), #105 (pull_request) | #121 (pull_request) |
| `7ccedeb` (merge em main) | #106 | #122 |
| `37cf300` (HEAD anterior) | #103 | #120 |
| `7f03465` (1º commit) | #101, #102 | #117, #118 com falha, corrigida em `37cf300` |

Limitações de evidência:

- Logs do GitHub Actions retornam 403 sem autenticação. As contagens exatas (75 Vitest, 68 Playwright) e o aviso de bundle de 559 kB não puderam ser confirmados, apenas o sucesso de cada step.
- A máquina local não tem Maven, Node, Java 21 nem Docker ativo. Nenhum teste foi executado localmente. A evidência de testes é exclusivamente o CI, que cobre a mesma tree integrada.

## 6. Migrations V1–V6

Todas pré-existentes e inalteradas. O diff da pasta `db/migration` entre a `main` pré-K (`37609ec`) e a atual (`7ccedeb`) é vazio.

| Migration | Introduzida em |
| --- | --- |
| V1 initial_schema | `f4996fa`, 2026-09-03 |
| V2 opaque_auth_sessions | `08acdd3`, 2026-09-06 |
| V3 business_initialization | `3a2b854`, 2026-09-08 |
| V4 maintenance_cutover | `4884ac9`, 2026-09-10 |
| V5 sale_cutover | `0ae39fc`, 2026-09-11 |
| V6 financial_initialization | `59fd127`, 2026-09-14 (Etapa J) |

A Etapa K não criou migration. Flyway e `ddl-auto=validate` são exercitados no CI (backend verify com Testcontainers e frontend verify subindo o jar com PostgreSQL real).

## 7. Verificações de segurança e armazenamento

### Pontos verificados e adequados

- Endpoints públicos: apenas `OPTIONS /**`, `POST /auth/login`, `POST /auth/logout`, `GET /health` e `GET /device-photos/content/**` (este exige assinatura HMAC válida). Todo o resto, incluindo `/audit` e `/users`, exige bearer token. 401 sem token coberto em `StageKFlowIT`.
- K-01: sanitização recursiva de chaves em `changes` (mapas e coleções), sort allowlisted, page size 1–100, filtros tipados por enum/UUID/Instant, sem injeção.
- K-02: Argon2id (`Argon2PasswordHashService`, parâmetros OWASP), autodesativação bloqueada, sessões revogadas na desativação, troca de senha só própria, username normalizado e único com constraint no banco, versão otimista sob lock pessimista.
- K-03: upload privado (`isPrivateFile(true)`); frontend só recebe URL HMAC própria (`PhotoUrlSigner`, TTL 5 min, `MessageDigest.isEqual`); backend busca no ImageKit com URL assinada de 300 s e repassa os bytes; segredo apenas por variável de ambiente, redigido em `toString`; nenhum segredo real no repositório nem no histórico; `.gitignore` cobre `.env` e `.env.*`; validação por magic bytes JPEG/PNG/WebP, MIME declarado, extensão, tamanho 10 MiB, dimensões até 12.000 px e 40 MP, sem decodificação completa (proteção contra bomba de descompressão); fallback legado lê local primeiro e só então ImageKit, sem apagar o arquivo local; delete no ImageKit por `fileId` após commit.
- K-04: Dockerfile multi-stage com JDK 21 e usuário não root; `/api/v1/health` retorna apenas `{"status":"UP"}` com `no-store`; `ProductionConfigurationValidator` exige exatamente uma origem HTTPS sem path, bootstrap desligado, provider `imagekit` e `sslmode=require`; `render.yaml` com secrets `sync: false` e `autoDeployTrigger: off`; `netlify.toml` com SPA fallback e headers básicos; frontend rejeita API sem HTTPS em produção.
- K-05: erros sem stack trace nem mensagens internas; apenas dois pontos de log, nenhum com segredo; sem actuator; CORS allowlist exato que rejeita `*`; `X-Frame-Options`, CSP e `frame-ancestors 'none'` na API.

Nenhum teste foi executado contra o ImageKit real. A cobertura do adapter é unitária com gateway falso (`ImageKitPhotoStorageTest`); `ImageKitSdkGateway` não tem teste algum.

### Ressalvas (nenhuma impeditiva)

Prioridade 1, antes do primeiro deploy:

1. **Rate limiter de login contornável.** `LoginRateLimitFilter.java:41` usa `getRemoteAddr()`. O Render ativa `SERVER_FORWARD_HEADERS_STRATEGY=framework`, que faz o Spring confiar no `X-Forwarded-For` mais à esquerda, controlável pelo cliente. Sem limite por username. Requer decidir qual header o proxy do Render controla.
2. **Validador de produção tardio e sem testes.** `ProductionConfigurationValidator` é um `ApplicationRunner`: roda após Flyway e após o servidor aceitar requisições, com ordem indefinida em relação ao runner de bootstrap. Só ativo no perfil exatamente `prod`. A verificação de SSL é substring e rejeita `sslmode=verify-full`.
3. **Docs desatualizados.** `CHANGELOG.md:3-9` e `docs/architecture/roadmap-implementacao.md:116` ainda dizem "Draft PR aguardando revisão humana".

Prioridade 2:

4. **Auditoria oculta `passwordChanged`.** O denylist em `AuditQueryService.java:32-34` remove qualquer chave contendo "password"; `UserService.java:102` grava `passwordChanged: true` e `docs/api/casos-de-uso.md:144` espera esse registro.
5. **Hook de rollback apaga em `STATUS_UNKNOWN`.** `DeviceService.java:372` deleta o upload sempre que `status != STATUS_COMMITTED`; se a conexão cair durante um commit bem-sucedido, a linha aponta para arquivo apagado.
6. **Troca de senha sem senha atual e sem revogar outras sessões.** `UserService.java:92-97`, `UpdateUserRequest.java:8-11`. Um token roubado basta para tomar a conta. `newPassword` sem `@NotBlank` no update.
7. **Lacunas de teste.** Nenhum teste de URL expirada ou forjada (exigido pelas instruções da K-03); nenhum teste de `ImageKitSdkGateway`, nem com servidor HTTP falso; nenhum teste do validador de produção; nenhum teste unitário de `AuditQueryService`.
8. **Operação de fotos.** Remoção de foto agora apaga o conteúdo também no provider local (mudança de comportamento); falha de delete no ImageKit é logada sem `fileId`; cópias migradas de fotos legadas removidas não são apagadas no ImageKit; upload com `fileId` sem `filePath` não é limpo; o runbook não exige que arquivos migrados sejam privados; `APP_PHOTO_STORAGE_ROOT=/tmp` no Render torna o fallback local inútil; falhas 502 de storage não são logadas.

Prioridade 3:

9. Netlify sem CSP nem anti-framing (a API tem; token em `sessionStorage`).
10. Denylist de sanitização sem `apiKey`, `authorization`, `cookie`, `otp`.
11. Dockerfile sem `HEALTHCHECK` (o Render usa `healthCheckPath`); jar hard-coded `0.0.1-SNAPSHOT`.
12. 405 sem header `Allow`; `RuntimeException` do ImageIO vira 500 em vez de 415; EXIF não removido.
13. Cada leitura de foto baixa o objeto inteiro do ImageKit em memória, sem cache; uploads ocorrem dentro da transação segurando lock de linha.

## 8. Gatilhos de CI/CD

- Em `main` existem apenas `backend-verify.yml` e `frontend-verify.yml`: gatilhos `push` e `pull_request` com filtro de paths, `permissions: contents: read`, sem passo de deploy.
- Não há `workflow_run`, release, tag ou automação de publicação. Os workflows "Stage J format" listados pela API foram removidos do repositório em `6589e8b`.
- `render.yaml`: `autoDeployTrigger: off`. `netlify.toml`: sem configuração de deploy; nenhum site conectado segundo a documentação.
- O merge disparou apenas os runs de CI #106 e #122. Nenhum deploy ocorreu.

## 9. Aviso de bundle do Vite

`vite.config.ts` não define `manualChunks` e `App.tsx` importa todas as páginas estaticamente, então o aviso de cerca de 559 kB persiste. Não é impeditivo para uma aplicação interna autenticada. Melhoria futura: rotas com `React.lazy` e `Suspense`.

## 10. Problemas encontrados e correções realizadas

Nenhuma correção foi realizada, por decisão do usuário (relatório apenas). Nenhum achado é impeditivo. As ressalvas da seção 7 devem entrar em PRs de follow-up sobre `main`, nunca por push direto nem reescrita de histórico. Qualquer mudança de código só poderá ser validada pelo CI do novo PR, dado que não há toolchain local.

## 11. Pendências externas

- Credenciais reais e contas de ImageKit, Neon, Render, Netlify e UptimeRobot.
- Execução das migrations V1–V6 em staging no Neon e registro do resultado real.
- Upload, leitura assinada e delete reais no ImageKit, com migração não destrutiva das fotos locais e backup preservado.
- Build real no Render e no Netlify.
- Smoke tests pós-implantação, somente após autorização explícita de deploy.
- Monitor UptimeRobot apenas após smoke test aprovado.

## 12. Melhorias futuras não impeditivas

- Aviso de bundle (seção 9).
- Limiter compartilhado antes de múltiplas réplicas (documentado em `etapa-k-readiness.md`).
- Itens de prioridade 3 da seção 7.
- Filtros `userId` e `entityId` na tela de histórico; refresh do contexto de auth ao editar o próprio usuário; axe nas três larguras e com diálogo aberto; e2e para edição e ativação/desativação de usuários.

## 13. Situação atual e encerramento

- PR #6: integrado e fechado. Branch de origem `codex/etapa-k-production-readiness` preservada e recuperável.
- `main`: `7ccedeb`, CI verde, sem deploy.
- Ações restantes para encerramento formal: PR docs-only atualizando CHANGELOG, roadmap e etapa-k-fechamento para registrar a integração em `7ccedeb` sem deploy; PRs de follow-up para as ressalvas de prioridade 1 e 2.
- A conclusão formal da Etapa K não equivale a implantação concluída. Nenhuma etapa nova foi iniciada.
