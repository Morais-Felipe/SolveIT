import { api } from '@/lib/api'
import type { Categoria, Pagina, Perfil, ResultadoOperacao, ResumoDashboard, UsuarioAdmin } from '@/lib/types'

export const ROTULO_PERFIL: Record<Perfil, string> = {
  SOLICITANTE: 'Solicitante',
  ATENDENTE: 'Atendente',
  ADMIN: 'Administrador',
}

export const PERFIS = Object.keys(ROTULO_PERFIL) as Perfil[]

/** Mesma regra do @ValidPassword da API; a API continua sendo quem decide. */
export const SENHA_MINIMA = 15

export const TAMANHO_PAGINA_ADMIN = 20

export async function buscarResumo(signal?: AbortSignal): Promise<ResumoDashboard> {
  const resposta = await api.get<ResumoDashboard>('/dashboard/resumo', { signal })
  return resposta.data
}

export async function listarUsuarios(page: number, signal?: AbortSignal): Promise<Pagina<UsuarioAdmin>> {
  const resposta = await api.get<Pagina<UsuarioAdmin>>('/usuarios', {
    params: { page, size: TAMANHO_PAGINA_ADMIN },
    signal,
  })
  return resposta.data
}

export type NovoUsuario = { nome: string; email: string; senha: string; perfil: Perfil }

export async function criarUsuario(dados: NovoUsuario): Promise<UsuarioAdmin> {
  const resposta = await api.post<UsuarioAdmin>('/usuarios', dados)
  return resposta.data
}

/**
 * Só dados pessoais (PUT). Perfil e situação vão por alterarOperacao: a API recusa
 * com 400 se este corpo trouxer perfil ou ativo. Senha vazia mantém a atual.
 */
export async function editarDadosUsuario(
  usuario: UsuarioAdmin,
  dados: { nome: string; email: string; senha: string },
): Promise<UsuarioAdmin> {
  const resposta = await api.put<UsuarioAdmin>(`/usuarios/${usuario.id}`, {
    nome: dados.nome,
    email: dados.email,
    senha: dados.senha || undefined,
    versaoEsperada: usuario.versao,
  })
  return resposta.data
}

/** Perfil e situação numa única operação; a API devolve quantos chamados voltaram para a fila. */
export async function alterarOperacao(
  usuario: UsuarioAdmin,
  perfil: Perfil,
  ativo: boolean,
): Promise<ResultadoOperacao> {
  const resposta = await api.patch<ResultadoOperacao>(`/usuarios/${usuario.id}/operacao`, {
    perfil,
    ativo,
    versaoEsperada: usuario.versao,
  })
  return resposta.data
}

/** Catálogo da administração. "incluirInativas" só é aceito para o ADMIN (plano 06-05 D-1). */
export async function listarCategorias(
  page: number,
  incluirInativas: boolean,
  signal?: AbortSignal,
): Promise<Pagina<Categoria>> {
  const resposta = await api.get<Pagina<Categoria>>('/categorias', {
    params: { page, size: TAMANHO_PAGINA_ADMIN, incluirInativas: incluirInativas || undefined },
    signal,
  })
  return resposta.data
}

export async function criarCategoria(dados: { nome: string; descricao: string }): Promise<Categoria> {
  const resposta = await api.post<Categoria>('/categorias', {
    nome: dados.nome.trim(),
    descricao: dados.descricao.trim() || undefined,
  })
  return resposta.data
}

export async function editarCategoria(
  categoria: Categoria,
  dados: { nome: string; descricao: string },
): Promise<Categoria> {
  const resposta = await api.put<Categoria>(`/categorias/${categoria.id}`, {
    nome: dados.nome.trim(),
    descricao: dados.descricao.trim() || undefined,
    versaoEsperada: categoria.versao,
  })
  return resposta.data
}

export async function alterarAtivoCategoria(categoria: Categoria, ativo: boolean): Promise<Categoria> {
  const resposta = await api.patch<Categoria>(`/categorias/${categoria.id}/ativo`, {
    ativo,
    versaoEsperada: categoria.versao,
  })
  return resposta.data
}

/** Mensagem de D-6 para 409 STALE_RESOURCE: a tela recarrega antes de mostrá-la. */
export const REGISTRO_DESATUALIZADO = 'Outra pessoa alterou este registro. Recarregamos os dados; revise e tente de novo.'
