import { createContext } from 'react'
import type { Usuario } from '@/lib/types'

/**
 * Situação da sessão:
 * - carregando: o app acabou de abrir e está validando um token salvo;
 * - anonimo: ninguém logado;
 * - autenticado: há um usuário confirmado pela API.
 */
export type Sessao =
  | { status: 'carregando' }
  | { status: 'anonimo' }
  | { status: 'autenticado'; usuario: Usuario }

export type DadosCadastro = { nome: string; email: string; senha: string }

export type AuthContextValue = {
  sessao: Sessao
  entrar: (email: string, senha: string) => Promise<Usuario>
  cadastrar: (dados: DadosCadastro) => Promise<Usuario>
  sair: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
