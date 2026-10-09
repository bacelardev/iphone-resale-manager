# Etapa K — readiness de produção

Este documento prepara a V1 para implantação sem executar deploy. A topologia aprovada é:

- frontend React/Vite no Netlify;
- API Spring Boot no Render, em uma única instância;
- PostgreSQL no Neon;
- fotos privadas no ImageKit;
- monitoramento HTTP no UptimeRobot;
- CI no GitHub Actions.

## Ordem segura de implantação

1. Criar um projeto Neon vazio e credenciais separadas de runtime e migration.
2. Configurar o ImageKit e migrar fotos existentes pelo procedimento não destrutivo abaixo.
3. Criar o serviço Render a partir de `render.yaml`, sem disparar deploy até preencher os secrets.
4. Criar o site Netlify a partir de `netlify.toml` e configurar a origem HTTPS final da API.
5. Definir no Render `APP_CORS_ALLOWED_ORIGINS` com **uma única** origem HTTPS do Netlify.
6. Validar migrations, login, fotos, histórico e usuários em staging.
7. Cadastrar `https://<api>/api/v1/health` no UptimeRobot somente após o smoke test.

## Render

O `backend/Dockerfile` gera um JAR com JDK 21 e executa a imagem final como usuário sem privilégios.
O `render.yaml` mantém secrets como `sync: false` e usa `/api/v1/health` como healthcheck.

Variáveis obrigatórias:

| Variável | Uso |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=prod` | Ativa validações fail-fast de produção. |
| `DB_URL` | JDBC Neon com `sslmode=require`. |
| `DB_USERNAME`, `DB_PASSWORD` | Credencial de runtime. |
| `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD` | Credencial usada pelo Flyway. |
| `APP_CORS_ALLOWED_ORIGINS` | Origem HTTPS exata do frontend, sem `/` final. |
| `APP_PHOTO_STORAGE_SIGNING_SECRET` | Segredo aleatório com no mínimo 32 caracteres. |
| `APP_IMAGEKIT_PRIVATE_KEY` | Chave privada, somente no backend. |
| `APP_IMAGEKIT_URL_ENDPOINT` | Endpoint HTTPS da conta ImageKit. |

`APP_BOOTSTRAP_ENABLED` deve permanecer `false`. Para a primeira instalação, o bootstrap pode ser
habilitado temporariamente em ambiente controlado; após a criação do primeiro usuário, remova as
três credenciais e volte imediatamente para `false` antes de considerar o ambiente pronto.

## Neon e Flyway

- Use PostgreSQL compatível com as migrations V1–V6; elas não foram alteradas pela Etapa K.
- Exija TLS no JDBC: `jdbc:postgresql://.../db?sslmode=require`.
- A credencial de migration precisa de DDL durante a inicialização; a de runtime deve receber apenas
  os privilégios necessários às operações da aplicação.
- Faça backup lógico antes de uma atualização. `ddl-auto=validate` permanece obrigatório.
- Execute migrations primeiro em staging e registre o resultado real. Não trate este documento como
  evidência de uma migration executada no Neon.

## ImageKit e fotos existentes

Novos uploads usam arquivos privados e chaves com prefixo `imagekit:`. A API continua entregando ao
frontend somente a URL temporária HMAC já existente; a chave privada nunca usa prefixo `VITE_`.

Migração reversível de fotos locais:

1. mantenha `APP_PHOTO_STORAGE_PROVIDER=local`;
2. faça backup integral de `APP_PHOTO_STORAGE_ROOT` e do PostgreSQL;
3. envie cada arquivo ao ImageKit preservando o caminho relativo original, por exemplo
   `device-uuid/foto.png`;
4. compare contagem, tamanho, MIME e leitura de amostras nos dois storages;
5. em staging, altere para `APP_PHOTO_STORAGE_PROVIDER=imagekit` e mantenha uma cópia do diretório
   local montada em `APP_PHOTO_STORAGE_ROOT`;
6. valide todas as fotos. Para uma chave antiga, o adapter lê primeiro o arquivo local e, se ausente,
   tenta o mesmo caminho no ImageKit;
7. somente depois da validação e do período de observação, arquive a cópia local. Não apague o backup
   durante a Etapa K.

Esse desenho evita modificar `device_photo.storage_key` e preserva o trigger de imutabilidade da V1.
Objetos antigos removidos logicamente devem entrar em uma lista operacional de limpeza, pois não têm
`fileId` persistido; novos objetos ImageKit são removidos por `fileId` após o commit do banco.

## Netlify

`netlify.toml` fixa Node 24, build Vite e fallback de SPA. Configure:

```text
VITE_API_BASE_URL=https://<servico-render>
```

Não configure chaves do banco, ImageKit private key, segredo de assinatura ou senha no Netlify. O
frontend valida que a API de produção usa HTTPS.

## CORS e proxies

O `StrictCorsFilter` continua allowlist-only e rejeita `*`. O perfil `prod` exige exatamente uma
origem HTTPS, sem credenciais, porta, path, query ou fragmento. No Render,
`SERVER_FORWARD_HEADERS_STRATEGY=framework` reconhece os headers do proxy gerenciado.

## Healthcheck e UptimeRobot

`GET /api/v1/health` é público e retorna apenas `{"status":"UP"}` com `Cache-Control: no-store`.
Ele não testa credenciais, não consulta dados de negócio e não devolve versão, host, stack trace ou
configuração. Configure o UptimeRobot com método GET e HTTPS após o deploy aprovado.

## Rate limiting e escala

`InMemoryLoginRateLimiter` é adequado à topologia oficial de **uma instância**. Seu estado é perdido
em restart e não é compartilhado. Antes de aumentar o serviço para múltiplas réplicas, substitua-o por
um limiter compartilhado e revalide enumeração, `Retry-After` e disponibilidade. Redis não foi
introduzido nesta etapa porque não é necessário para a topologia aprovada.

## Evidência ainda externa

Sem contas e credenciais reais, permanecem pendentes: conexão Neon, upload/leitura real no ImageKit,
build Render, build Netlify, smoke test público e monitor UptimeRobot. Nenhum desses itens deve ser
marcado como aprovado até a execução pós-revisão.
