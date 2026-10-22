# Procedimento: criar o primeiro ADMIN

> **Status:** procedimento operacional **proposto**. Ele nunca é executado automaticamente pela aplicação. A primeira execução real em qualquer ambiente exige aprovação explícita do responsável.

## Por que existe este procedimento

O SolveIT não tem endpoint público, *runner* de inicialização nem senha padrão capazes de criar um administrador:

- `POST /api/auth/register` cria **sempre** um `SOLICITANTE`.
- `POST /api/usuarios` cria qualquer perfil, mas **exige** um ADMIN autenticado.

Isso é intencional. Qualquer atalho automático (usuário `admin/admin`, variável de ambiente que promove alguém na inicialização, rota de *setup*) seria uma porta para alguém se tornar administrador sem controle. Num banco novo, então, não existe nenhum ADMIN. Este documento descreve a única forma prevista de criar o primeiro: uma intervenção manual, local, auditável e restrita a uma linha.

## Pré-requisitos

- Acesso administrativo **local** ao MySQL do ambiente (cliente `mysql` ou equivalente), sem expor o banco à internet para isso.
- A aplicação em execução, com as migrations do Flyway aplicadas até a versão atual (V8 ou superior).
- Um operador que vai conferir cada resultado antes do `COMMIT`.

Não registre neste repositório senhas, e-mails reais ou credenciais do banco.

## Passo a passo

### 1. Cadastrar a pessoa normalmente

A futura administradora se cadastra pela rota pública `POST /api/auth/register`, com a própria senha. Assim a senha passa pela validação normal e é guardada em BCrypt. **Ninguém** digita ou vê a senha neste procedimento.

### 2. Confirmar o ID e o e-mail exatos

```sql
SELECT id, email, perfil, ativo, version
FROM usuario
WHERE email = 'pessoa@exemplo.com';
```

Anote o `id`. Confirme que existe **exatamente uma** linha, que `perfil = 'SOLICITANTE'` e que `ativo = 1`. Se algo divergir, pare.

### 3. Promover dentro de uma transação

Substitua `:id` e `:email` pelos valores confirmados no passo 2.

```sql
START TRANSACTION;

-- (a) Mesmo lock global que a API usa nas escritas do core. Enquanto esta
--     transação estiver aberta, nenhuma escrita concorrente da aplicação avança.
SELECT id FROM core_write_guard WHERE id = 1 FOR UPDATE;

-- (b) Só prossiga se este resultado for 0. Se já existe ADMIN ativo, este
--     procedimento não é o caminho: use a administração normal da aplicação.
SELECT COUNT(*) AS admins_ativos
FROM usuario
WHERE perfil = 'ADMIN' AND ativo = TRUE;

-- (c) Promove somente aquele usuário, identificado por ID e e-mail.
--     Incrementar version invalida formulários abertos com a versão antiga.
UPDATE usuario
SET perfil = 'ADMIN',
    version = version + 1
WHERE id = :id
  AND email = :email
  AND perfil = 'SOLICITANTE'
  AND ativo = TRUE;

-- (d) Deve retornar exatamente 1.
SELECT ROW_COUNT() AS linhas_alteradas;
```

### 4. Decidir: `COMMIT` ou `ROLLBACK`

- Se `admins_ativos` foi `0` **e** `linhas_alteradas` foi `1`, execute `COMMIT;`.
- Em **qualquer** outro caso, execute `ROLLBACK;` e investigue antes de tentar de novo.

### 5. Verificar pela API

A pessoa promovida faz login normalmente. O perfil é relido do banco a cada requisição, então não é preciso emitir um token novo. Confirme que `GET /api/auth/me` mostra `"perfil": "ADMIN"` e que `GET /api/usuarios` responde `200`.

## Por que cada detalhe importa

| Detalhe | Risco que evita |
|---|---|
| Cadastro pela rota pública | Senha digitada ou exposta pelo operador; hash gerado fora do BCrypt configurado |
| `SELECT ... FOR UPDATE` no `core_write_guard` | Escrita concorrente da aplicação durante a promoção |
| Contar ADMINs ativos antes | Usar este atalho quando já existe administração normal |
| `WHERE id AND email AND perfil AND ativo` | Promover a pessoa errada ou uma conta inativa |
| `ROW_COUNT() = 1` antes do `COMMIT` | Alterar zero ou várias linhas sem perceber |
| `version = version + 1` | Formulário aberto antes da promoção sobrescrever o estado novo |

## O que este procedimento não faz

- Não cria usuário com senha definida pelo operador.
- Não é executado por migration, *seed* ou código da aplicação.
- Não substitui a futura troca de perfil pela API (plano 03-10), que é o caminho normal depois que existe um ADMIN.
