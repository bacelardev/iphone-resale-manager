# Security baseline do projeto

Baseline aprovado no encerramento da Etapa E, versão 1.0, em 06/09/2026.
E-05 validada com Argon2id no workflow `34041521491`.

Este documento é obrigatório para todas as etapas posteriores. Uma funcionalidade só
está pronta quando mantém os controles aplicáveis abaixo e possui testes negativos.

## Cinco riscos principais

| Risco | Proteção obrigatória |
| --- | --- |
| Banco sem tranca | Rede privada, TLS, firewall, PostgreSQL local apenas em loopback, usuário runtime com least privilege e credencial de migration separada quando possível. Browser nunca acessa banco diretamente. |
| Permissão insegura no navegador | CORS por allowlist exata e sem credenciais, CSRF coerente com Bearer em header, headers defensivos. CORS nunca é autorização. |
| Rota entregando dado indevido | Default-deny, autorização no backend, DTOs explícitos, nunca serializar entity e nunca retornar senha, hash, token de sessão ou campo interno. |
| Chave ou segredo exposto | Segredos somente no backend/ambiente; `.env` não versionado; nenhum segredo em `VITE_*`, JavaScript, logs, URLs ou Git. Exposição exige revogação/rotação. |
| Input sem tratamento | Bean Validation, DTOs fechados, propriedades desconhecidas rejeitadas, queries parametrizadas, sort em allowlist e upload validado por conteúdo. |

## Controles permanentes

### Autenticação e autorização

- Negar por padrão e liberar publicamente só a rota explicitamente documentada.
- Validar sessão, expiração, revogação e usuário ativo em toda requisição protegida.
- Persistir somente hash SHA-256 do token opaco; usar Argon2id para senha de 12–128
  caracteres, sem truncamento ou pré-hash próprio. Parâmetros E-05: 19.456 KiB, 2
  iterações, paralelismo 1, salt aleatório de 16 bytes e hash de 32 bytes.
- Manter `PasswordHashService` entre application e infraestrutura. Usuário inexistente
  verifica dummy Argon2id de mesmo custo; falhas inexistente/incorreta/inativa usam
  `401 AUTHENTICATION_FAILED`. Hash codificado de 97 caracteres cabe em `varchar(255)`.
- Revogar sessões em vez de apagá-las e oferecer revogação total por usuário.
- Não transformar `X-Request-Id` em idempotência; mecanismo futuro usa decisão própria.

### Exposição, cache e logs

- Requests/responses são DTOs; entities não atravessam controllers.
- Respostas autenticadas, tokens e erros usam `Cache-Control: no-store`.
- Nunca registrar `Authorization`, cookie, senha, token, hash, body de login ou segredo.
- Erros públicos não mostram SQL, constraint, stack trace, hostname ou configuração.
- Logs devem usar request ID e eventos estruturados sem dados sensíveis.

### Banco e produção

- Runtime recebe somente `CONNECT`, `USAGE`, DML estritamente necessário e acesso às
  sequências; não recebe `CREATE`, `ALTER`, `DROP`, ownership ou superuser.
- Migration usa credencial separada e controlada pelo pipeline quando disponível.
- Banco produtivo aceita apenas rede da aplicação, exige TLS e não publica porta pública.
- Backup, restore e rotação de credenciais devem ser ensaiados antes do go-live.

Exemplo orientativo, a adaptar pelo operador:

```sql
GRANT CONNECT ON DATABASE iphone_resale TO iphone_resale_runtime;
GRANT USAGE ON SCHEMA public TO iphone_resale_runtime;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO iphone_resale_runtime;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO iphone_resale_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE iphone_resale_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE ON TABLES TO iphone_resale_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE iphone_resale_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO iphone_resale_runtime;
```

### Browser e frontend

- Produção usa somente origens HTTPS exatas; wildcard é proibido.
- `Access-Control-Allow-Credentials` permanece ausente/falso no modelo Bearer atual.
- Nenhuma variável `VITE_*` contém segredo. Chaves públicas devem ser tratadas como
  públicas mesmo quando possuem a palavra “key”.
- Token fica apenas no mecanismo de armazenamento aprovado para cada cliente. No frontend
  web da Etapa F, isso significa `AuthTokenStore` sobre `sessionStorage`; `localStorage`
  e caches persistentes permanecem proibidos.

### Input, consultas e upload

- Rejeitar JSON desconhecido e limitar tamanho, formato e cardinalidade no servidor.
- Nunca concatenar input em JPQL/SQL. Campos de ordenação usam allowlist por recurso.
- Upload futuro valida extensão, MIME declarado, magic bytes, tamanho e dimensões;
  gera storage key aleatória; não executa conteúdo; não confia no nome original.
- Renderização web deve escapar conteúdo por contexto. Não armazenar HTML quando texto
  simples resolve o caso de uso.

### Dependências e configuração

