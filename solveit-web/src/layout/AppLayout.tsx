import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { Button } from '@/components/ui/button'
import type { Perfil } from '@/lib/types'
import { cn } from '@/lib/utils'

type ItemMenu = { rotulo: string; para: string }

const MENU: Record<Perfil, ItemMenu[]> = {
  SOLICITANTE: [
    { rotulo: 'Meus chamados', para: '/chamados' },
    { rotulo: 'Abrir chamado', para: '/chamados/novo' },
  ],
  ATENDENTE: [
    { rotulo: 'Fila', para: '/fila' },
    { rotulo: 'Chamados', para: '/chamados' },
    { rotulo: 'Dashboard', para: '/dashboard' },
  ],
  ADMIN: [
    { rotulo: 'Dashboard', para: '/dashboard' },
    { rotulo: 'Chamados', para: '/chamados' },
    { rotulo: 'Usuários', para: '/usuarios' },
    { rotulo: 'Categorias', para: '/categorias' },
  ],
}

const NOME_PERFIL: Record<Perfil, string> = {
  SOLICITANTE: 'Solicitante',
  ATENDENTE: 'Atendente',
  ADMIN: 'Administrador',
}

/**
 * Estrutura das telas logadas: menu lateral no desktop e barra superior com rolagem
 * horizontal no celular. Os itens dependem do perfil atual.
 */
export function AppLayout() {
  const { sessao, sair } = useAuth()
  if (sessao.status !== 'autenticado') {
    return null
  }
  const { usuario } = sessao

  return (
    <div className="flex min-h-svh flex-col bg-background text-foreground md:flex-row">
      <aside className="border-b border-border bg-sidebar md:w-60 md:shrink-0 md:border-r md:border-b-0">
        <div className="flex items-center justify-between gap-2 px-4 py-3 md:flex-col md:items-start md:py-5">
          <span className="text-lg font-semibold">
            Solve<span className="text-primary">IT</span>
          </span>
          <div className="text-right text-xs md:text-left">
            <p className="font-medium">{usuario.nome}</p>
            <p className="text-muted-foreground">{NOME_PERFIL[usuario.perfil]}</p>
          </div>
        </div>
        <nav aria-label="Menu principal" className="flex gap-1 overflow-x-auto px-2 pb-2 md:flex-col md:pb-0">
          {MENU[usuario.perfil].map((item) => (
            <NavLink
              key={item.para}
              to={item.para}
              end
              className={({ isActive }) =>
                cn(
                  'rounded-md px-3 py-2 text-sm whitespace-nowrap transition-colors hover:bg-sidebar-accent',
                  isActive && 'bg-primary/15 font-medium text-primary',
                )
              }
            >
              {item.rotulo}
            </NavLink>
          ))}
        </nav>
        <div className="hidden px-4 py-4 md:block">
          <Button variant="outline" className="w-full" onClick={sair}>
            Sair
          </Button>
        </div>
      </aside>

      <main className="flex-1 p-4 md:p-8">
        <div className="mb-4 flex justify-end md:hidden">
          <Button variant="outline" size="sm" onClick={sair}>
            Sair
          </Button>
        </div>
        <Outlet />
      </main>
    </div>
  )
}
