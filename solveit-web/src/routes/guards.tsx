import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { rotaInicial, useAuth } from '@/auth/useAuth'
import type { Perfil } from '@/lib/types'

/**
 * Guardas de navegação. Eles só organizam a experiência: a autorização de verdade é
 * sempre da API, que recusa com 403 qualquer acesso indevido.
 */

export function TelaCarregando() {
  return (
    <div role="status" className="flex min-h-svh items-center justify-center text-muted-foreground">
      Carregando…
    </div>
  )
}

/** Exige sessão; sem ela, vai para /login lembrando de onde a pessoa veio. */
export function RotaProtegida({ children }: { children: ReactNode }) {
  const { sessao } = useAuth()
  const location = useLocation()

  if (sessao.status === 'carregando') {
    return <TelaCarregando />
  }
  if (sessao.status === 'anonimo') {
    return <Navigate to="/login" replace state={{ de: location.pathname }} />
  }
  return children
}

/** Login e cadastro: quem já está logado vai para a tela inicial do seu perfil. */
export function SomenteAnonimo({ children }: { children: ReactNode }) {
  const { sessao } = useAuth()

  if (sessao.status === 'carregando') {
    return <TelaCarregando />
  }
  if (sessao.status === 'autenticado') {
    return <Navigate to={rotaInicial(sessao.usuario.perfil)} replace />
  }
  return children
}

/** Tela que só faz sentido para alguns perfis; os demais voltam para a inicial. */
export function RotaPorPerfil({ perfis, children }: { perfis: Perfil[]; children: ReactNode }) {
  const { sessao } = useAuth()

  if (sessao.status !== 'autenticado') {
    return null
  }
  if (!perfis.includes(sessao.usuario.perfil)) {
    return <Navigate to={rotaInicial(sessao.usuario.perfil)} replace />
  }
  return children
}

export function RedirecionarParaInicial() {
  const { sessao } = useAuth()
  if (sessao.status !== 'autenticado') {
    return <Navigate to="/login" replace />
  }
  return <Navigate to={rotaInicial(sessao.usuario.perfil)} replace />
}