- Fixar versões via BOM do Spring Boot e revisar alertas de dependência continuamente.
  Bouncy Castle `bcprov-jdk18on:1.80` tem versão explícita alinhada ao Spring Security
  6.5.0, pois o BOM do Boot 3.5.0 não a gerencia.
- Configuração inválida deve falhar de modo seguro no startup.
- Separar defaults de desenvolvimento das exigências de produção.
- Não habilitar debug de Spring Security, dump de headers/body ou SQL em produção.

### Testes mínimos por mudança

- caminho feliz e credencial ausente, malformada, desconhecida, expirada e revogada;
- usuário inativo e resposta indistinguível para falhas de login;
- autorização negada, CORS permitido/proibido e CSRF conforme o modelo vigente;
- propriedades JSON extras, limites, request ID inválido e ausência de campo secreto;
- rate limit, cache, headers e consultas parametrizadas;
- migration sobre PostgreSQL vazio, startup e `hibernate.ddl-auto=validate`;
- busca manual por segredo antes de cada entrega.

## Resposta a incidente de segredo

Se um segredo real for versionado, não o repita em issue, chat ou log. Remova o uso,
revogue/rotacione imediatamente, investigue acessos e trate o histórico como exposto.
Apagar apenas o arquivo ou o commit mais recente não invalida a credencial vazada.


## Fundação frontend — regras permanentes F-10 a F-17

- Token opaco somente por `AuthTokenStore` em `sessionStorage`; nunca localStorage,
  URL, console, analytics, markup ou cache persistido. Senha não entra no Query.
- `sessionStorage` é acessível a scripts da mesma origem e não protege contra XSS.
  Preservar escaping React, limitar scripts e aplicar CSP/HTTPS no deploy.
- Cliente HTTP único com origem explícita, sem credenciais em VITE, sem cookies e sem
  redirecionamentos de requests autenticados para outros destinos.
- 401 protegido limpa sessão e cache privado; 401 de login é erro de credencial.
  Falha de rede na restauração preserva token e permite retry, sem liberar conteúdo.
- Logout com rede indisponível limpa a aba, mas não promete revogação no servidor.
  Respostas atrasadas nunca podem restabelecer sessão encerrada ou invalidar login novo.


## Extensão permanente da baseline — Etapa G

- Todos os endpoints de G exigem Bearer autenticado com papel `SOCIO`.
- Upload aceita somente JPEG/PNG/WebP, até 10 MiB, validando MIME declarado e magic bytes.
- Binários permanecem fora do PostgreSQL; storage keys são aleatórias e nunca aparecem em responses.
- URLs de mídia são temporárias e assinadas; o cliente só resolve caminhos dentro de `/api/v1/device-photos/content/`.
- Falhas SQL após upload executam compensação; remoção é lógica e respeita o mínimo de duas fotos.
- Filtros e ordenação usam allowlists; DTOs rejeitam campos desconhecidos e não expõem entidades.
- `expectedVersion`, locks transacionais, constraints e advisory lock protegem mudanças concorrentes.
- Segredo de assinatura vem de variável server-side `APP_PHOTO_STORAGE_SIGNING_SECRET`, nunca de `VITE_*`, logs ou respostas.

## Extensão permanente da baseline — proposta da Etapa H

- Endpoints de peças e manutenções permanecem default-deny e exigem Bearer `SOCIO`.
- Requests fechados não aceitam total, status, origem, responsável, posição ou IDs de
  ledger; responses usam DTOs e não expõem entidades ou transações internas.
- Peças, UUIDs, custos, detalhes, intervalos e sort passam por Bean Validation, queries
  parametrizadas e allowlists; `OTHER` exige texto simples limitado após trim.
- `expectedVersion`, locks pessimistas e ordem canônica protegem cancelamento,
  arquivamento e futuras disputas com venda.
- V4 duplica no PostgreSQL as invariantes críticas de origem/cutoff sem alterar V1–V3.
- Ledger e auditoria permanecem append-only; reversões referenciam o original e nenhum
  evento inclui token, segredo, chave de storage ou detalhe SQL.
- PartPicker mantém trap/restauração de foco, Escape, teclado e axe; não introduz HTML
  armazenado nem dependência visual externa.

## Extensão permanente da baseline — proposta da Etapa I

- Rotas de venda exigem Bearer de sócio ativo e preservam CORS, no-store e DTOs fechados.
- `deviceVersion` e `saleVersion` são obrigatórios, não negativos e conferidos sob lock.
- Preço, datas e motivo são validados no backend; PostgreSQL V5 protege invariantes críticas.
- Usuário responsável vem da sessão; nenhum seletor de vendedor ou input de lucro/ledger.
- Registro/cancelamento são atômicos com ledger e auditoria append-only, sem PATCH/delete.
- Falta do ledger original bloqueia cancelamento. Motivo é texto escapado e limitado.
- Resposta não expõe entidades, IDs internos de ledger, hash, token nem storage key.
- Testes negativos, concorrência real, SQL direto e browser com axe fazem parte do gate.
