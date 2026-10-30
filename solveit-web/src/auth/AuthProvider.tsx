import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { AuthContext, type DadosCadastro, type Sessao } from '@/auth/auth-context'
import { api, setUnauthorizedHandler, tokenStorage } from '@/lib/api'
import type { TokenResponse, Usuario } from '@/lib/types'

/**
 * Guarda a sessão do app. A API é sempre a autoridade: o perfil exibido vem de
 * GET /api/auth/me, nunca de dados decodificados do token no navegador.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<Sessao>(() =>
    tokenStorage.get() ? { status: 'carregando' } : { status: 'anonimo' },
  )

  const sair = useCallback(() => {
    tokenStorage.clear()
    setSessao({ status: 'anonimo' })
  }, [])

  // Qualquer 401 da API (token expirado, conta desativada) encerra a sessão.
  useEffect(() => {
    setUnauthorizedHandler(sair)
    return () => setUnauthorizedHandler(null)
  }, [sair])

  // Restauração: se havia um token salvo, confirma com a API quem é o usuário.
  useEffect(() => {
    if (!tokenStorage.get()) {
      return
    }
    let ativo = true
    api
      .get<Usuario>('/auth/me')
      .then(({ data }) => {
        if (ativo) setSessao({ status: 'autenticado', usuario: data })
      })
      .catch(() => {
        if (ativo) sair()
      })
    return () => {
      ativo = false
    }
  }, [sair])

  const entrar = useCallback(async (email: string, senha: string) => {
    const { data: token } = await api.post<TokenResponse>('/auth/login', { email, senha })
    tokenStorage.set(token.accessToken)
    const { data: usuario } = await api.get<Usuario>('/auth/me')
    setSessao({ status: 'autenticado', usuario })
    return usuario
  }, [])

  const cadastrar = useCallback(
    async ({ nome, email, senha }: DadosCadastro) => {
      await api.post<Usuario>('/auth/register', { nome, email, senha })
      return entrar(email, senha)
    },
    [entrar],
  )

  const value = useMemo(
    () => ({ sessao, entrar, cadastrar, sair }),
    [sessao, entrar, cadastrar, sair],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
