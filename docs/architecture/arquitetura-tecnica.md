# Arquitetura Técnica

## Visão geral

Arquitetura cliente-servidor com frontend separado do backend.

```text
React + TypeScript
        |
        | HTTPS / REST JSON
        v
Spring Boot
        |
        +---- PostgreSQL
        |
        +---- Storage de imagens
```

## Backend

Tecnologia principal:

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- Bean Validation
- Spring Security
- Flyway
- PostgreSQL

Diretrizes:

- regra de negócio deve permanecer no backend;
- frontend não deve ser responsável por cálculos financeiros críticos;
- validações devem existir no backend mesmo que também existam no frontend;
- migrations de banco devem ser versionadas;
- evitar arquitetura excessivamente complexa.

## Banco

Banco recomendado:

- PostgreSQL

Hospedagem inicial recomendada:

- Supabase, aproveitando plano gratuito quando suficiente.

Uso do Supabase:

- PostgreSQL;
- storage para fotos, se adequado;
- infraestrutura hospedada.

A aplicação não deve acoplar regras de negócio ao Supabase.

## Frontend

Tecnologias:

- React
- TypeScript
- Vite
- Tailwind CSS

Base visual:

- shadcn/ui;
- Radix UI;
- componentes selecionados do 21st.dev;
- Tabler Icons;
- React Bits para microinterações;
- Uiverse como inspiração pontual.

## Mobile futuro

Aplicativo iOS poderá ser construído posteriormente com React Native ou outra tecnologia adequada, reutilizando:

- backend;
- API;
- banco;
- autenticação;
- regras de negócio.

## Auditoria

Toda mutação relevante deve registrar autoria e data.

## Segurança

Requisitos mínimos:

- senha armazenada com hash seguro;
- HTTPS em produção;
- autenticação no backend;
- endpoints protegidos;
- validação de entrada;
- queries via ORM/JPA;
- secrets apenas em variáveis de ambiente;
- nenhuma credencial commitada no Git;
- CORS restrito;
- limitação de tipos e tamanho de upload;
- logs sem dados sensíveis.

## Performance

Prioridades:

- resposta rápida;
- queries simples;
- índices apenas onde necessários;
- imagens comprimidas;
- paginação em listas;
- frontend com lazy loading quando útil;
- animações curtas;
- nenhuma animação deve bloquear uma ação.

Princípio visual:

> Motion must never delay work.
