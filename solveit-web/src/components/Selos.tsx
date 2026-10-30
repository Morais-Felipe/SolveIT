import { ROTULO_PRIORIDADE, ROTULO_STATUS } from '@/lib/chamados'
import type { Prioridade, StatusChamado } from '@/lib/types'
import { cn } from '@/lib/utils'

const COR_STATUS: Record<StatusChamado, string> = {
  ABERTO: 'border-sky-500/40 bg-sky-500/10 text-sky-700 dark:text-sky-300',
  EM_ANDAMENTO: 'border-primary/40 bg-primary/10 text-primary',
  RESOLVIDO: 'border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300',
  FECHADO: 'border-border bg-muted text-muted-foreground',
  CANCELADO: 'border-border bg-muted text-muted-foreground line-through',
}

const COR_PRIORIDADE: Record<Prioridade, string> = {
  BAIXA: 'text-muted-foreground',
  MEDIA: 'text-foreground',
  ALTA: 'text-amber-600 dark:text-amber-400',
  CRITICA: 'font-semibold text-destructive',
}

export function SeloStatus({ status }: { status: StatusChamado }) {
  return (
    <span className={cn('inline-flex rounded-full border px-2 py-0.5 text-xs whitespace-nowrap', COR_STATUS[status])}>
      {ROTULO_STATUS[status]}
    </span>
  )
}

export function SeloPrioridade({ prioridade }: { prioridade: Prioridade }) {
  return (
    <span className={cn('text-sm whitespace-nowrap', COR_PRIORIDADE[prioridade])}>{ROTULO_PRIORIDADE[prioridade]}</span>
  )
}
