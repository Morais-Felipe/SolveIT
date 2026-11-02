import { lerDataDaApi } from '@/lib/chamados'
import type { ResumoDashboard } from '@/lib/types'

export type LinhaSemana = ResumoDashboard['semanas'][number]

const DIA_E_MES = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit' })

/** Dia e mês no fuso de quem vê, ex.: "08/06". */
export function formatarDiaEMes(valor: string): string {
  return DIA_E_MES.format(lerDataDaApi(valor))
}

/** Período de uma janela, ex.: "08/06 a 15/06". */
export function formatarPeriodo({ inicio, fim }: Pick<LinhaSemana, 'inicio' | 'fim'>): string {
  return `${formatarDiaEMes(inicio)} a ${formatarDiaEMes(fim)}`
}
