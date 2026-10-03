# Etapa K --- Instruções de Implementação

## Delarte Control --- Fechamento da V1

**Base obrigatória:** `main`\
**SHA:** `37609eccfa5a159f7c2cd94777b74892cf4b231a`

A auditoria da main concluiu que o produto está quase funcional, mas
ainda não pronto para implantação. Os gaps obrigatórios são:
histórico/auditoria consultável, gestão de usuários, migração do storage
local para ImageKit, readiness de produção para
Render/Netlify/Neon/UptimeRobot e hardening/evidências finais.
fileciteturn400file0L7-L23

## 1. Regras de execução

-   Criar branch própria da K.
-   Criar PR draft.
-   Não fazer merge automático.
-   Não fazer deploy de produção durante a K.
-   Não iniciar Etapa L.
-   Não modificar migrations V1--V6.
-   Preservar contratos e decisões das Etapas A--J.
-   Preservar V2 + Mobile-first.
-   Preservar **Delarte Control** e **Desenvolvido por Andelar**.

A Etapa K é fechamento da V1, não expansão do produto.

## 2. K-01 --- Histórico e auditoria

Implementar os contratos existentes:

-   `GET /audit`
-   `GET /audit/{id}`

E tornar `/history` funcional.

### Backend

Implementar repository/service/controller/DTOs de leitura, com filtros,
paginação, ordenação allowlisted quando prevista e
autenticação/autorização.

O DTO deve ser explícito. Nunca retornar:

-   hash de senha;
-   token/hash de sessão;
-   secrets;
-   credenciais;
-   dados internos desnecessários.

O `audit_log` continua append-only.

### Frontend

`/history` deve ter:

-   filtros;
-   paginação;
-   detalhe;
-   loading;
-   vazio;
-   erro;
-   mobile;
-   teclado;
-   foco;
-   Escape;
-   axe/WCAG 2 AA;
-   baseline V2 + Mobile-first.

### Testes

Cobrir autenticação, autorização, filtros, paginação, detalhe, 404,
`requestId`, ausência de dados sensíveis, somente leitura, estados da
UI, mobile e axe.

## 3. K-02 --- Gestão de usuários

Implementar os contratos documentados para:

-   criar;
-   listar;
-   consultar;
-   editar;
-   ativar;
-   desativar.

Tornar `/settings/users` funcional.

Preservar:

-   papel `SOCIO`;
-   Argon2id;
-   versionamento/concurrency control;
-   proteção do último sócio ativo;
-   regras de autodesativação;
-   invalidação de sessão de conta desativada;
-   proteção contra enumeração;
-   nunca expor hash/token.

### Testes

Cobrir CRUD, ativação/desativação, Argon2id, autorização, concorrência,
último sócio, autodesativação e sessão após desativação, além dos fluxos
frontend/E2E.

## 4. K-03 --- ImageKit / Storage

O storage atual é `LocalFilePhotoStorage` e não é adequado para o
Render. fileciteturn400file0L44-L52

Criar adaptador `PhotoStorage` para ImageKit, preservando a abstração
existente.

Requisitos:

-   credenciais somente no backend;
-   configuração por env;
-   upload/removal seguros;
-   URLs temporárias/assinadas compatíveis;
-   tratamento de falha;
-   rollback/compensação;
-   estratégia segura para fotos existentes;
-   nenhuma migração destrutiva.

Preservar e reforçar validação de tamanho, MIME, extensão e magic bytes;
quando compatível, validar/decodear imagem e limitar dimensões/pixels.

Testar upload válido, formatos, arquivos malformados/oversized, URL
expirada, remoção, falha do provider, compensação e ausência de segredo
no frontend.

## 5. K-04 --- Readiness de produção

Infraestrutura oficial:

``` text
Frontend  → Netlify
Backend   → Render
Database  → Neon PostgreSQL
Storage   → ImageKit
Monitor   → UptimeRobot
CI/CD     → GitHub Actions
```

A auditoria identificou que Render/Netlify/ImageKit/UptimeRobot ainda
não possuem configuração operacional reprodutível.
fileciteturn400file0L118-L127

### Render

Preparar o necessário entre:

-   Dockerfile;
-   render.yaml;
-   start;
-   healthcheck;
-   env vars;
-   documentação.

Não criar configuração redundante sem necessidade.

### Healthcheck

Criar endpoint público mínimo:

-   sem autenticação;
-   sem dados sensíveis;
-   sem stack trace;
-   estável para monitoramento.

### Netlify

Preparar:

-   Vite build;
-   `VITE_API_BASE_URL`;
-   SPA fallback/rewrite;
-   env vars;
-   origem HTTPS.

Criar `netlify.toml` somente se necessário.

### Neon

Documentar:

-   PostgreSQL;
-   SSL;
-   runtime credentials;
-   migration credentials;
-   Flyway.

Não inserir credenciais no Git.

### CORS

Produção deve aceitar somente a origem HTTPS real do Netlify.

### Bootstrap

