# Direção Visual e Design System

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

Duração típica:

- 150ms a 250ms.

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
    input/
    badge/
```

Isso evita inconsistência visual e dependência excessiva de terceiros.
