import { Eye, EyeOff, Lock } from 'lucide-react'
import { useState, type ComponentProps } from 'react'
import { CampoTexto } from '@/components/CampoTexto'

type Props = Omit<ComponentProps<typeof CampoTexto>, 'type' | 'icone' | 'acao'>

/** Campo de senha com botão para mostrar o que foi digitado. */
export function CampoSenha(props: Props) {
  const [visivel, setVisivel] = useState(false)
  const Icone = visivel ? EyeOff : Eye

  return (
    <CampoTexto
      {...props}
      type={visivel ? 'text' : 'password'}
      icone={Lock}
      acao={
        // type="button": sem isso, o clique enviaria o formulário.
        <button
          type="button"
          aria-label={visivel ? 'Ocultar senha' : 'Mostrar senha'}
          className="absolute top-1/2 right-1 grid size-8 -translate-y-1/2 place-items-center rounded-md text-muted-foreground outline-none transition-colors hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50"
          onClick={() => setVisivel((atual) => !atual)}
        >
          <Icone aria-hidden="true" className="size-4" />
        </button>
      }
    />
  )
}
