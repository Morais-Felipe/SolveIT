import { api } from '@/lib/api'
import type {
  Categoria,
  Chamado,
  Comentario,
  HistoricoStatus,
  Pagina,
  Pessoa,
  Prioridade,
  StatusChamado,
  Usuario,
} from '@/lib/types'

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
  atendenteId?: number
  page: number
}

export const TAMANHO_PAGINA = 10

/** A API só ordena por criadoEm, atualizadoEm e id. */
export type Ordem = 'criadoEm,desc' | 'criadoEm,asc' | 'atualizadoEm,desc'

export async function listarChamados(
  filtros: FiltrosChamados,
  signal?: AbortSignal,
  ordem: Ordem = 'criadoEm,desc',
): Promise<Pagina<Chamado>> {
  const resposta = await api.get<Pagina<Chamado>>('/chamados', {
    params: { ...filtros, size: TAMANHO_PAGINA, sort: ordem },
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

/**
 * A API grava as datas em UTC (LocalDateTime.now(ZoneOffset.UTC)) e as envia sem fuso,
 * ex.: 2026-10-30T18:16:00. Sem o "Z", o navegador leria esse texto como horário local.
 */
export function lerDataDaApi(valor: string): Date {
  const temFuso = /(Z|[+-]\d{2}:?\d{2})$/i.test(valor)
  return new Date(temFuso ? valor : `${valor}Z`)
}

/** Formata uma data da API como data curta no fuso de quem vê, ex.: 30/10/2026. */
export function formatarData(valor: string): string {
  const data = lerDataDaApi(valor)
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

/** Formata uma data da API como data e hora no fuso de quem vê, ex.: 30/10/2026, 15:16. */
export function formatarDataHora(valor: string): string {
  const data = lerDataDaApi(valor)
  return Number.isNaN(data.getTime())
    ? valor
    : data.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

/**
 * Tira o chamado da fila. O atendente passa o próprio id (assumir); o ADMIN escolhe
 * qualquer atendente ativo (atribuir). Se outro atendente chegou antes, a API devolve 409.
 */
export async function atribuir(chamado: Chamado, atendenteId: number, observacao = ''): Promise<Chamado> {
  const resposta = await api.patch<Chamado>(`/chamados/${chamado.id}/atendente`, {
    atendenteId,
    versaoEsperada: chamado.versao,
    observacao: observacao.trim() || undefined,
  })
  return resposta.data
}

/** Atendentes ativos, para o ADMIN escolher. Percorre todas as páginas de /usuarios (100 por vez). */
export async function listarAtendentesAtivos(signal?: AbortSignal): Promise<Pessoa[]> {
  const atendentes: Pessoa[] = []
  let page = 0
  let totalPages = 1
  while (page < totalPages) {
    const resposta = await api.get<Pagina<Usuario>>('/usuarios', { params: { page, size: 100 }, signal })
    for (const u of resposta.data.items) {
      if (u.perfil === 'ATENDENTE' && u.ativo) atendentes.push({ id: u.id, nome: u.nome })
    }
    totalPages = resposta.data.totalPages
    page += 1
  }
  return atendentes.sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'))
}
