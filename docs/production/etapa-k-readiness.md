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
`SERVER_FORWARD_HEADERS_STRATEGY=framework` reconhece o esquema HTTPS informado pelo proxy
gerenciado, o que mantém `request.isSecure()` e o HSTS corretos.

Essa estratégia **não** é usada para identificar o cliente no rate limit de login. O
`ForwardedHeaderFilter` do Spring 6.2 adota o **primeiro** valor de `Forwarded` ou de
`X-Forwarded-For` como endereço remoto, e esse valor é enviado pelo próprio cliente. Antes desta
correção, alternar esse cabeçalho criava um contador novo a cada tentativa. O
`LoginClientAddressResolver` lê a requisição do container, sem passar pelo wrapper do Spring, e usa:

- `APP_AUTH_LOGIN_TRUSTED_PROXY_HOPS=0` (padrão da aplicação): o peer TCP; todo cabeçalho de
  encaminhamento é ignorado;
- `APP_AUTH_LOGIN_TRUSTED_PROXY_HOPS=N`: a N-ésima entrada de `X-Forwarded-For` contada da direita,
  a única escrita por proxy confiável. Entradas à esquerda e o cabeçalho `Forwarded` são ignorados.
  Se houver menos de N entradas ou o valor não for um IP literal, usa o peer TCP.

O `render.yaml` define `APP_AUTH_LOGIN_TRUSTED_PROXY_HOPS=1`. **Justificativa:** o Render não
documenta formalmente o formato do cabeçalho. Um relato público de teste indica que ele acrescenta o
endereço do cliente ao `X-Forwarded-For` recebido, sem limpá-lo, o que torna a entrada mais à direita
a única confiável. Com `0`, todo o tráfego chegaria pelo IP interno do proxy, todos os usuários
compartilhariam um contador e qualquer pessoa poderia bloquear o login de todos com cinco tentativas
por minuto. **Impactos possíveis se o formato real for outro:**

| Formato real no Render | Efeito de `1` | Ajuste |
| --- | --- | --- |
| Acrescenta o IP do cliente à direita | Correto | Nenhum |
| Há um salto adicional (ex.: CDN) antes do proxy do Render | Clientes atrás do mesmo nó do CDN compartilham contador | `2` |
| Insere o IP real à esquerda e preserva o resto | Contador por IP volta a ser contornável; os limites por conta continuam valendo | Rever a estratégia |
| Não acrescenta `X-Forwarded-For` | A entrada mais à direita é do cliente: contador por IP contornável | Rever a estratégia |
| Acrescenta à primeira linha do cabeçalho, não à última | Uma linha extra enviada pelo cliente vira a entrada usada | Rever a estratégia |

A configuração também supõe que **todo** tráfego passe pelo proxy do Render. Um acesso que não
passe por ele, por exemplo pela rede privada entre serviços da mesma conta, consegue forjar o
cabeçalho.

**Verificação obrigatória em staging, antes de liberar o serviço**, sem registrar IPs em log, usando
um username inexistente:

1. de uma máquina, envie seis logins inválidos, cada um com um `X-Forwarded-For` falso diferente. A
   sexta resposta deve ser `429`. Se for `401`, o cabeçalho continua forjável;
2. repita o passo 1 enviando o valor falso numa **segunda linha** de `X-Forwarded-For`. A sexta
   resposta também deve ser `429`;
3. bloqueie uma rede e, de outra rede, faça um login inválido. A resposta deve ser `401`. Se for
   `429`, os clientes estão compartilhando contador;
4. registre o resultado aqui antes de aprovar o deploy.

`APP_AUTH_LOGIN_TRUSTED_PROXY_HOPS` maior que zero com `SERVER_FORWARD_HEADERS_STRATEGY=native` é
recusado na inicialização, porque o `RemoteIpValve` do Tomcat já consome o cabeçalho.

## Healthcheck e UptimeRobot

`GET /api/v1/health` é público e retorna apenas `{"status":"UP"}` com `Cache-Control: no-store`.
Ele não testa credenciais, não consulta dados de negócio e não devolve versão, host, stack trace ou
configuração. Configure o UptimeRobot com método GET e HTTPS após o deploy aprovado.

## Rate limiting e escala

O login tem três limites, todos com janela fixa que expira sozinha e sem bloqueio permanente. A
chave de conta é o username normalizado (`trim` e minúsculas), exista a conta ou não.

