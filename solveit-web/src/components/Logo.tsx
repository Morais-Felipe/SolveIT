import { cn } from '@/lib/utils'

/** O S do SolveIT, em um único traço angular. O ícone da aba repete o desenho. */
export function Logo({ className }: { className?: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 32 32"
      fill="none"
      stroke="currentColor"
      strokeWidth="3.6"
      strokeLinejoin="miter"
      className={cn('text-primary', className)}
    >
      <path d="M26.5 10 15.5 3.8 5 10.3 27 21.7 16.5 28.2 5.5 22" />
    </svg>
  )
}

/** Logo com o nome, como aparece nos cabeçalhos. */
export function Marca({ className }: { className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <Logo className="size-[1.4em] shrink-0" />
      <span>
        Solve<span className="text-primary">IT</span>
      </span>
    </span>
  )
}
