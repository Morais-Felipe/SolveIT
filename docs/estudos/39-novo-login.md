# Estudo — Ticket 39: novo login

Notas do plano aprovado em 2026-11-02. As telas de login e cadastro foram refeitas a partir de uma imagem de referência, com o botão vindo de um componente externo. As regras de login e cadastro não mudaram.

## 1. Adaptar um componente, em vez de colar

O código do botão foi escrito para uma demonstração, e copiá-lo como estava quebraria o login:

| No código original | Problema | Como ficou |
|---|---|---|
| `type="button"` fixo | Não enviaria o formulário | Aceita `type="submit"` |
| Só aceita `label` em texto | Sem lugar para o ícone | Aceita conteúdo e a prop `icone` |
| Não repassa `disabled` | Permitiria clicar duas vezes | Repassa, e mostra o indicador durante o envio |
| `aria-label` com o mesmo texto | Redundante, e ficaria errado com outro conteúdo | O nome acessível vem do próprio texto |
| Hover em qualquer aparelho | No toque, o efeito fica preso | `@media (hover: hover)` |
| Bloco `<style>` a cada uso | CSS duplicado na página | CSS único no `index.css` |
| `"use client"` | Só faz sentido em Next.js | Removido |

**Para levar:** antes de usar código de terceiros, pergunte o que ele assume sobre o ambiente. Quem escreve uma demonstração não está pensando no seu formulário.

## 2. Por que a borda consegue girar: `@property`

O navegador só anima o que sabe interpolar. Para ele, uma variável CSS comum é só texto, então não existe "meio caminho" entre `0deg` e `360deg`.

```css
@property --brilho-angulo {
  syntax: "<angle>";
  initial-value: 0deg;
  inherits: false;
}
```

O registro diz que a variável é um ângulo. A partir daí, ela pode ser animada, e o gradiente cônico da borda, que depende dela, gira junto.

A técnica da borda usa dois fundos na mesma caixa, com a borda transparente:

- **`padding-box`:** o preto cobre a parte interna.
- **`border-box`:** o gradiente vai até a borda, e só essa faixa fica à mostra.

**O custo:** o projeto prefere animar só `transform` e `opacity`, que o navegador faz sem redesenhar a área. Este efeito redesenha o botão a cada quadro. É uma exceção consciente, porque a área é pequena. A tela manteve cerca de 60 quadros por segundo nas medidas, feitas em um computador de desenvolvimento, não em um celular real.

## 3. Acessibilidade do movimento

Com `prefers-reduced-motion`, o botão para. Só que, parado, sobraria um pequeno arco de luz, e um botão preto em cartão escuro quase sumiria. Por isso, nesse modo, a borda inteira fica laranja.

**Para levar:** desligar a animação não basta. Confira se o que restou ainda funciona.

## 4. Fundo estático com SVG

O fundo anterior usava `canvas` e redesenhava partículas o tempo todo. O novo é um SVG sem animação: trabalho quase nulo depois de desenhado, e o movimento fica concentrado no botão.

A primeira versão usava `preserveAspectRatio="xMidYMid slice"`, que mantém a proporção cortando as sobras. No celular, a tela alta cortava justamente os cantos, onde estão as ondas. Com `none`, o desenho estica para caber. Isso distorceria um logo, mas curvas orgânicas toleram bem.

**Para levar:** esse problema só apareceu ao olhar a captura do celular. Nenhum teste automatizado o acusaria.

## 5. O que a comparação com a imagem encontrou

- O texto do rodapé encostava na borda do cartão.
- O S do logo tinha um degrau no centro, onde dois traços se cruzavam. Um único traço contínuo resolveu.
- As ondas sumiam no celular, como descrito acima.

## 6. Decisões

| Decisão | Escolha | Motivo |
|---|---|---|
| Cadastro | Mesmo botão e mesmo cartão | Evita parecer outro produto |
| Logo | S em SVG, também na aba e no menu | Uma só marca |
| Slogan | "Abra. Acompanhe. Resolva." | Descreve o caminho de um chamado; o da imagem lembrava uma plataforma de ensino |
| Ícones dos campos | Ao lado do rótulo | Como na imagem de referência |

O logo e o fundo são recriações próximas da imagem, não cópias exatas.

## 7. Como foi verificado

- **Testes de unidade:** o botão envia o formulário, não envia em andamento e tem nome acessível correto.
- **Regressão:** lint, tipos, 90 testes de unidade e 6 jornadas E2E.
- **Capturas:** login e cadastro no desktop e no celular, com foco no botão, movimento reduzido e tela baixa.
- **Tela baixa:** a página rola, e o botão continua acessível.

## Para aprofundar (opcional)

- `conic-gradient` e `animation-composition`: como somar animações.
- `@media (hover: hover)` e `pointer`: como detectar o tipo de aparelho.
- Por que `transform` e `opacity` são mais baratos: as etapas de layout, pintura e composição do navegador.
