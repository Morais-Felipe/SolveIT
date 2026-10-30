import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '@/App'
import { AuthProvider } from '@/auth/AuthProvider'
import { api, ApiError, tokenStorage } from '@/lib/api'
import type { Categoria, Chamado, Pagina, Usuario } from '@/lib/types'

const SOLICITANTE: Usuario = {
  id: 2,
  nome: 'Sol',
  email: 'sol@example.com',
  perfil: 'SOLICITANTE',
  ativo: true,
  criadoEm: '2026-10-30T10:00:00',
}

const CATEGORIAS: Categoria[] = [
  { id: 1, nome: 'Rede', descricao: null, ativo: true, versao: 0 },
  { id: 2, nome: 'Hardware', descricao: null, ativo: true, versao: 0 },
]

function chamado(id: number, titulo: string, extra: Partial<Chamado> = {}): Chamado {
  return {
    id,
    titulo,
    descricao: 'Descrição',
    status: 'ABERTO',
    prioridade: 'MEDIA',
    solicitante: { id: 2, nome: 'Sol' },
    atendente: null,
    categoria: { id: 1, nome: 'Rede', ativo: true },
    criadoEm: '2026-10-30T10:00:00',
    atualizadoEm: '2026-10-30T10:00:00',
    resolvidoEm: null,
    versao: 0,
    ...extra,
  }
}

function pagina(items: Chamado[], extra: Partial<Pagina<Chamado>> = {}): Pagina<Chamado> {
  return { items, page: 0, size: 10, totalElements: items.length, totalPages: items.length ? 1 : 0, ...extra }
}

type Rotas = { chamados?: () => Promise<unknown> }

/** Simula a API por URL: /auth/me, /categorias e /chamados. */
function simularApi(rotas: Rotas = {}) {
  return vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
    if (url === '/auth/me') return { data: SOLICITANTE }
    if (url === '/categorias') return { data: { items: CATEGORIAS, page: 0, size: 100, totalElements: 2, totalPages: 1 } }
    if (url === '/chamados') return rotas.chamados ? rotas.chamados() : { data: pagina([]) }
    throw new Error(`GET inesperado: ${url}`)
  })
}

function renderApp(rota: string): void {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[rota]}>
      <AuthProvider>{children}</AuthProvider>
    </MemoryRouter>
  )
  render(<App />, { wrapper })
}

/** Parâmetros enviados na última chamada GET /chamados. */
function ultimosParametros(get: ReturnType<typeof simularApi>) {
  const chamadas = get.mock.calls.filter(([url]) => url === '/chamados')
  return (chamadas.at(-1)?.[1] as { params: Record<string, unknown> }).params
}

beforeEach(() => {
  localStorage.clear()
  tokenStorage.set('token-valido')
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('lista de chamados', () => {
  it('mostra os chamados do solicitante com status e prioridade', async () => {
    simularApi({
      chamados: async () => ({
        data: pagina([
          chamado(10, 'Impressora sem papel', { prioridade: 'ALTA' }),
          chamado(11, 'VPN cai à noite', { status: 'EM_ANDAMENTO' }),
        ]),
      }),
    })
    renderApp('/chamados')

    expect(await screen.findAllByText('Impressora sem papel')).not.toHaveLength(0)
    expect(screen.getAllByText('VPN cai à noite')).not.toHaveLength(0)
    expect(screen.getAllByText('Em andamento')).not.toHaveLength(0)
    expect(screen.getAllByText('Alta')).not.toHaveLength(0)
    expect(screen.getByRole('link', { name: 'Novo chamado' })).toBeInTheDocument()
  })

  it('envia o filtro escolhido e volta para a primeira página', async () => {
    const usuarioEvento = userEvent.setup()
    const get = simularApi({
      chamados: async () => ({ data: pagina([chamado(10, 'Impressora')], { page: 1, totalPages: 3, totalElements: 21 }) }),
    })
    renderApp('/chamados?page=1')
    await screen.findAllByText('Impressora')
    expect(ultimosParametros(get)).toMatchObject({ page: 1 })

    await usuarioEvento.selectOptions(screen.getByLabelText('Status'), 'RESOLVIDO')

    expect(ultimosParametros(get)).toMatchObject({ status: 'RESOLVIDO', page: 0 })
  })

  it('avança de página', async () => {
    const usuarioEvento = userEvent.setup()
    const get = simularApi({
      chamados: async () => ({ data: pagina([chamado(10, 'Impressora')], { totalPages: 2, totalElements: 11 }) }),
    })
    renderApp('/chamados')
    await screen.findAllByText('Impressora')

    await usuarioEvento.click(screen.getByRole('button', { name: 'Próxima' }))

    expect(ultimosParametros(get)).toMatchObject({ page: 1 })
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument()
  })

  it('diferencia lista vazia de filtro sem resultados', async () => {
    simularApi()
    renderApp('/chamados')
    expect(await screen.findByText('Você ainda não abriu nenhum chamado.')).toBeInTheDocument()
  })

  it('avisa quando o filtro não encontra nada', async () => {
    simularApi()
    renderApp('/chamados?prioridade=CRITICA')
    expect(await screen.findByText('Nenhum chamado encontrado para estes filtros.')).toBeInTheDocument()
  })

  it('mostra o erro da API e permite tentar novamente', async () => {
    const usuarioEvento = userEvent.setup()
    let tentativas = 0
    simularApi({
      chamados: async () => {
        tentativas += 1
        if (tentativas === 1) {
          throw new ApiError(0, 'NETWORK_ERROR', 'Não foi possível conectar ao servidor. Tente novamente.', {})
        }
        return { data: pagina([chamado(10, 'Impressora')]) }
      },
    })
    renderApp('/chamados')

    const alerta = await screen.findByRole('alert')
    expect(within(alerta).getByText(/não foi possível conectar/i)).toBeInTheDocument()

    await usuarioEvento.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findAllByText('Impressora')).not.toHaveLength(0)
  })
})
