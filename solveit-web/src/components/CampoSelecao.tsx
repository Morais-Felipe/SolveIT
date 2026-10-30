import type { ComponentProps } from 'react'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

type Props = ComponentProps<'select'> & {
  id: string
  rotulo: string
  erro?: string
}

/**
 * <select> nativo com o visual do Input. Nativo de propósito: é acessível sem esforço
 * e, no celular, abre o seletor do próprio sistema.
 */
export function CampoSelecao({ id, rotulo, erro, className, children, ...props }: Props) {
  const idErro = erro ? `${id}-erro` : undefined

  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{rotulo}</Label>
      <select
        id={id}
        aria-invalid={erro ? true : undefined}
        aria-describedby={idErro}
        className={cn(
          'h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2 text-base outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30 [&>option]:bg-popover [&>option]:text-popover-foreground',
          className,
        )}
        {...props}
      >
        {children}
      </select>
      {erro && (
        <p id={idErro} className="text-xs text-destructive">
          {erro}
        </p>
      )}
    </div>
  )
}
