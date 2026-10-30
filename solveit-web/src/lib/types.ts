// Tipos que espelham os DTOs da API Spring (pacote com.solveit.dto).
// Se um contrato mudar no back-end, este é o lugar a atualizar.

export type Perfil = 'SOLICITANTE' | 'ATENDENTE' | 'ADMIN'

/** Espelha UsuarioResponse (GET /api/auth/me, POST /api/auth/register). */
export type Usuario = {
  id: number
  nome: string
  email: string
  perfil: Perfil
  ativo: boolean
  criadoEm: string
}

/** Espelha TokenResponse (POST /api/auth/login). */
export type TokenResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
}

/** Corpo de erro da API (RFC 9457 Problem Details + campos do SolveIT). */
export type ProblemDetail = {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  errors?: Record<string, string>
}
