# Estudo — Ticket 40: modo demonstração

Notas do plano aprovado em 2026-11-02. O objetivo foi permitir que outra pessoa teste o SolveIT com um comando, sem enfraquecer o sistema normal.

## 1. O conflito: facilidade contra segurança

O projeto proíbe senha padrão e atalho para virar ADMIN, porque quem conhece o atalho controla o sistema. Mas o portfólio precisa ser fácil de testar. Uma conta fixa no sistema normal resolveria a facilidade e criaria o risco.

A saída foi **separar os dois mundos**, em vez de abrir uma exceção dentro do sistema:

| | Modo normal | Modo demonstração |
|---|---|---|
| Comando | `docker compose up` | `docker compose -f docker-compose.demo.yml up` |
| Segredos | `.env`, fora do Git | Públicos, no próprio arquivo |
| Contas prontas | Nenhuma | Três, documentadas |
| Banco | Volume `solveit` | Volume `solveit-demo` |
| Porta | 8080 | 8090 |

## 2. Por que os dados ficam fora da imagem

O Flyway pode ler mais de uma pasta. A configuração do modo demonstração acrescenta uma:

```yaml
SPRING_FLYWAY_LOCATIONS: classpath:db/migration,filesystem:/demo
volumes:
  - ./demo:/demo:ro
```

O arquivo `demo/V1000__dados_demonstracao.sql` chega à API por um volume, somente nesse compose. Como a imagem é construída a partir de `solveit-api/`, a pasta `demo/` nem faz parte do que entra nela. A garantia vem da estrutura, não de um `if` no código. Foi conferido no `app.jar`: 9 migrations normais e nenhuma de demonstração.

**Para levar:** quando possível, torne o erro impossível em vez de apenas proibi-lo.

## 3. Hash da senha

O SQL guarda só o hash BCrypt. Para gerá-lo, a própria API fez um cadastro temporário num banco descartável, com o mesmo algoritmo e o mesmo custo que a aplicação usa. Assim não foi preciso instalar uma biblioteca só para isso.

A senha continua pública, porque está documentada. Isso é aceitável apenas porque ela só existe num banco local de demonstração.

## 4. Datas relativas

Datas fixas fariam o gráfico nascer vazio algumas semanas depois. Por isso o SQL calcula tudo a partir do momento da subida:

```sql
SET @agora = UTC_TIMESTAMP(6);
-- ...
@agora - INTERVAL 1440 MINUTE
```

As datas são fixadas na primeira subida. Depois de semanas, o gráfico vai esvaziando; `down -v` e uma nova subida renovam os dados.

## 5. Provar os dois lados

Testar só que a demonstração funciona não bastaria. O que mais importa é o outro lado:

| O que precisa ser verdade | Como é provado |
|---|---|
| As três contas entram na demonstração | `demo/verificar.sh`, no job de CI `Demonstração` |
| O dashboard vem preenchido | O mesmo script |
| A conta de demonstração não existe no modo normal | E2E: o login precisa receber 401 |
| O modo normal começa sem nenhum ADMIN | Já exigido pelo `global-setup` do E2E |

## 6. Limites

- Docker continua obrigatório. Só uma demonstração online eliminaria isso.
- **Nunca publique este modo na internet.** Senhas e chave de assinatura dos tokens são públicas. Uma demonstração online precisaria de segredos próprios, sem contas com senha conhecida, ou com limites e reinício periódico.

## Para aprofundar (opcional)

- Flyway: `locations`, migrations repetíveis e callbacks.
- Volumes do Docker: nomeados (`mysql-data`) e de pasta (`./demo`).
- Perfis do Spring e por que não foram usados aqui para carregar os dados.