| Limite | Chave | Padrão | Variáveis | No sucesso |
| --- | --- | --- | --- | --- |
| Por cliente | endereço resolvido acima (IPv6 por /64) | 5 tentativas / 1 min | `APP_AUTH_LOGIN_MAX_ATTEMPTS`, `APP_AUTH_LOGIN_WINDOW` | zerado |
| Conta + cliente | conta e endereço resolvido | 5 falhas / 15 min | `APP_AUTH_LOGIN_ACCOUNT_CLIENT_MAX_FAILURES`, `APP_AUTH_LOGIN_ACCOUNT_CLIENT_WINDOW` | **falhas mantidas** |
| Conta global | conta | 100 falhas / 15 min | `APP_AUTH_LOGIN_ACCOUNT_MAX_FAILURES`, `APP_AUTH_LOGIN_ACCOUNT_WINDOW` | **falhas mantidas** |

Ordem e contagem, aplicadas pelo `LoginThrottle` depois do limite por cliente:

1. reserva uma vaga em conta + cliente. Se recusada, responde `429` e o contador global **não** é tocado;
2. reserva uma vaga no contador global. Se recusada, responde `429` e devolve a vaga de conta + cliente;
3. senha errada, conta inexistente ou inativa: as duas vagas ficam e contam como falha;
4. sucesso: devolve **apenas as duas vagas da própria tentativa**. As falhas já registradas e as
   reservas de requisições concorrentes continuam contadas nos dois níveis até a janela expirar;
5. erro inesperado: devolve as duas vagas.

Cada janela tem uma geração própria, e uma vaga só é devolvida à janela em que foi reservada. Se a
janela expirar ou for zerada enquanto a senha é verificada, a devolução é ignorada e nunca reduz a
contagem da janela nova.

As reservas acontecem antes da verificação da senha, então requisições paralelas não ultrapassam
nenhum dos limites. Como nenhum resultado apaga falhas, um cliente contribui com no máximo 5 falhas
por janela de conta + cliente, mesmo conhecendo a senha e intercalando sucessos. Essa
janela nunca é menor que a global, então no máximo duas se sobrepõem a uma janela global, e um cliente
soma no máximo 10 falhas a ela. A aplicação **recusa iniciar** se o limite global não for maior que o
dobro do limite de conta + cliente, ou se a janela de conta + cliente for menor que a global. Assim,
um cliente sozinho nunca bloqueia a conta para os outros, mesmo que o contador por cliente seja zerado
por um login bem-sucedido.

**Riscos residuais.** Bloquear uma conta para todos exige falhas vindas de pelo menos 11 endereços
distintos dentro de 15 minutos. Um atacante com muitos IPs, ou com um bloco IPv6 maior que /64,
consegue isso; o bloqueio dura no máximo a janela, mas pode ser renovado. Quem compartilha o mesmo IP
público que a vítima (NAT corporativo ou doméstico) pode bloquear a combinação conta + cliente dela. A
resposta `429` é idêntica para todos os limites e para contas inexistentes. Nenhum username, IP,
senha ou token é registrado em log.

**Memória.** Cada mapa guarda no máximo `APP_AUTH_LOGIN_MAX_TRACKED_CLIENTS` ou
`APP_AUTH_LOGIN_MAX_TRACKED_ACCOUNTS` chaves (10.000). Tentativas recusadas não criam entradas. Com o
mapa cheio, a entrada removida é a menos usada recentemente **que não esteja bloqueando**. Um
bloqueio ativo só é descartado se o mapa inteiro estiver bloqueado, o que exige 10.000 chaves com o
limite atingido.

`InMemoryLoginRateLimiter` é adequado à topologia oficial de **uma instância**. Seu estado é perdido
em restart e não é compartilhado. Antes de aumentar o serviço para múltiplas réplicas, substitua-o por
um limiter compartilhado e revalide enumeração, `Retry-After` e disponibilidade. Redis não foi
introduzido nesta etapa porque não é necessário para a topologia aprovada.

## Evidência ainda externa

Sem contas e credenciais reais, permanecem pendentes: conexão Neon, upload/leitura real no ImageKit,
build Render, build Netlify, smoke test público e monitor UptimeRobot. Nenhum desses itens deve ser
marcado como aprovado até a execução pós-revisão.
