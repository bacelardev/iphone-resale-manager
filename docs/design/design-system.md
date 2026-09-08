# Direção Visual e Design System

## Baseline Visual Oficial

As referências normativas do produto são:

- [Desktop — Visual Prototype V2](prototypes/iphone-resale-manager-visual-prototype-v2.svg);
- [Mobile — Mobile-first Prototype V1](prototypes/iphone-resale-manager-mobile-prototype-v1.svg).

O V2 orienta desktop, notebook, sidebar, cards, grids, tabelas, formulários e
hierarquia. O Mobile-first orienta iPhone, telas pequenas, drawers, bottom sheets,
cards empilhados, ações touch e formulários em coluna única. Ambos definem linguagem
visual; textos, números, catálogos e registros exibidos são exemplos, não contratos.

Em caso de divergência, prevalecem nesta ordem:

1. regras de negócio e contratos aprovados nas Etapas A–E;
2. contratos reais implementados no backend;
3. security baseline;
4. baseline visual V2 + Mobile-first;
5. este Design System;
6. decisões específicas documentadas na Etapa F.

## Referências principais

- Copilot Money
- Cuberto

## Referências secundárias

- Apple
- Active Theory
- Lusion
- Ramp
- Rho

## Conceito

Tema:

**Dark Premium / Financial Minimalism**

Objetivo:

- aparência extremamente limpa;
- sensação de produto premium;
- alto contraste;
- poucos elementos competindo;
- animações leves;
- resposta imediata;
- excelente uso em desktop e celular.

## Paleta inicial

```text
Fundo principal:      #080808
Superfície:           #101010
Card elevado:         #151515
Hover:                #1B1B1B
Bordas:               #262626
Texto principal:      #F5F5F7
Texto secundário:     #A1A1A6
Texto discreto:       #6E6E73
```

Cores semânticas:

- verde: entrada, lucro, disponível;
- vermelho: saída, custo, erro;
- amarelo: manutenção pendente;
- cinza: neutro;
- branco: ação principal.

## Raios

Sugestão:

- cards grandes: 20px;
- cards pequenos: 16px;
- inputs: 12–14px;
- botões: 12–14px;
- badges: pill.

## Movimento

Durações oficiais:

- hover e press: 150ms;
- input e toggle: 150–180ms;
- card e mudança de estado: 180ms;
- drawer, bottom sheet e modal: 200–220ms;
- foto e crossfade: 180ms.

Usar para:

- hover;
- transição de card;
- feedback de clique;
- entrada de modal;
- atualização de valores;
- estados de carregamento.

Evitar:

- WebGL contínuo em dashboard;
- partículas;
- 3D pesado;
- animações longas;
- qualquer efeito que atrase operação.

## Bibliotecas e repositórios

### Base

- Tailwind CSS
- shadcn/ui
- Radix UI

### Complementos

- 21st.dev: componentes avançados;
- Tabler Icons: família oficial de ícones;
- React Bits: microinterações e animações;
- Uiverse Galaxy: inspiração e componentes pontuais.

## Regra de arquitetura visual

Nenhum componente externo deve ser espalhado diretamente pela aplicação.

Todo componente adotado deve primeiro ser adaptado para o Design System interno do projeto.

Exemplo:

```text
components/
  ui/
    button/
    card/
    dialog/
    drawer/
    bottom-sheet/
    input/
    select/
    badge/
```

Isso evita inconsistência visual e dependência excessiva de terceiros.

O conjunto base inclui Button, Input, Select/Combobox, Card, Badge, Dialog, Drawer,
BottomSheet, Skeleton, Spinner, FormField, PageHeader, EmptyState e ErrorState. Um
catálogo só pode ser ligado ao Select/Combobox quando o backend o disponibilizar; a
fundação não inventa modelos, cores ou capacidades.
