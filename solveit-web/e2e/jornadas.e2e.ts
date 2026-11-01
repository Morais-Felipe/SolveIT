import { expect, test, type Page } from '@playwright/test'
import { ADMIN_E2E, SENHA_E2E, URL_MAILPIT } from './ambiente.ts'

/**
 * Jornadas essenciais dos três perfis (plano 07-02 D-5, QUAL-05). Rodam em série: a
 * categoria criada pelo ADMIN recebe o chamado do solicitante, que o atendente assume.
 * Cada projeto (desktop, celular) usa os próprios usuários, sufixados pelo nome dele.
 */
test.describe.configure({ mode: 'serial' })

let sufixo = ''
let atendente = { nome: '', email: '' }
let solicitante = { nome: '', email: '' }
let categoria = ''
let titulo = ''
let chamadoId = ''

test.beforeAll(() => {
  sufixo = test.info().project.name
  atendente = { nome: `Ari Atendente ${sufixo}`, email: `ari.${sufixo}@e2e.solveit.test` }
  solicitante = { nome: `Sol Solicitante ${sufixo}`, email: `sol.${sufixo}@e2e.solveit.test` }
  categoria = `Rede ${sufixo}`
  titulo = `Impressora sem rede (${sufixo})`
})

async function entrar(page: Page, email: string) {
  await page.goto('/login')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha').fill(SENHA_E2E)
  await page.getByRole('button', { name: 'Entrar' }).click()
}

async function sair(page: Page) {
  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()
}

/** Navega pelo menu, exigindo antes que todos os itens estejam inteiros na tela, sem rolagem (WEB-07). */
async function irPeloMenu(page: Page, item: string) {
  const menu = page.getByRole('navigation', { name: 'Menu principal' })
  for (const link of await menu.getByRole('link').all()) {
    await expect(link).toBeInViewport({ ratio: 1 })
  }
  await menu.getByRole('link', { name: item }).click()
}

/** A tela atual cabe na largura do aparelho: nada empurra a página para o lado (WEB-07). */
async function semRolagemHorizontal(page: Page) {
  const larguras = await page.evaluate(() => ({
    conteudo: document.documentElement.scrollWidth,
    tela: window.innerWidth,
  }))
  expect(larguras.conteudo, 'largura do conteúdo').toBeLessThanOrEqual(larguras.tela)
}

test('ADMIN torna um usuário atendente e cria uma categoria', async ({ page, request }) => {
  const cadastro = await request.post('/api/auth/register', {
    data: { nome: atendente.nome, email: atendente.email, senha: SENHA_E2E },
  })
  expect(cadastro.status()).toBe(201)

  await entrar(page, ADMIN_E2E.email)
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
  await semRolagemHorizontal(page)

  await irPeloMenu(page, 'Usuários')
  const linhaAtendente = page.getByRole('listitem').filter({ hasText: atendente.email })
  await linhaAtendente.getByRole('button', { name: 'Perfil e situação' }).click()
  await linhaAtendente.getByLabel('Perfil').selectOption({ label: 'Atendente' })
  await linhaAtendente.getByRole('button', { name: 'Salvar perfil e situação' }).click()
  await expect(page.getByText(`Perfil e situação de ${atendente.nome} atualizados.`)).toBeVisible()
  await semRolagemHorizontal(page)

  await irPeloMenu(page, 'Categorias')
  await page.getByRole('button', { name: 'Nova categoria' }).click()
  await page.getByLabel('Nome').fill(categoria)
  await page.getByRole('button', { name: 'Criar categoria' }).click()
  await expect(page.getByRole('listitem').filter({ hasText: categoria })).toBeVisible()
  await semRolagemHorizontal(page)
})

