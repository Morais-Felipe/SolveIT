import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '@/App'
import { AuthProvider } from '@/auth/AuthProvider'
import { api, ApiError, tokenStorage } from '@/lib/api'
import type { Chamado, Pagina, Usuario } from '@/lib/types'

const ATENDENTE: Usuario = {
  id: 5,
  nome: 'Ari Atendente',
  email: 'ari@example.com',
  perfil: 'ATENDENTE',
  ativo: true,
  criadoEm: '2026-10-30T10:00:00',
}

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
    versao: 3,
    ...extra,
  }
}

function pagina(items: Chamado[]): Pagina<Chamado> {
  return { items, page: 0, size: 10, totalElements: items.length, totalPages: items.length ? 1 : 0 }
}

/** Simula a API: a lista de /chamados vem da função recebida, o resto é fixo. */
function simularApi(listar: () => Promise<unknown>) {
  return vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
    if (url === '/auth/me') return { data: ATENDENTE }
    if (url === '/chamados') return listar()
    if (url === '/chamados/7') return { data: chamado(7, 'VPN cai', { status: 'EM_ANDAMENTO', atendente: { id: 5, nome: 'Ari Atendente' } }) }
    if (url === '/chamados/7/comentarios' || url === '/chamados/7/historico') return { data: [] }
    throw new Error(`GET inesperado: ${url}`)
  })
}

/** Parâmetros da última chamada GET /chamados. */
function ultimosParametros(get: ReturnType<typeof simularApi>) {
  const ultima = get.mock.calls.filter(([url]) => url === '/chamados').at(-1)
  if (!ultima) throw new Error('GET /chamados não foi chamado')
  return (ultima[1] as { params: Record<string, unknown> }).params
}

function renderApp(rota: string): void {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[rota]}>
      <AuthProvider>{children}</AuthProvider>
    </MemoryRouter>
  )
  render(<App />, { wrapper })
}

beforeEach(() => {
  localStorage.clear()
  tokenStorage.set('token-valido')
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('fila do atendente', () => {
  it('mostra os chamados abertos sem responsável, do mais antigo para o mais novo', async () => {
    const get = simularApi(async () => ({ data: pagina([chamado(7, 'VPN cai', { prioridade: 'CRITICA' })]) }))
    renderApp('/fila')

    expect(await screen.findAllByText('VPN cai')).not.toHaveLength(0)
    expect(screen.getByRole('tab', { name: 'Fila', selected: true })).toBeInTheDocument()
    expect(screen.getAllByText('Crítica')).not.toHaveLength(0)
    expect(ultimosParametros(get)).toMatchObject({ status: 'ABERTO', sort: 'criadoEm,asc', page: 0 })
    expect(ultimosParametros(get).atendenteId).toBeUndefined()
  })

  it('assume o chamado para si e abre o detalhe', async () => {
    const usuarioEvento = userEvent.setup()
    simularApi(async () => ({ data: pagina([chamado(7, 'VPN cai')]) }))
    const patch = vi.spyOn(api, 'patch').mockResolvedValue({
      data: chamado(7, 'VPN cai', { status: 'EM_ANDAMENTO', atendente: { id: 5, nome: 'Ari Atendente' }, versao: 4 }),
    })
    renderApp('/fila')

    await usuarioEvento.click(await screen.findByRole('button', { name: 'Assumir VPN cai' }))

    expect(patch).toHaveBeenCalledWith('/chamados/7/atendente', { atendenteId: 5, versaoEsperada: 3 })
    expect(await screen.findByText('Você assumiu o chamado #7.')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'VPN cai' })).toBeInTheDocument()
  })

  // O servidor checa a versão antes do estado: quem perde a corrida recebe STALE_RESOURCE.
  it('avisa quando outro atendente assumiu primeiro e recarrega a fila', async () => {
    const usuarioEvento = userEvent.setup()
    let leituras = 0
    simularApi(async () => {
      leituras += 1
      return { data: pagina(leituras === 1 ? [chamado(7, 'VPN cai')] : []) }
    })
    vi.spyOn(api, 'patch').mockRejectedValue(
      new ApiError(409, 'STALE_RESOURCE', 'O recurso foi alterado por outra operação.', {}),
    )
    renderApp('/fila')

    await usuarioEvento.click(await screen.findByRole('button', { name: 'Assumir VPN cai' }))

    expect(
      await screen.findByText('Este chamado mudou ou já foi assumido por outro atendente. A fila foi atualizada.'),
    ).toBeInTheDocument()
    expect(await screen.findByText('A fila está vazia: nenhum chamado aguardando atendimento.')).toBeInTheDocument()
  })

  it('mostra os meus chamados em andamento na outra aba, sem botão de assumir', async () => {
    const usuarioEvento = userEvent.setup()
    const get = simularApi(async () => ({
      data: pagina([chamado(8, 'Impressora', { status: 'EM_ANDAMENTO', atendente: { id: 5, nome: 'Ari Atendente' } })]),
    }))
    renderApp('/fila')
    await screen.findAllByText('Impressora')

    await usuarioEvento.click(screen.getByRole('tab', { name: 'Meus chamados' }))

    expect(ultimosParametros(get)).toMatchObject({ atendenteId: 5, status: 'EM_ANDAMENTO', sort: 'atualizadoEm,desc' })
    expect(screen.getByRole('tab', { name: 'Meus chamados', selected: true })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Assumir/ })).not.toBeInTheDocument()
  })

  it('abre direto na aba indicada pela URL', async () => {
    const get = simularApi(async () => ({ data: pagina([]) }))
    renderApp('/fila?aba=meus&status=TODOS')

    expect(await screen.findByText('Você não tem chamados nesta situação.')).toBeInTheDocument()
    expect(ultimosParametros(get)).toMatchObject({ atendenteId: 5 })
    // undefined é descartado pelo Axios: nenhum filtro de status chega à API.
    expect(ultimosParametros(get).status).toBeUndefined()
  })
})
