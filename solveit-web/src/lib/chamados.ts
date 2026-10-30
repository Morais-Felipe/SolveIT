import { api } from '@/lib/api'
import type { Categoria, Chamado, Comentario, HistoricoStatus, Pagina, Prioridade, StatusChamado } from '@/lib/types'

export const ROTULO_STATUS: Record<StatusChamado, string> = {
  ABERTO: 'Aberto',
  EM_ANDAMENTO: 'Em andamento',
  RESOLVIDO: 'Resolvido',
  FECHADO: 'Fechado',
  CANCELADO: 'Cancelado',
}

export const ROTULO_PRIORIDADE: Record<Prioridade, string> = {
  BAIXA: 'Baixa',
  MEDIA: 'Média',
  ALTA: 'Alta',
  CRITICA: 'Crítica',
}

export const STATUS = Object.keys(ROTULO_STATUS) as StatusChamado[]
export const PRIORIDADES = Object.keys(ROTULO_PRIORIDADE) as Prioridade[]

/** Filtros que a lista envia. O escopo por perfil (só os meus chamados) é decidido pela API. */
export type FiltrosChamados = {
  status?: StatusChamado
  prioridade?: Prioridade
  categoriaId?: number
  page: number
}

export const TAMANHO_PAGINA = 10

export async function listarChamados(filtros: FiltrosChamados, signal?: AbortSignal): Promise<Pagina<Chamado>> {
  const resposta = await api.get<Pagina<Chamado>>('/chamados', {
    params: { ...filtros, size: TAMANHO_PAGINA, sort: 'criadoEm,desc' },
    signal,
  })
  return resposta.data
}

/** Exatamente os quatro campos aceitos por ChamadoDtos.Criar; qualquer outro vira 400. */
export type NovoChamado = {
  titulo: string
  descricao: string
  prioridade: Prioridade
  categoriaId: number
}

export async function abrirChamado(dados: NovoChamado): Promise<Chamado> {
  const resposta = await api.post<Chamado>('/chamados', dados)
  return resposta.data
}

/** Categorias ativas para filtros e formulários. 100 é o limite de página da API. */
export async function listarCategoriasAtivas(signal?: AbortSignal): Promise<Categoria[]> {
  const resposta = await api.get<Pagina<Categoria>>('/categorias', { params: { size: 100 }, signal })
  return resposta.data.items
}

/** Formata LocalDateTime (sem fuso) como data local curta, ex.: 30/10/2026. */
export function formatarData(valor: string): string {
  const data = new Date(valor)
  return Number.isNaN(data.getTime()) ? valor : data.toLocaleDateString('pt-BR')
}

export async function buscarChamado(id: number, signal?: AbortSignal): Promise<Chamado> {
  const resposta = await api.get<Chamado>(`/chamados/${id}`, { signal })
  return resposta.data
}

export async function listarComentarios(chamadoId: number, signal?: AbortSignal): Promise<Comentario[]> {
  const resposta = await api.get<Comentario[]>(`/chamados/${chamadoId}/comentarios`, { signal })
  return resposta.data
}

export async function comentar(chamadoId: number, texto: string): Promise<Comentario> {
  const resposta = await api.post<Comentario>(`/chamados/${chamadoId}/comentarios`, { texto })
  return resposta.data
}

export async function listarHistorico(chamadoId: number, signal?: AbortSignal): Promise<HistoricoStatus[]> {
  const resposta = await api.get<HistoricoStatus[]>(`/chamados/${chamadoId}/historico`, { signal })
  return resposta.data
}

/** versaoEsperada é a versão que a tela mostrou: se outra pessoa alterou antes, a API devolve 409. */
export async function alterarStatus(
  chamado: Chamado,
  status: StatusChamado,
  observacao: string,
): Promise<Chamado> {
  const resposta = await api.patch<Chamado>(`/chamados/${chamado.id}/status`, {
    status,
    versaoEsperada: chamado.versao,
    observacao: observacao.trim() || undefined,
  })
  return resposta.data
}

export async function editarChamado(chamado: Chamado, titulo: string, descricao: string): Promise<Chamado> {
  const resposta = await api.put<Chamado>(`/chamados/${chamado.id}`, {
    titulo: titulo.trim(),
    descricao,
    versaoEsperada: chamado.versao,
  })
  return resposta.data
}

/** Formata LocalDateTime como data e hora locais, ex.: 30/10/2026 14:05. */
export function formatarDataHora(valor: string): string {
  const data = new Date(valor)
  return Number.isNaN(data.getTime())
    ? valor
    : data.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}
