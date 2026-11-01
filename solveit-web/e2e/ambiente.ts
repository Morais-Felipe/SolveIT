import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

/**
 * Ambiente dos testes de ponta a ponta (plano 07-02 D-2): um compose separado do de
 * desenvolvimento, com nome de projeto e portas próprios, criado e apagado a cada
 * execução. Assim o banco começa vazio sem tocar nos dados do compose "solveit".
 */
export const PROJETO_COMPOSE = 'solveit-e2e'
export const PORTA_WEB = 8180
export const PORTA_MAILPIT = 8125
export const URL_WEB = `http://127.0.0.1:${PORTA_WEB}`
export const URL_MAILPIT = `http://127.0.0.1:${PORTA_MAILPIT}`

const ARQUIVO_COMPOSE = fileURLToPath(new URL('../../docker-compose.yml', import.meta.url))

/** Senha de teste usada por todos os usuários do E2E (a regra exige 15+ caracteres). */
export const SENHA_E2E = 'senha-de-teste-e2e-solveit'

/** Primeiro ADMIN, criado pela preparação global com o SQL de docs/PRIMEIRO-ADMIN.md. */
export const ADMIN_E2E = { nome: 'Ada Administradora', email: 'ada.admin@e2e.solveit.test' }

/**
 * Executa `docker compose` sobre o projeto do E2E. Os segredos vêm do ambiente do
 * processo (definidos na preparação global), nunca de um arquivo versionado.
 */
export function compose(args: string[], opcoes: { entrada?: string; mostrarSaida?: boolean } = {}) {
  const resultado = spawnSync(
    'docker',
    ['compose', '-f', ARQUIVO_COMPOSE, '-p', PROJETO_COMPOSE, ...args],
    {
      env: {
        ...process.env,
        SOLVEIT_WEB_PORT: String(PORTA_WEB),
        SOLVEIT_MAILPIT_PORT: String(PORTA_MAILPIT),
      },
      input: opcoes.entrada,
      encoding: 'utf8',
      stdio: opcoes.mostrarSaida ? ['pipe', 'inherit', 'inherit'] : 'pipe',
    },
  )
  if (resultado.error) {
    throw resultado.error
  }
  if (resultado.status !== 0) {
    throw new Error(`docker compose ${args.join(' ')} falhou (código ${resultado.status}):\n${resultado.stderr ?? ''}`)
  }
  return resultado.stdout ?? ''
}
