import { useContext } from 'react'
import { AuthContext, type AuthContextValue } from '@/auth/auth-context'
import type { Perfil } from '@/lib/types'

export function useAuth(): AuthContextValue {
  const contexto = useContext(AuthContext)
  if (!contexto) {
    throw new Error('useAuth precisa estar dentro de <AuthProvider>')
  }
  return contexto
}

/** Tela inicial de cada perfil depois do login. */
export function rotaInicial(perfil: Perfil): string {
  switch (perfil) {
    case 'SOLICITANTE':
      return '/chamados'
    case 'ATENDENTE':
      return '/fila'
    case 'ADMIN':
      return '/dashboard'
  }
}
