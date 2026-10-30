import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '@/App'
import { AuthProvider } from '@/auth/AuthProvider'
import { api, ApiError, tokenStorage } from '@/lib/api'
import type { Chamado, Comentario, HistoricoStatus, Usuario } from '@/lib/types'

const SOLICITANTE: Usuario = {
  id: 2,
  nome: 'Sol',
  email: 'sol@example.com',
  perfil: 'SOLICITANTE',
  ativo: true,
  criadoEm: '2026-10-30T10:00:00',
}
const ATENDENTE: Usuario = { ...SOLICITANTE, id: 5, nome: 'Ari Atendente', email: 'ari@example.com', perfil: 'ATENDENTE' }

function chamado(extra: Partial<Chamado> = {}): Chamado {
  return {
    id: 7,
    titulo: 'Impressora travada',
    descricao: 'Mostra erro de papel atolado.',
    status: 'ABERTO',
    prioridade: 'ALTA',
    solicitante: { id: 2, nome: 'Sol' },
    atendente: null,
    categoria: { id: 2, nome: 'Hardware', ativo: true },
    criadoEm: '2026-10-30T10:00:00',
    atualizadoEm: '2026-10-30T10:00:00',
    resolvidoEm: null,
    versao: 3,
    ...extra,
  }
}

const COMENTARIOS: Comentario[] = [
  { id: 1, texto: 'Já reiniciei a impressora.', autor: { id: 2, nome: 'Sol' }, criadoEm: '2026-10-30T10:05:00' },
]

const HISTORICO: HistoricoStatus[] = [
  {
    id: 1,
    statusAnterior: null,
    statusNovo: 'ABERTO',
    alteradoPor: { id: 2, nome: 'Sol' },
    alteradoEm: '2026-10-30T10:00:00',
    observacao: null,
  },
]

type Cenario = {
  usuario?: Usuario
  detalhe?: () => Promise<unknown>
  comentarios?: Comentario[]
}

