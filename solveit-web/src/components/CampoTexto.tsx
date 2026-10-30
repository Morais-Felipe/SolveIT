import type { ComponentProps } from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

type Props = ComponentProps<typeof Input> & {
  id: string
  rotulo: string
  erro?: string
  ajuda?: string
}

/** Campo com rótulo, texto de ajuda e mensagem de erro ligados por acessibilidade. */
export function CampoTexto({ id, rotulo, erro, ajuda, ...props }: Props) {
  const idAjuda = ajuda ? `${id}-ajuda` : undefined
  const idErro = erro ? `${id}-erro` : undefined

  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{rotulo}</Label>
      <Input
        id={id}
        aria-invalid={erro ? true : undefined}
        aria-describedby={[idAjuda, idErro].filter(Boolean).join(' ') || undefined}
        {...props}
      />
      {ajuda && !erro && (
        <p id={idAjuda} className="text-xs text-muted-foreground">
          {ajuda}
        </p>
      )}
      {erro && (
        <p id={idErro} className="text-xs text-destructive">
          {erro}
        </p>
      )}
    </div>
  )
}
