# Etapa G — Aparelhos, Catálogos e Preparação da Implantação

**Versão:** 1.0 aprovada  
**Status:** Etapa G encerrada

## Resultado

A primeira vertical do negócio implementa catálogos, aparelhos, fotos, estoque e importação inicial. O `cutoffAt` nasce em `PREPARING`, antes da carga; a primeira importação o bloqueia. Compras operacionais criam `DEVICE_PURCHASE / OUTFLOW`; estoque existente preserva o custo sem fabricar uma nova saída.

## Migration

`V3__business_initialization_and_owner_attribution.sql` adiciona `business_initialization`, `registration_origin`, atribuição futura de sócios e `owner_capital_opening`. V1 e V2 não foram alteradas. Manutenção e financeiro permanecem sem comportamento funcional nesta etapa.

## Storage

Fotos ficam em filesystem configurável no backend. Chaves são aleatórias, upload valida tamanho, MIME e magic bytes, respostas omitem `storageKey` e leitura usa URL HMAC temporária. Produção deve montar volume persistente/backup e fornecer segredo forte server-side.

## Validação técnica

Head técnico validado: `2c56075a7f0bf5a6cb49552e5f4f1e68106b3d92`.
Fechamento aprovado no head `4525b0922f3605f1de0a7999ad0a6ef068bab76f`.

- Backend workflow `34389291835`: success.
- Frontend workflow `34389291371`: success.
- Backend: 49 testes (24 unitários + 25 de integração), zero falhas/skips.
- Frontend: 41 testes Vitest em 8 arquivos.
- E2E: 22/22 Playwright com backend real, zero falhas/skips.
- PostgreSQL 16.15; Flyway V1+V2+V3; Hibernate `ddl-auto=validate`; build Java e frontend.
- Cobertura de revisão: invariância do `INITIAL_IMPORT`, `expectedVersion` obrigatório, filtros desktop/mobile, edição de catálogos, fotos PNG decodificáveis e cutoff em horário local.
- axe/WCAG 2 AA com o picker aberto, focus trap, Escape, restauração de foco, teclado, reduced motion e larguras 375/430/768/1024/1440.
- Screenshots responsivos e detalhe do aparelho com fotos renderizadas publicados no artefato `frontend-responsive-screenshots` do workflow de frontend.

## Limitações conhecidas

- Catálogos começam vazios; nenhum seed foi criado sem lista oficial aprovada.
- Storage em filesystem requer volume persistente e estratégia de backup no ambiente de produção.
- `maintenance.registration_origin` é somente schema/JPA; manutenção funcional pertence à H.
- Conclusão da implantação, caixa e capital dos sócios pertencem à J.

A Etapa G foi aprovada e encerrada formalmente em 09/09/2026. Nenhuma funcionalidade da Etapa H foi iniciada; qualquer avanço exige nova autorização explícita.
