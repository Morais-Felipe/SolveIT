import { CirclePlus, Inbox, LayoutDashboard, Tags, Ticket, Users, type LucideIcon } from 'lucide-react'
import { useEffect } from 'react'
import { NavLink, Outlet, useLocation, useNavigationType } from 'react-router-dom'
import { useAuth } from '@/auth/useAuth'
import { Marca } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import type { Perfil } from '@/lib/types'
import { cn } from '@/lib/utils'

type ItemMenu = { rotulo: string; para: string; icone: LucideIcon }

const MENU: Record<Perfil, ItemMenu[]> = {
  SOLICITANTE: [
    { rotulo: 'Meus chamados', para: '/chamados', icone: Ticket },
    { rotulo: 'Abrir chamado', para: '/chamados/novo', icone: CirclePlus },
  ],
  ATENDENTE: [
    { rotulo: 'Fila', para: '/fila', icone: Inbox },
    { rotulo: 'Chamados', para: '/chamados', icone: Ticket },
    { rotulo: 'Dashboard', para: '/dashboard', icone: LayoutDashboard },
  ],
  ADMIN: [
    { rotulo: 'Dashboard', para: '/dashboard', icone: LayoutDashboard },
    { rotulo: 'Chamados', para: '/chamados', icone: Ticket },
    { rotulo: 'Usuários', para: '/usuarios', icone: Users },
    { rotulo: 'Categorias', para: '/categorias', icone: Tags },
  ],
}

const NOME_PERFIL: Record<Perfil, string> = {
  SOLICITANTE: 'Solicitante',
  ATENDENTE: 'Atendente',
  ADMIN: 'Administrador',
}

/** Primeira letra do primeiro e do último nome: "Ada Lovelace" vira "AL". */
function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/)
  const letras = partes.length > 1 ? [partes[0], partes[partes.length - 1]] : partes
  return letras.map((parte) => parte.charAt(0).toUpperCase()).join('')
}

/**
 * Estrutura das telas logadas: menu lateral fixo no desktop e barra superior no celular, que
 * quebra linha quando os itens não cabem (nenhum fica escondido). Os itens dependem do perfil
 * atual. O bloco do usuário existe uma só vez: o CSS o põe no topo do celular e no rodapé do menu.
 */
export function AppLayout() {
  const { sessao, sair } = useAuth()
  const { pathname } = useLocation()
  const navegacao = useNavigationType()

  // Tela nova começa no topo; "Voltar" (POP) deixa o navegador devolver a posição anterior.
  useEffect(() => {
    if (navegacao !== 'POP') {
      window.scrollTo({ top: 0, behavior: 'instant' })
    }
  }, [pathname, navegacao])

  if (sessao.status !== 'autenticado') {
    return null
  }
  const { usuario } = sessao

  return (
    <div className="flex min-h-svh flex-col bg-background text-foreground md:flex-row">
      <aside className="grid grid-cols-[1fr_auto] items-center border-b border-border bg-sidebar md:sticky md:top-0 md:flex md:h-svh md:w-60 md:shrink-0 md:flex-col md:items-stretch md:overflow-y-auto md:border-r md:border-b-0">
        <Marca className="px-4 py-3 text-lg font-semibold tracking-tight md:py-5" />
        <nav
          aria-label="Menu principal"
          className="col-span-2 row-start-2 flex flex-wrap gap-1 px-2 pb-2 md:flex-col md:flex-nowrap md:pb-0"
        >
          {MENU[usuario.perfil].map((item) => (
            <NavLink
              key={item.para}
              to={item.para}
              end
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 rounded-md px-3 py-2 text-sm whitespace-nowrap transition-[color,background-color,transform] duration-(--duracao-rapida) ease-(--curva-saida) hover:bg-sidebar-accent active:scale-[0.98]',
                  isActive && 'bg-primary/15 font-medium text-primary md:shadow-[inset_2px_0_0_var(--primary)]',
                )
              }
            >
              <item.icone aria-hidden="true" className="size-4 shrink-0" />
              {item.rotulo}
            </NavLink>
          ))}
        </nav>
        <div className="col-start-2 row-start-1 flex min-w-0 items-center gap-2 px-4 py-3 md:mt-auto md:border-t md:border-border">
          <span
            aria-hidden="true"
            className="grid size-8 shrink-0 place-items-center rounded-full bg-primary/15 text-xs font-semibold text-primary"
          >
            {iniciais(usuario.nome)}
          </span>
          <div className="min-w-0 text-xs">
            <p className="truncate font-medium">{usuario.nome}</p>
            <p className="text-muted-foreground">{NOME_PERFIL[usuario.perfil]}</p>
          </div>
        </div>
        <div className="hidden px-4 pb-4 md:block">
          <Button variant="outline" className="w-full" onClick={sair}>
            Sair
          </Button>
        </div>
      </aside>

      <main className="min-w-0 flex-1 p-4 md:p-8">
        <div className="mb-4 flex justify-end md:hidden">
          <Button variant="outline" size="sm" onClick={sair}>
            Sair
          </Button>
        </div>
        {/* A chave reinicia a entrada a cada tela; mudar só filtros (?status=) não troca a chave. */}
        <div key={pathname} className="entrada-suave">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
