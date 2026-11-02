/**
 * Fundo das telas de entrada: faixas escuras nos cantos, com a borda acesa em laranja. É estático:
 * o movimento da tela fica com o botão. O desenho estica para caber ("none"): manter a proporção
 * cortaria os cantos em telas altas, como no celular, e são eles que têm as ondas.
 */
export function FundoOndas() {
  return (
    <div aria-hidden="true" className="pointer-events-none fixed inset-0 -z-10 overflow-hidden bg-black">
      <svg viewBox="0 0 1820 864" preserveAspectRatio="none" className="size-full">
        <defs>
          <linearGradient id="onda-faixa" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="white" stopOpacity="0.05" />
            <stop offset="100%" stopColor="white" stopOpacity="0.01" />
          </linearGradient>
          <linearGradient id="onda-luz-alto" x1="0" y1="1" x2="1" y2="0">
            <stop offset="0%" stopColor="var(--primary)" stopOpacity="0" />
            <stop offset="55%" stopColor="var(--primary)" stopOpacity="0.55" />
            <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.95" />
          </linearGradient>
          <linearGradient id="onda-luz-baixo" x1="1" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--primary)" stopOpacity="0.15" />
            <stop offset="60%" stopColor="var(--primary)" stopOpacity="0.8" />
            <stop offset="100%" stopColor="var(--primary)" stopOpacity="1" />
          </linearGradient>
          <linearGradient id="onda-brasa" x1="1" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--primary)" stopOpacity="0" />
            <stop offset="100%" stopColor="var(--primary)" stopOpacity="0.28" />
          </linearGradient>
          <filter id="onda-desfoque" x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur stdDeviation="14" />
          </filter>
        </defs>

        {/* Canto superior esquerdo. */}
        <path
          d="M0 300C230 225 440 205 560 0H745C650 165 470 265 270 345 165 390 70 455 0 535Z"
          fill="url(#onda-faixa)"
        />
        <path
          d="M0 300C230 225 440 205 560 0"
          fill="none"
          stroke="url(#onda-luz-alto)"
          strokeWidth="10"
          filter="url(#onda-desfoque)"
        />
        <path d="M0 300C230 225 440 205 560 0" fill="none" stroke="url(#onda-luz-alto)" strokeWidth="1.5" />

        {/* Canto inferior direito. */}
        <path d="M1820 410C1540 460 1340 610 1155 864H1820Z" fill="url(#onda-faixa)" />
        <path d="M1820 590C1630 630 1460 680 1350 864H1820Z" fill="url(#onda-brasa)" />
        <path
          d="M1820 590C1630 630 1460 680 1350 864"
          fill="none"
          stroke="url(#onda-luz-baixo)"
          strokeWidth="12"
          filter="url(#onda-desfoque)"
        />
        <path d="M1820 590C1630 630 1460 680 1350 864" fill="none" stroke="url(#onda-luz-baixo)" strokeWidth="2" />
      </svg>
    </div>
  )
}
