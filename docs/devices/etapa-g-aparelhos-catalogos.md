# Etapa G — Aparelhos, Catálogos e Preparação da Implantação

**Versão:** 1.0 proposta  
**Status:** aguardando aprovação da Etapa G

## Resultado

A primeira vertical do negócio implementa catálogos, aparelhos, fotos, estoque e importação inicial. O `cutoffAt` nasce em `PREPARING`, antes da carga; a primeira importação o bloqueia. Compras operacionais criam `DEVICE_PURCHASE / OUTFLOW`; estoque existente preserva o custo sem fabricar uma nova saída.

## Migration

`V3__business_initialization_and_owner_attribution.sql` adiciona `business_initialization`, `registration_origin`, atribuição futura de sócios e `owner_capital_opening`. V1 e V2 não foram alteradas. Manutenção e financeiro permanecem sem comportamento funcional nesta etapa.

## Storage

Fotos ficam em filesystem configurável no backend. Chaves são aleatórias, upload valida tamanho, MIME e magic bytes, respostas omitem `storageKey` e leitura usa URL HMAC temporária. Produção deve montar volume persistente/backup e fornecer segredo forte server-side.

## Validação técnica

Head técnico validado: `03e7eb0fd01668de07d58d156486f2b4cb0c3d8f`.

- Backend workflow `34309109701`: success.
- Frontend workflow `34309109708`: success.
- Backend: 49 testes (24 unitários + 25 de integração), zero falhas/skips.
- Frontend: 38 testes Vitest em 7 arquivos.
- E2E: 22/22 Playwright com backend real.
- PostgreSQL 16.15; Flyway V1+V2+V3; Hibernate `ddl-auto=validate`; build Java e frontend.
- axe/WCAG 2 AA, teclado, reduced motion e larguras 375/430/768/1024/1440.

## Limitações conhecidas

- Catálogos começam vazios; nenhum seed foi criado sem lista oficial aprovada.
- Storage em filesystem requer volume persistente e estratégia de backup no ambiente de produção.
- `maintenance.registration_origin` é somente schema/JPA; manutenção funcional pertence à H.
- Conclusão da implantação, caixa e capital dos sócios pertencem à J.

Nenhum merge automático foi realizado e nenhuma funcionalidade da Etapa H foi iniciada.
