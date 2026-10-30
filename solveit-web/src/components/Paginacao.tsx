import { Button } from '@/components/ui/button'
import type { Pagina } from '@/lib/types'

/** Anterior / "Página X de Y" / Próxima. Some quando há uma página só. */
export function Paginacao({ pagina, aoMudar }: { pagina: Pagina<unknown>; aoMudar: (numero: number) => void }) {
  if (pagina.totalPages <= 1) {
    return null
  }
  return (
    <nav aria-label="Paginação" className="flex items-center justify-between gap-3 text-sm">
      <Button variant="outline" size="sm" disabled={pagina.page === 0} onClick={() => aoMudar(pagina.page - 1)}>
        Anterior
      </Button>
      <span className="text-muted-foreground">
        Página {pagina.page + 1} de {pagina.totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        disabled={pagina.page + 1 >= pagina.totalPages}
        onClick={() => aoMudar(pagina.page + 1)}
      >
        Próxima
      </Button>
    </nav>
  )
}
