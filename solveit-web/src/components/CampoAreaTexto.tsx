import type { ComponentProps } from 'react'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

type Props = Omit<ComponentProps<'textarea'>, 'value'> & {
  id: string
  rotulo: string
  value: string
  erro?: string
  /** Quando informado, limita o tamanho e mostra o contador "n / máximo". */
  maximo?: number
}

/** Área de texto com o visual do Input, rótulo, erro e contador ligados por acessibilidade. */
export function CampoAreaTexto({ id, rotulo, erro, maximo, value, className, ...props }: Props) {
  const idErro = `${id}-erro`
  const idContador = `${id}-contador`

  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{rotulo}</Label>
      <textarea
        id={id}
        value={value}
        maxLength={maximo}
        aria-invalid={erro ? true : undefined}
        aria-describedby={erro ? idErro : maximo ? idContador : undefined}
        className={cn(
          'w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-2 text-base outline-none transition-colors placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30',
          className,
        )}
        {...props}
      />
      {erro ? (
        <p id={idErro} className="text-xs text-destructive">
          {erro}
        </p>
      ) : (
        maximo && (
          <p id={idContador} className="text-right text-xs text-muted-foreground">
            {value.length.toLocaleString('pt-BR')} / {maximo.toLocaleString('pt-BR')}
          </p>
        )
      )}
    </div>
  )
}
