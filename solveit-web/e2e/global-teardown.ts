import { compose } from './ambiente.ts'

/** Apaga containers, rede e volume do E2E; o compose de desenvolvimento não é tocado. */
export default async function globalTeardown() {
  compose(['down', '-v', '--remove-orphans'])
}
