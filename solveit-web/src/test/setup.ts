import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Sem os globais do Vitest, o Testing Library não desmonta as telas sozinho:
// cada teste precisa começar com o documento vazio.
afterEach(() => {
  cleanup()
})
