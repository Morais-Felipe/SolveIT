import { useEffect, useRef } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'

/**
 * Aviso de resultado no topo da tela. Como a página não muda de altura ao recarregar, quem agiu
 * no fim de uma lista longa não veria o aviso: por isso ele rola até ficar visível. Se já está
 * visível, nada se move. A rolagem é instantânea porque a lista recarrega no mesmo momento, e
 * uma rolagem animada poderia ser interrompida no caminho, deixando o aviso fora da tela.
 */
export function AvisoPagina({ texto, erro = false }: { texto: string; erro?: boolean }) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    ref.current?.scrollIntoView({ block: 'nearest', behavior: 'instant' })
  }, [texto])

  return (
    <Alert ref={ref} variant={erro ? 'destructive' : 'default'} className="scroll-mt-4">
      <AlertDescription>{texto}</AlertDescription>
    </Alert>
  )
}
