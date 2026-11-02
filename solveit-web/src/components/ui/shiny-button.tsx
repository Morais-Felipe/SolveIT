import { LoaderCircle, type LucideIcon } from "lucide-react"
import * as React from "react"
import { cn } from "cn"

/**
 * Botão de destaque das telas de entrada: fundo preto e luz girando na borda (CSS em index.css).
 * O nome acessível vem do texto, e o ícone é decorativo. Em andamento, o indicador toma o lugar dele.
 */
function ShinyButton({
  className,
  icone: Icone,
  carregando = false,
  children,
  ...props
}: React.ComponentProps<"button"> & { icone?: LucideIcon; carregando?: boolean }) {
  return (
    <button data-slot="shiny-button" className={cn("botao-brilhante", className)} {...props}>
      <span className="flex items-center justify-center gap-2">
        {carregando ? (
          <LoaderCircle aria-hidden="true" className="size-4 animate-spin" />
        ) : (
          Icone && <Icone aria-hidden="true" className="size-4" />
        )}
        {children}
      </span>
    </button>
  )
}

export { ShinyButton }
