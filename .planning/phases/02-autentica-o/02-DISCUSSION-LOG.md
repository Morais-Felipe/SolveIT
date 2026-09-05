# Phase 2: Autenticação - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-05
**Phase:** 2-Autenticação
**Areas discussed:** Cadastro e entrada, duração e renovação do JWT, contas inativas e permissões, respostas da API

---

## Cadastro e entrada

| Decisão | Opções apresentadas | Escolha |
|---------|---------------------|---------|
| Comportamento após cadastro | Cadastro separado; login automático; confirmação sem dados | Cadastro separado do login, com resposta segura |
| Política de senha | Comprimento com caracteres livres; regras obrigatórias de composição | Mínimo de 15 caracteres e máximo de 72 bytes, sem composição obrigatória |
| Tratamento do e-mail | Normalização e mensagens diferentes por contexto; preservar entrada literal | Remover espaços externos, converter para minúsculas, informar duplicidade apenas no cadastro |
| Ativação e perfil | Ativação imediata; aprovação administrativa; confirmação por e-mail | Conta ativa imediatamente e sempre `SOLICITANTE` |

**User's choice:** Aprovou todas as recomendações após explicação dos conceitos e das consequências.
**Notes:** O usuário pediu explicações antes de escolher. A separação entre criação da conta e comprovação da identidade foi usada como base pedagógica.

---

## Duração e renovação do JWT

| Decisão | Opções apresentadas | Escolha |
|---------|---------------------|---------|
| Validade | 30 minutos; 1 hora; 8 horas | 1 hora, configurável por ambiente |
| Renovação | Novo login após expiração; refresh token | Novo login; sem refresh token na primeira versão |
| Transporte | Cabeçalho `Authorization: Bearer`; cookie `HttpOnly` | `Authorization: Bearer` |
| Conteúdo e autoridade | Identidade mínima com banco como autoridade; perfil e dados no token | `sub`, `iat`, `exp` e `iss`; perfil e ativação vêm do banco |

**User's choice:** Aprovou JWT curto, sem renovação persistente e enviado explicitamente pelo Axios à API.
**Notes:** O armazenamento no navegador foi preservado para a Fase 6. Foi explicado que stateless significa ausência de sessão HTTP mantida pelo servidor, não ausência de consulta ao banco.

---

## Contas inativas e permissões

| Decisão | Opções apresentadas | Escolha |
|---------|---------------------|---------|
| Desativação ou mudança de perfil | Aplicar imediatamente; aguardar expiração do token | Aplicar o estado atual do banco na próxima requisição |
| Reativação | Voltar a aceitar token ainda válido; exigir novo login | Voltar a aceitar token ainda dentro de uma hora |
| Política de rotas | Proteção por padrão; liberação individual | Cadastro e login públicos; demais rotas protegidas |
| Autoridade de acesso | Servidor; somente restrições da interface | Servidor valida perfil e relação com o recurso |

**User's choice:** Aprovou efeito imediato do estado da conta e a reativação simples compatível com o MVP.
**Notes:** Revogação persistente exigiria estado adicional de sessão e ficou fora desta versão.

---

## Respostas da API

| Decisão | Opções apresentadas | Escolha |
|---------|---------------------|---------|
| Resposta do login | Token e metadados; token junto com usuário | `accessToken`, `tokenType` e `expiresIn`; usuário consultado em `/me` |
| Formato de erro | RFC 9457 Problem Details; JSON próprio | Problem Details com `code` e `errors` |
| Status HTTP | Status específico por classe; um status genérico | `400`, `409`, `401` e `403` conforme a semântica |
| Detalhe de autenticação | Mensagens seguras por situação; mensagem única | Códigos estáveis, login genérico e mensagens úteis para token expirado ou inválido |

**User's choice:** Aprovou o padrão Problem Details e destacou positivamente a mensagem “Sua sessão expirou. Entre novamente para continuar.”
**Notes:** O front-end deve interpretar `code`, não o texto humano. Respostas e logs não podem expor senha, JWT completo, stack trace, SQL ou classes internas.

---

## the agent's Discretion

- Nomes internos de classes e DTOs.
- Parâmetros criptográficos seguros, após pesquisa técnica da fase.
- Representação exata de extensões do Problem Details, preservando o contrato discutido.

## Deferred Ideas

- Refresh token, rotação e revogação persistente.
- Armazenamento do JWT no navegador, a discutir na Fase 6.
- Recuperação de senha, confirmação por e-mail e MFA.
