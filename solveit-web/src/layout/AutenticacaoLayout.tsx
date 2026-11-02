import type { ReactNode } from 'react'
import { FundoOndas } from '@/components/FundoOndas'
import { Logo, Marca } from '@/components/Logo'
import { Card } from '@/components/ui/card'

/**
 * Moldura de Login e Cadastro. A altura mínima (em vez de altura fixa) deixa a página rolar
 * quando o cartão não cabe, como em celular deitado ou com o teclado aberto.
 */
export function AutenticacaoLayout({ children }: { children: ReactNode }) {
  return (
    <div className="isolate flex min-h-svh flex-col text-foreground">
      <FundoOndas />
      <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 px-5 py-5 md:px-12 md:py-8">
        <Marca className="text-xl font-semibold tracking-tight" />
        <p className="flex items-center gap-4 text-xs text-muted-foreground md:text-sm">
          <span aria-hidden="true" className="hidden h-px w-10 bg-primary sm:block" />
          <span>
            Abra. Acompanhe. <span className="text-primary">Resolva.</span>
          </span>
        </p>
      </header>
      <main className="flex flex-1 items-center justify-center px-4 pb-8 md:pb-16">
        <Card className="cartao-entrada relative w-full max-w-md gap-5 rounded-3xl bg-card/70 backdrop-blur [--card-spacing:--spacing(6)] animate-in fade-in slide-in-from-bottom-4 duration-300 motion-reduce:animate-none md:[--card-spacing:--spacing(8)]">
          <Logo className="mx-auto size-14" />
          {children}
        </Card>
      </main>
    </div>
  )
}
