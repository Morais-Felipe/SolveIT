# Estudo — Ticket 38: refinamento visual

Notas do plano aprovado em 2026-11-02. O objetivo foi aproximar o SolveIT da disciplina visual do estilo do BaseHub, visto na galeria do Refero, sem mudar nenhum comportamento.

## 1. De onde veio a direção

Três estilos ficaram mais próximos do SolveIT:

| Estilo | Por que entrou | O que foi usado |
|---|---|---|
| BaseHub | Mesma fonte (Geist) e laranja quase igual | Uma só cor quente, cantos mais retos e letras justas nos títulos |
| Featurebase | É software de suporte | A ideia de camadas de painéis, sem a cor violeta |
| Cron | Mesma disciplina no uso do laranja | Nada direto: ele proíbe cor em status, o que não serve para chamados |

**Para levar:** referência não é molde. Vale perguntar por que cada regra existe no produto original e se ela faz sentido no seu.

## 2. Design tokens

Um *token* é uma variável que dá nome a uma decisão visual. A escala de bordas deriva de um único valor:

```css
--radius: 0.5rem;                        /* antes: 0.625rem */
--radius-lg: var(--radius);              /* botões: 8px */
--radius-xl: calc(var(--radius) * 1.4);  /* cartões: cerca de 11px */
```

Uma linha alterou botões e cartões em todas as telas. O custo é que a mudança alcança tudo, então ela precisa ser conferida nas telas.

## 3. Uma cor, um significado

Antes, o laranja queria dizer duas coisas ao mesmo tempo: "clique aqui" e "este chamado está em andamento". A regra agora é: **laranja significa ação ou tela atual**.

| Item | Antes | Depois |
|---|---|---|
| Status "Em andamento" | Laranja | Azul |
| Prioridade "Alta" | Laranja | Âmbar |
| Selo "Ativo" (usuários) | Laranja | Verde |
| Botões, menu ativo e foco | Laranja | Sem mudança |

**Exceções que continuam em laranja:**

- O gráfico semanal e as barras de categoria e de atendente, porque ali a cor marca um dado, não um estado.
- O alerta sobre chamados que voltam para a fila, em `UsuariosPage`. Ele ficou fora do escopo aprovado e pode ser revisto depois.

A cor nunca é o único sinal, porque o texto do status continua na tela. Quem não distingue bem as cores não perde informação.

## 4. Contraste

O WCAG, guia internacional de acessibilidade, pede ao menos 4,5:1 entre texto pequeno e fundo no nível AA. As medidas foram feitas com as cores que o navegador realmente calculou, somando as transparências de cada camada:

| Selo | Antes | Depois |
|---|---|---|
| Aberto | 17,78:1 | 16,23:1 |
| Em andamento | 6,34:1 | 10,11:1 |
| Resolvido | 11,93:1 | 10,99:1 |
| Baixa | 8,08:1 | 7,61:1 |
| Média | 19,28:1 | 18,16:1 |
| Alta | 6,97:1 | 13,10:1 |
| Crítica | 6,97:1 | 6,56:1 |
| Ativo | 6,34:1 | 10,99:1 |

Alguns valores caíram um pouco porque o fundo das linhas ficou mais claro. Todos seguem acima do mínimo. "Fechado" e "Cancelado" não foram medidos, porque nenhum deles apareceu na tela medida; as cores desses dois não mudaram.

## 5. Três níveis de superfície

| Nível | Token | Onde |
|---|---|---|
| Fundo | `--background` | A página |
| Painel | `--card` | Cartões e linhas das listas |
| Elevado | `--muted` e `--secondary` | Botões secundários e etiquetas |

Os tokens já existiam. Faltava usá-los nas listas, cujas linhas tinham só borda sobre o fundo. A mudança valeu para `ChamadosPage`, `FilaPage`, `UsuariosPage` e `CategoriasPage`. Os comentários da página de detalhe ficaram como estavam, porque não faziam parte do plano.

## 6. Como foi verificado

- **Capturas de antes e depois:** dashboard, chamados, usuários e categorias, no desktop e no celular. A fila não foi capturada, mas recebeu a mesma classe das outras listas.
- **Testes:** lint, tipos, testes de unidade e as 6 jornadas E2E.

**Para levar:** teste automatizado prova que nada quebrou, não que ficou bonito. Por isso a comparação visual é uma etapa à parte.

## Para aprofundar (opcional)

- Espaço de cor `oklch`, usado no tema: por que é mais previsível que `rgb` ao clarear uma cor.
- Critérios de contraste do WCAG e diferenças entre texto pequeno e grande.
- `color-mix` e transparências: por que a cor final depende da camada de baixo.