/** Simula a API do detalhe por URL. Devolve o espião para inspecionar as chamadas. */
function simularApi({ usuario = SOLICITANTE, detalhe, comentarios = COMENTARIOS }: Cenario = {}) {
  return vi.spyOn(api, 'get').mockImplementation(async (url: string) => {
    if (url === '/auth/me') return { data: usuario }
    if (url === '/chamados/7') return detalhe ? detalhe() : { data: chamado() }
    if (url === '/chamados/7/comentarios') return { data: comentarios }
    if (url === '/chamados/7/historico') return { data: HISTORICO }
    if (url === '/categorias') return { data: { items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } }
    if (url === '/chamados') {
      return { data: { items: [chamado()], page: 0, size: 10, totalElements: 1, totalPages: 1 } }
    }
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

beforeEach(() => {
  localStorage.clear()
  tokenStorage.set('token-valido')
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('detalhe do chamado', () => {
  it('mostra dados, descrição, histórico e comentários', async () => {
    simularApi()
    renderApp('/chamados/7')

    expect(await screen.findByRole('heading', { name: 'Impressora travada' })).toBeInTheDocument()
    expect(screen.getByText('Mostra erro de papel atolado.')).toBeInTheDocument()
    expect(screen.getByText('Hardware')).toBeInTheDocument()
    expect(screen.getByText('Ninguém assumiu ainda')).toBeInTheDocument()
    expect(await screen.findByText('Já reiniciei a impressora.')).toBeInTheDocument()
    const historico = screen.getByRole('list', { name: 'Histórico de status' })
    expect(within(historico).getByText('Chamado aberto')).toBeInTheDocument()
  })

  it('não revela se o chamado existe quando o acesso é negado', async () => {
    simularApi({
      detalhe: async () => {
        throw new ApiError(403, 'ACCESS_DENIED', 'Acesso negado.', {})
      },
    })
    renderApp('/chamados/7')

    expect(await screen.findByText('Chamado não encontrado ou você não tem acesso a ele.')).toBeInTheDocument()
  })

  it('abre o detalhe a partir da lista', async () => {
    const usuarioEvento = userEvent.setup()
    simularApi()
    renderApp('/chamados')

    const [link] = await screen.findAllByRole('link', { name: 'Impressora travada' })
    await usuarioEvento.click(link)

    expect(await screen.findByRole('heading', { name: 'Impressora travada' })).toBeInTheDocument()
  })
})

describe('comentar e agir', () => {
  it('adiciona o comentário à conversa', async () => {
    const usuarioEvento = userEvent.setup()
    simularApi()
    const post = vi.spyOn(api, 'post').mockResolvedValue({
      data: { id: 2, texto: 'Ainda não funcionou.', autor: { id: 2, nome: 'Sol' }, criadoEm: '2026-10-30T11:00:00' },
    })
    renderApp('/chamados/7')

    await usuarioEvento.type(await screen.findByLabelText('Novo comentário'), 'Ainda não funcionou.')
    await usuarioEvento.click(screen.getByRole('button', { name: 'Comentar' }))

    expect(post).toHaveBeenCalledWith('/chamados/7/comentarios', { texto: 'Ainda não funcionou.' })
    expect(await screen.findByText('Ainda não funcionou.')).toBeInTheDocument()
    expect(screen.getByLabelText('Novo comentário')).toHaveValue('')
  })

  it('explica por que não dá para comentar num chamado fechado', async () => {
    simularApi({ detalhe: async () => ({ data: chamado({ status: 'FECHADO', atendente: { id: 5, nome: 'Ari' } }) }) })
    renderApp('/chamados/7')

    expect(await screen.findByText('A conversa foi encerrada porque o chamado está fechado.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Novo comentário')).not.toBeInTheDocument()
  })

  it('fecha um chamado resolvido enviando status, versão e observação', async () => {
    const usuarioEvento = userEvent.setup()
    simularApi({ detalhe: async () => ({ data: chamado({ status: 'RESOLVIDO', atendente: { id: 5, nome: 'Ari' } }) }) })
    const patch = vi.spyOn(api, 'patch').mockResolvedValue({
      data: chamado({ status: 'FECHADO', atendente: { id: 5, nome: 'Ari' }, versao: 4 }),
    })
    renderApp('/chamados/7')

    await usuarioEvento.click(await screen.findByRole('button', { name: 'Fechar chamado' }))
    await usuarioEvento.type(screen.getByLabelText('Observação (opcional)'), 'Resolvido, obrigado!')
    await usuarioEvento.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(patch).toHaveBeenCalledWith('/chamados/7/status', {
      status: 'FECHADO',
      versaoEsperada: 3,
      observacao: 'Resolvido, obrigado!',
    })
    expect(await screen.findByText('Status alterado para Fechado.')).toBeInTheDocument()
  })

  it('recarrega e avisa quando outra pessoa alterou o chamado antes', async () => {
    const usuarioEvento = userEvent.setup()
    let leituras = 0
    simularApi({
      detalhe: async () => {
        leituras += 1
        return { data: chamado(leituras === 1 ? {} : { titulo: 'Impressora travada (editado)', versao: 4 }) }
      },
    })
    vi.spyOn(api, 'patch').mockRejectedValue(
      new ApiError(409, 'STALE_RESOURCE', 'O recurso foi alterado por outra operação.', {}),
    )
    renderApp('/chamados/7')

    await usuarioEvento.click(await screen.findByRole('button', { name: 'Cancelar chamado' }))
    await usuarioEvento.click(screen.getByRole('button', { name: 'Confirmar' }))

    expect(
      await screen.findByText('O chamado foi alterado por outra pessoa. Confira os dados atualizados e tente de novo.'),
    ).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Impressora travada (editado)' })).toBeInTheDocument()
  })

  it('mostra Resolver ao atendente responsável e não ao solicitante', async () => {
    simularApi({
      usuario: ATENDENTE,
      detalhe: async () => ({ data: chamado({ status: 'EM_ANDAMENTO', atendente: { id: 5, nome: 'Ari Atendente' } }) }),
    })
    renderApp('/chamados/7')

    expect(await screen.findByRole('button', { name: 'Resolver' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Fechar chamado' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
  })

  it('edita título e descrição de um chamado aberto', async () => {
    const usuarioEvento = userEvent.setup()
    simularApi()
    const put = vi.spyOn(api, 'put').mockResolvedValue({
      data: chamado({ titulo: 'Impressora do 2º andar travada', versao: 4 }),
    })
    renderApp('/chamados/7')

    await usuarioEvento.click(await screen.findByRole('button', { name: 'Editar' }))
    const titulo = screen.getByLabelText('Título')
    await usuarioEvento.clear(titulo)
    await usuarioEvento.type(titulo, 'Impressora do 2º andar travada')
    await usuarioEvento.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(put).toHaveBeenCalledWith('/chamados/7', {
      titulo: 'Impressora do 2º andar travada',
      descricao: 'Mostra erro de papel atolado.',
      versaoEsperada: 3,
    })
    expect(await screen.findByRole('heading', { name: 'Impressora do 2º andar travada' })).toBeInTheDocument()
  })
})