Documentar e garantir desativação após o primeiro sócio.

### UptimeRobot

Preparar documentação da URL de healthcheck. Não é necessário criar
integração proprietária.

## 6. K-05 --- Hardening e evidência

Revisar:

-   autenticação;
-   autorização;
-   CORS;
-   headers;
-   secrets;
-   logs;
-   erros;
-   `X-Request-Id`;
-   uploads;
-   URLs assinadas;
-   storage;
-   inputs;
-   concorrência;
-   acesso a recursos;
-   dados sensíveis.

### Rate limit

O `InMemoryLoginRateLimiter` é suficiente para uma única instância, mas
não compartilha estado entre réplicas. fileciteturn400file0L59-L67

Decidir/documentar a topologia:

-   uma instância: manter e registrar limitação;
-   múltiplas instâncias: mecanismo compartilhado apropriado.

Não adicionar infraestrutura distribuída sem necessidade.

### Documentação

Atualizar referências que ainda dizem que a J está "proposta aguardando
aprovação". A J já está mergeada.

## 7. Banco de dados

**Não modificar V1--V6.**

Se K realmente exigir banco:

-   nova migration aditiva;
-   Flyway;
-   `ddl-auto=validate`;
-   nunca editar migrations antigas.

A auditoria confirmou que V1--V6 devem permanecer preservadas.
fileciteturn400file0L85-L91

## 8. Fora do escopo

Não implementar:

-   CRM;
-   clientes/compradores;
-   WhatsApp;
-   marketplace;
-   parcelamento;
-   emissão fiscal;
-   garantia;
-   comissão;
-   multiempresa/multifilial;
-   aplicativo mobile nativo;
-   analytics avançado;
-   pagamentos online;
-   controle de funcionários;
-   RBAC adicional;
-   novas regras comerciais;
-   recuperação complexa de senha;
-   idempotência global baseada em `X-Request-Id`.

## 9. Critérios de aceite

### Auditoria

-   [ ] `/audit` funcional.
-   [ ] `/audit/{id}` funcional.
-   [ ] filtros/paginação.
-   [ ] DTO seguro.
-   [ ] somente leitura.
-   [ ] `/history` funcional.
-   [ ] mobile/acessível.

### Usuários

-   [ ] CRUD.
-   [ ] ativação/desativação.
-   [ ] proteção do último sócio.
-   [ ] regras de autodesativação.
-   [ ] concorrência.
-   [ ] Argon2id.
-   [ ] nenhuma exposição de hash/token.
-   [ ] `/settings/users` funcional.
-   [ ] mobile/acessível.

### Storage

-   [ ] ImageKit.
-   [ ] segredo somente backend.
-   [ ] upload/removal.
-   [ ] URLs temporárias.
-   [ ] falhas/rollback.
-   [ ] fotos existentes tratadas.
-   [ ] arquivos inválidos/malformados testados.

### Produção

-   [ ] Render.
-   [ ] Netlify.
-   [ ] Neon.
-   [ ] ImageKit.
-   [ ] healthcheck.
-   [ ] UptimeRobot.
-   [ ] SPA fallback.
-   [ ] CORS correto.
-   [ ] secrets fora do Git.
-   [ ] bootstrap desativável.

### Regressão

-   [ ] Backend verde.
-   [ ] Frontend verde.
-   [ ] Vitest verde.
-   [ ] Playwright verde.
-   [ ] axe verde.
-   [ ] PostgreSQL/Testcontainers verde.
-   [ ] Flyway verde.
-   [ ] Hibernate Validate verde.
-   [ ] zero skips.
-   [ ] zero flaky.
-   [ ] G--J preservadas.

## 10. Ordem de implementação

1.  K-01 --- auditoria.
2.  K-02 --- usuários.
3.  K-03 --- ImageKit/storage.
4.  K-04 --- readiness de produção.
5.  K-05 --- hardening, documentação e evidências.

A auditoria recomenda fechar K-03 antes de qualquer deploy e somente
depois concluir K-04/K-05. fileciteturn400file0L168-L175

## 11. Deploy

Durante a K:

**NÃO fazer deploy de produção.**

Após aprovação humana:

1.  merge da K;
2.  validação da `main`;
3.  preparação final da infraestrutura;
4.  deploy controlado;
5.  smoke test;
6.  monitoramento;
7.  declaração da V1.0.

## 12. Instrução final ao antigravity

Implemente somente o escopo deste documento.

Antes de cada alteração:

1.  ler a documentação correspondente;
2.  localizar a implementação existente;
3.  preservar contratos;
4.  preservar decisões A--J;
5.  evitar duplicação;
6.  evitar refatorações não necessárias;
7.  adicionar testes;
8.  validar regressões.

Ao concluir:

-   não fazer merge;
-   não fazer deploy;
-   não iniciar Etapa L;
-   manter PR draft;
-   apresentar evidências completas;
-   informar limitações e decisões.

**Resultado esperado: Etapa K --- versão 1.0 proposta, aguardando
revisão e aprovação humana.**
