import { randomBytes } from 'node:crypto'
import { ADMIN_E2E, compose, SENHA_E2E, URL_WEB } from './ambiente.ts'

const ESPERA_API_MS = 180_000

/**
 * Roda uma vez antes de todos os testes: sobe o compose isolado com banco vazio, espera
 * a API responder e cria o primeiro ADMIN pelo procedimento documentado (D-2, D-3).
 */
export default async function globalSetup() {
  // Segredos descartáveis, válidos só nesta execução. Ficam no ambiente do processo,
  // que o Playwright repassa ao teardown e aos workers.
  process.env.DB_USERNAME = 'solveit'
  process.env.DB_PASSWORD = randomBytes(18).toString('hex')
  process.env.SOLVEIT_JWT_SECRET = randomBytes(48).toString('base64')

  // Uma execução interrompida pode ter deixado containers e volume para trás.
  compose(['down', '-v', '--remove-orphans'])
  compose(['up', '--build', '-d'], { mostrarSaida: true })
  await esperarApi()

  await cadastrar(ADMIN_E2E.nome, ADMIN_E2E.email)
  promoverPrimeiroAdmin(ADMIN_E2E.email)
}

/** Pelo nginx, a API responde 401 em /api/auth/me assim que sobe; antes disso, 502. */
async function esperarApi() {
  const limite = Date.now() + ESPERA_API_MS
  while (Date.now() < limite) {
    try {
      const resposta = await fetch(`${URL_WEB}/api/auth/me`)
      if (resposta.status === 401) {
        return
      }
    } catch {
      // nginx ainda não aceita conexões
    }
    await new Promise((resolver) => setTimeout(resolver, 2_000))
  }
  throw new Error(`A API não respondeu em ${ESPERA_API_MS / 1000}s. Veja: docker compose -p solveit-e2e logs api`)
}

async function cadastrar(nome: string, email: string) {
  const resposta = await fetch(`${URL_WEB}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nome, email, senha: SENHA_E2E }),
  })
  if (resposta.status !== 201) {
    throw new Error(`Cadastro de ${email} respondeu ${resposta.status}: ${await resposta.text()}`)
  }
}

/**
 * Versão não interativa do SQL de docs/PRIMEIRO-ADMIN.md. No documento, uma pessoa lê
 * admins_ativos e linhas_alteradas e escolhe COMMIT ou ROLLBACK. Aqui a condição
 * "nenhum ADMIN ativo" fica dentro do próprio UPDATE, então o COMMIT nunca promove
 * ninguém indevidamente; depois conferimos os dois números como a pessoa faria.
 */
function promoverPrimeiroAdmin(email: string) {
  const sql = `
START TRANSACTION;
SELECT id FROM core_write_guard WHERE id = 1 FOR UPDATE;
SELECT COUNT(*) INTO @admins_ativos FROM usuario WHERE perfil = 'ADMIN' AND ativo = TRUE;
SELECT id INTO @id FROM usuario WHERE email = '${email}' AND perfil = 'SOLICITANTE' AND ativo = TRUE;
UPDATE usuario
SET perfil = 'ADMIN', version = version + 1
WHERE id = @id AND email = '${email}' AND perfil = 'SOLICITANTE' AND ativo = TRUE
  AND @admins_ativos = 0;
SELECT @admins_ativos, ROW_COUNT();
COMMIT;
`
  // As credenciais do banco são lidas de dentro do container, não da linha de comando daqui.
  const saida = compose(
    ['exec', '-T', 'mysql', 'sh', '-c', 'mysql --batch --skip-column-names -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" solveit'],
    { entrada: sql },
  )
  const ultimaLinha = saida.trim().split('\n').at(-1)?.trim()
  if (ultimaLinha !== '0\t1') {
    throw new Error(`Promoção do primeiro ADMIN não confirmada (admins_ativos, linhas_alteradas = "${ultimaLinha}")`)
  }
}
