# Estudo — Ticket 37: teste de login confiável

Notas do plano aprovado em 2026-11-02. O teste `AuthLoginIntegrationTest.returnsTheSameSafeProblemForEveryInvalidCredential` falhou uma vez no CI da `main` e passou ao repetir, com o mesmo código.

## 1. O que o teste protege

Além da resposta HTTP, ele verifica que o log da requisição não contém senha, hash, segredo do JWT nem detalhes internos, como `Exception`, `java.` e `org.`.

O `CapturedOutput` captura a saída do **processo inteiro**, não só da requisição. Por isso, o que qualquer outra thread escrever durante o teste entra na verificação.

## 2. O que aconteceu

Um aviso de outro pool de conexões caiu na janela da requisição:

```text
[onnection-adder] HikariPool-1 - Pool is empty, failed to create/setup connection
com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
```

O teste estava certo ao recusar esse texto. O problema é que a origem não era o login.

## 3. Por que havia pools sem banco

1. **Cache de contextos:** o Spring guarda cada contexto de teste (o conjunto de beans) para reaproveitá-lo. O limite padrão é de 32.
2. **Nenhum reaproveitamento:** cada classe tem o próprio `static MySQLContainer`, então cada uma tem o próprio contexto. Com 28 classes, quase nenhum saía do cache.
3. **Ordem de encerramento:** o `@Testcontainers` encerra o MySQL no fim da classe, mas o contexto continua aberto até o fim da JVM.
4. **Pool insistente:** o Hikari continua validando e tentando repor as conexões de um banco que já não existe.

A execução que falhou teve 238 avisos `Failed to validate connection` e 77 `Pool is empty`. Só os 77 trazem stack trace, com as palavras proibidas. A janela do teste é curta, por isso a falha era rara.

## 4. Opções comparadas

| Opção | O que faz | Problema |
|---|---|---|
| Filtrar por thread | Só olha as linhas da thread da requisição | Muda a asserção, deixa passar uma senha logada em outra thread e só protege este teste |
| `@DirtiesContext` em cada classe | Fecha o contexto ao fim da classe | Altera 27 arquivos, e uma classe nova pode esquecer |
| **Limitar o cache a 1 (escolhida)** | Fecha o contexto anterior ao guardar o da próxima classe | Classes que compartilhassem contexto o recarregariam, mas hoje nenhuma compartilha |
| Configurar o Hikari para não validar | Reduz os avisos | Esconde o sintoma, e os pools continuam vivos |

## 5. A correção

Uma linha em `solveit-api/src/test/resources/spring.properties`:

```properties
spring.test.context.cache.maxSize=1
```

Esse arquivo é lido pelo próprio Spring, diferente do `application-test.yml`, que só vale depois que o contexto já está subindo. O cache de contextos existe antes disso, então a configuração precisa estar aqui. Como fica em `src/test/resources`, não entra no JAR da aplicação.

Nenhuma asserção mudou. Por isso, a correção também vale para os outros testes que leem a mesma captura.

**Risco restante:** outra thread ainda pode escrever durante a janela. Se isso acontecer, vale investigar: sem pools antigos, não é mais o ruído conhecido.

## 6. Como foi verificado

| Verificação | Resultado |
|---|---|
| Mutação: logar a senha em `AuthService.login` | 4 de 7 testes falharam, pelo motivo certo |
| Suíte completa | 248 testes, 0 falhas |
| `Pool is empty` | de 77 para 0 |
| `Failed to validate connection` | de 238 para 0 |

**Mutação** é colocar um defeito de propósito para ver se o teste percebe. Ela foi desfeita antes da suíte completa e não entrou em nenhum commit.

As contagens de avisos pesam mais que o verde da suíte. Como a falha era rara, uma execução verde sozinha não provaria nada; já o fim dos avisos mostra que a fonte do ruído sumiu.

**Para levar:** antes de afrouxar um teste instável, descubra de onde vem a instabilidade. Aqui, corrigir a origem manteve a proteção intacta e ainda resolveu o mesmo risco em outros testes.

## Para aprofundar (opcional)

- Cache de contextos do Spring TestContext e como a chave de cada contexto é montada.
- Hikari: `keepaliveTime`, `maxLifetime` e como o pool repõe conexões.
- Contêineres por classe ou um único contêiner compartilhado pela suíte, o que reduziria o tempo total.
