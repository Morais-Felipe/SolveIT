# Estudo — Ticket 33: identidade visual preto e laranja

Notas do plano aprovado em 2026-11-01. O ponto de partida foi um template de login do site 21st.dev, adaptado ao SolveIT em vez de copiado.

## 1. Por que não seguir o prompt do template ao pé da letra

Prompts de integração são genéricos: não conhecem o projeto. O nosso pedia coisas que atrapalhariam:

| O prompt pedia | O que fizemos | Motivo |
|---|---|---|
| Copiar `button`, `card`, `input` e `label` | Mantivemos os nossos | Os nossos usam Base UI. Trocar afetaria todas as telas. |
| Instalar pacotes `@radix-ui/*` | Nada instalado | Teríamos duas bibliotecas com a mesma função. |
| Usar o componente pronto | Levamos só o visual | O formulário dele não envia nada: não tem `onSubmit` nem estado. |
| Manter GitHub, Google, "Forgot password", "Remember me" e Contact | Removidos | O sistema não tem essas funções, e botão sem função engana o usuário. |

**Para levar:** ao usar código de terceiros, separe o que é aparência do que é comportamento. A aparência quase sempre pode ser aproveitada; o comportamento precisa ser o do seu sistema.

## 2. Tokens de tema: um único lugar para as cores

Os componentes não guardam cores fixas. Eles pedem `bg-primary` ou `bg-background`, que apontam para variáveis CSS em `solveit-web/src/index.css`. Por isso, mudar os valores do bloco `.dark` mudou o sistema inteiro quase sem tocar nas telas.

É a mesma ideia de centralizar uma constante em Java em vez de repetir o valor pelo código.

As exceções foram os lugares com cores escritas à mão (`sky-`, `amber-` e `emerald-`) em `Selos.tsx` e `UsuariosPage.tsx`.

## 3. Cor também é informação

O verde ficou só em "Resolvido" e o vermelho, nos erros e na prioridade crítica. Se um erro fosse laranja, se confundiria com um botão, porque o laranja já significa "ação" aqui.

O texto sobre o laranja é escuro porque branco sobre laranja tem pouco contraste e fica difícil de ler.

## 4. Decisões no código

- **Separação de responsabilidades:** `FundoAnimado` só decora, `AutenticacaoLayout` é a moldura comum e `CampoSenha` reaproveita o `CampoTexto`.
- **`aria-hidden` e `pointer-events-none`:** o fundo não aparece para leitores de tela nem captura cliques.
- **`type="button"` no olho da senha:** dentro de um `<form>`, um botão sem `type` envia o formulário. O `CampoSenha.test.tsx` protege esse detalhe.
- **`min-h-svh` em vez de `fixed inset-0`:** o template travava a tela na altura da janela, o que cortaria o cartão em telas baixas. Com altura mínima, a página rola.
- **`prefers-reduced-motion`:** é uma preferência do sistema operacional de quem passa mal ou se distrai com movimento. Nesse caso, a grade aparece pronta e não há partículas.
- **`requestAnimationFrame`:** o navegador pede um quadro de cada vez e suspende o pedido em abas ocultas, então não foi preciso escrever código para pausar.

## 5. Como mudar a tela afetou um teste

O E2E usava `getByLabel('Senha')`. No Playwright, esse seletor aceita correspondência parcial, então passaria a encontrar também o botão "Mostrar senha" e falharia por ambiguidade. A correção foi `{ exact: true }`: o teste continua procurando o mesmo campo, só que com mais precisão.

## 6. Um diagnóstico que vale lembrar

Na revisão visual, o cartão apareceu quase invisível no painel de navegador embutido, com o relógio da animação parado em 0. Em vez de concluir pelo que parecia, medimos num Chromium independente: a opacidade chegou a 1. O problema era do painel, que congelava a animação, e não do SolveIT.

**Para levar:** quando um sintoma aparece, compare em outro ambiente antes de alterar o código. E lembre que o teste `toBeVisible` do Playwright não considera opacidade: um elemento transparente passa nele.

## Para aprofundar (opcional)

- Espaço de cor `oklch`: por que é mais previsível que hexadecimal para montar paletas.
- Contraste de texto segundo a WCAG, a principal diretriz de acessibilidade na web.
- Como o navegador anima elementos sem refazer o layout (`transform` e `opacity`).
