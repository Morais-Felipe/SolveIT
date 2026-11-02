# Estudo — Ticket 35: gráfico de chamados por semana

Notas do plano aprovado em 2026-11-02. Este ticket substitui a decisão D-2 da Fase 5 ("cards e tabelas, sem gráficos"): agora existe um requisito concreto para um gráfico.

## 1. O que é uma "semana"?

Essa pergunta veio antes do código. O banco grava datas em UTC e o Brasil está 3 horas atrás, então um chamado aberto às 22h de domingo em São Paulo já é segunda-feira em UTC.

| Opção | Vantagem | Desvantagem |
|---|---|---|
| **Janelas móveis de 7 dias (escolhida)** | Evita o problema do fuso, e toda janela tem 7 dias completos | Os limites mudam a cada momento |
| Semana de calendário | Mais familiar | Exige fuso no servidor, e a semana atual sempre parece fraca |

Os limites são instantes, não datas de calendário. O fuso só aparece na hora de exibir o rótulo, no navegador de quem vê.

## 2. Intervalo meio aberto: `[início, fim)`

O início entra na janela e o fim não. Assim, o fim de uma janela pode ser igual ao início da seguinte, sem deixar buracos nem contar nada em dobro. Se os dois lados fossem inclusivos, um chamado exatamente na divisa contaria duas vezes.

## 3. `SUM(CASE WHEN ...)`: agregação condicional

Contar 8 janelas com 8 `COUNT` significaria 8 idas ao banco, ou 16 com as duas séries. A agregação condicional faz tudo num único `SELECT`:

```sql
SELECT SUM(CASE WHEN criado_em >= ? AND criado_em < ? THEN 1 ELSE 0 END), -- janela 1
       SUM(CASE WHEN criado_em >= ? AND criado_em < ? THEN 1 ELSE 0 END)  -- janela 2 etc.
FROM chamado
WHERE <regra de visibilidade> AND criado_em >= ? AND criado_em < ?
```

O código está em `DashboardRepository.contarPorJanela`, escrito com a Criteria API do JPA.

Um detalhe: sem nenhuma linha no período, `SUM` devolve `NULL`, e não `0`. Por isso o código trata `null`. Outro: um chamado não resolvido tem `resolvido_em` nulo, e `NULL >= ?` não é verdadeiro, então ele fica de fora sem nenhuma regra especial.

## 4. Injetar o relógio torna o tempo testável

`DashboardService` recebe um `Clock` em vez de chamar `LocalDateTime.now()` direto. Nos testes, `DashboardSemanasIntegrationTest` fornece um relógio fixo e pode comparar limites até o microssegundo. Em produção, nada muda.

É injeção de dependência resolvendo um problema prático: o tempo vira um dado de entrada, como qualquer outro parâmetro.

## 5. Testes de mutação manuais

Para saber se um teste protege de verdade, quebre o código de propósito e veja se ele falha.

| Mutação | Teste que falhou |
|---|---|
| Fim da janela inclusivo | `windowIncludesItsStartAndExcludesItsEnd` |
| Sem a regra de visibilidade | `attendantOnlyCountsTheQueueAndTheirOwnTickets` |

## 6. Carregamento sob demanda com `React.lazy`

O Recharts é grande, e só o dashboard precisa dele. Com `lazy` e `Suspense`, o Vite gera um arquivo separado, baixado apenas quando essa tela abre.

| Pacote em gzip | Antes | Depois |
|---|---|---|
| Código principal, baixado por todos | 138,76 kB | 140,04 kB |
| Gráfico, baixado só no dashboard | não existia | 102,84 kB |

**O risco:** o download pode falhar, por exemplo logo após um deploy. Sem tratamento, o erro derrubaria a tela inteira. O `.catch` do `import()` troca o gráfico por um aviso.

## 7. Dependência indireta com versão errada

Ao instalar o Recharts, o `npm ls` mostrou o `react-is` na versão 17, herdada de uma ferramenta de teste. O Recharts o usa para inspecionar elementos React, e o React 19 mudou a marcação interna desses elementos. Por isso, `react-is` foi declarado com a mesma versão do React.

**Para levar:** depois de instalar algo, olhe o que veio junto com `npm ls`. Dependências que dizem aceitar várias versões nem sempre recebem a certa.

## 8. Gráfico acessível

Um leitor de tela não enxerga um gráfico. Por isso o desenho leva `aria-hidden` e uma tabela com a classe `sr-only` (visível só para leitores de tela) traz os mesmos números. As duas séries também se diferenciam pela forma, tracejada e sólida, e não só pela cor.

Nos testes de componente, o jsdom não desenha gráficos, e é essa tabela que confirma os números. Só o E2E, num navegador real, prova que o desenho aparece.

## 9. Nomes evitam confusão

A série se chama "Novos", e não "Abertos". O card "Abertos" significa "está nesse status agora". A série significa "foi criado nesta janela", mesmo que já tenha sido fechado ou cancelado. Dois significados com o mesmo nome levariam a uma leitura errada dos números.

## 10. Limitações conhecidas

- O gráfico do atendente é parcial: um chamado resolvido por outro atendente sai do escopo dele.
- `criado_em` e `resolvido_em` não têm índice próprio. No volume deste projeto isso não deve pesar. Se o volume crescer, a solução é uma nova migration com índices.

## Para aprofundar (opcional)

- `EXPLAIN` no MySQL: como ver se uma consulta usa índice.
- Divisão de código (*code splitting*) e como o Vite decide os arquivos gerados.
- Por que comparar instantes é mais seguro do que comparar datas de calendário entre fusos.
