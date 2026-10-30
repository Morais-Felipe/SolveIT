import axios, { type AxiosError } from 'axios'
import type { ProblemDetail } from '@/lib/types'

const TOKEN_KEY = 'solveit.token'

/**
 * Onde o token JWT fica guardado (decisão D-1: localStorage, para sobreviver a uma
 * recarga da página). Acesso protegido por try/catch: em modo privado ou com
 * armazenamento bloqueado, o app continua funcionando, só sem lembrar a sessão.
 */
export const tokenStorage = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token: string): void {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch {
      // armazenamento indisponível: a sessão vale só até recarregar
    }
  },
  clear(): void {
    try {
      localStorage.removeItem(TOKEN_KEY)
    } catch {
      // nada a limpar
    }
  },
}

/** Erro padronizado para as telas: mensagem pronta e erros por campo. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

/** Converte qualquer falha de requisição em ApiError, sem expor detalhes internos. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }
  const axiosError = error as AxiosError<ProblemDetail>
  const response = axiosError?.response
  if (!response) {
    return new ApiError(0, 'NETWORK_ERROR', 'Não foi possível conectar ao servidor. Tente novamente.', {})
  }
  const problem = (typeof response.data === 'object' && response.data) || {}
  if (response.status >= 500 && !problem.code) {
    // Sem Problem Details: a API está fora do ar ou um proxy respondeu no lugar dela.
    return new ApiError(
      response.status,
      'SERVER_UNAVAILABLE',
      'O servidor não respondeu. Tente novamente em instantes.',
      {},
    )
  }
  return new ApiError(
    response.status,
    problem.code ?? 'UNEXPECTED_ERROR',
    problem.detail ?? problem.title ?? 'Ocorreu um erro inesperado. Tente novamente.',
    problem.errors ?? {},
  )
}

let onUnauthorized: (() => void) | null = null

/** O AuthContext registra aqui como reagir a um 401 (encerrar a sessão). */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

/** Única instância HTTP do app. Em desenvolvimento, /api passa pelo proxy do Vite. */
export const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = toApiError(error)
    if (apiError.status === 401 && tokenStorage.get()) {
      onUnauthorized?.()
    }
    return Promise.reject(apiError)
  },
)