test('solicitante se cadastra, abre um chamado e comenta', async ({ page, request }) => {
  await page.goto('/login')
  await page.getByRole('link', { name: 'Cadastre-se' }).click()
  await page.getByLabel('Nome').fill(solicitante.nome)
  await page.getByLabel('E-mail').fill(solicitante.email)
  await page.getByLabel('Senha').fill(SENHA_E2E)
  await page.getByRole('button', { name: 'Criar conta' }).click()
  await expect(page.getByRole('heading', { name: 'Meus chamados' })).toBeVisible()
  await semRolagemHorizontal(page)

  await irPeloMenu(page, 'Abrir chamado')
  await page.getByLabel('Título').fill(titulo)
  await page.getByLabel('Descrição').fill('A impressora do segundo andar não aparece na rede desde ontem.')
  await page.getByLabel('Prioridade').selectOption({ label: 'Alta' })
  await page.getByLabel('Categoria').selectOption({ label: categoria })
  await semRolagemHorizontal(page)
  await page.getByRole('button', { name: 'Abrir chamado' }).click()

  const aviso = page.getByText(/Chamado #\d+ aberto com sucesso\./)
  await expect(aviso).toBeVisible()
  chamadoId = (await aviso.textContent())?.match(/#(\d+)/)?.[1] ?? ''
  expect(chamadoId).not.toBe('')

  await page.getByRole('link', { name: titulo }).first().click()
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible()
  await page.getByLabel('Novo comentário').fill('Já reiniciei a impressora, sem efeito.')
  await page.getByRole('button', { name: 'Comentar' }).click()
  await expect(page.getByRole('list', { name: 'Comentários' })).toContainText('Já reiniciei a impressora')
  await semRolagemHorizontal(page)

  // WEB-07: a interface esconde as telas de ADMIN, mas quem garante é a API.
  const login = await request.post('/api/auth/login', { data: { email: solicitante.email, senha: SENHA_E2E } })
  const { accessToken } = await login.json()
  const proibido = await request.get('/api/usuarios', { headers: { Authorization: `Bearer ${accessToken}` } })
  expect(proibido.status()).toBe(403)
  expect((await proibido.json()).code).toBe('ACCESS_DENIED')
})

test('atendente assume e resolve; o solicitante é avisado e fecha', async ({ page, request }) => {
  await entrar(page, atendente.email)
  await expect(page.getByRole('heading', { name: 'Fila' })).toBeVisible()
  await semRolagemHorizontal(page)
  await page.getByRole('button', { name: `Assumir ${titulo}` }).click()
  await expect(page.getByText(`Você assumiu o chamado #${chamadoId}.`)).toBeVisible()
  await expect(page.getByRole('definition').filter({ hasText: atendente.nome })).toBeVisible()

  await page.getByRole('region', { name: 'Ações' }).getByRole('button', { name: 'Resolver' }).click()
  await page.getByRole('button', { name: 'Confirmar' }).click()
  await expect(page.getByText('Status alterado para Resolvido.')).toBeVisible()
  await semRolagemHorizontal(page)

  // MAIL-03: o solicitante recebe o aviso de que o chamado foi atribuído.
  await expect
    .poll(async () => {
      const resposta = await request.get(`${URL_MAILPIT}/api/v1/messages`)
      const { messages } = (await resposta.json()) as {
        messages: { Subject: string; Snippet: string; To: { Address: string }[] }[]
      }
      return messages.some(
        (m) =>
          m.To.some((d) => d.Address === solicitante.email) &&
          m.Subject === `[SolveIT] #${chamadoId} ${titulo}` &&
          m.Snippet.includes(`atribuído a ${atendente.nome}`),
      )
    })
    .toBe(true)

  await sair(page)
  await entrar(page, solicitante.email)
  await expect(page.getByRole('heading', { name: 'Meus chamados' })).toBeVisible()
  await semRolagemHorizontal(page)
  await page.goto(`/chamados/${chamadoId}`)
  await page.getByRole('region', { name: 'Ações' }).getByRole('button', { name: 'Fechar chamado' }).click()
  await page.getByRole('button', { name: 'Confirmar' }).click()
  await expect(page.getByText('Status alterado para Fechado.')).toBeVisible()
  await expect(page.getByRole('list', { name: 'Histórico de status' })).toContainText('Resolvido → Fechado')
  await semRolagemHorizontal(page)
})
