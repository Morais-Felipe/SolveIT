import type { LucideIcon } from 'lucide-react'
import type { ComponentProps, ReactNode } from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

type Props = ComponentProps<typeof Input> & {
  id: string
  rotulo: string
  erro?: string
  ajuda?: string
  icone?: LucideIcon
  /** Botão posicionado na ponta direita do campo. */
  acao?: ReactNode
}

/** Campo com rótulo, texto de ajuda e mensagem de erro ligados por acessibilidade. */
export function CampoTexto({ id, rotulo, erro, ajuda, icone: Icone, acao, className, ...props }: Props) {
  const idAjuda = ajuda ? `${id}-ajuda` : undefined
  const idErro = erro ? `${id}-erro` : undefined

  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>
        {Icone && <Icone aria-hidden="true" className="size-4 text-muted-foreground" />}
        {rotulo}
      </Label>
      <div className="relative">
        <Input
          id={id}
          className={cn(acao && 'pr-10', className)}
          aria-invalid={erro ? true : undefined}
          aria-describedby={[idAjuda, idErro].filter(Boolean).join(' ') || undefined}
          {...props}
        />
        {acao}
      </div>
      {ajuda && !erro && (
        <p id={idAjuda} className="text-xs text-muted-foreground">
          {ajuda}
        </p>
      )}
      {erro && (
        <p id={idErro} className="entrada-suave text-xs text-destructive">
          {erro}
        </p>
      )}
    </div>
  )
}
