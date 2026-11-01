import { defineConfig, devices } from '@playwright/test'
import { URL_WEB } from './e2e/ambiente.ts'

/**
 * Testes de ponta a ponta (plano 07-02): navegador real contra o compose isolado.
 * `npm run e2e` sobe o ambiente, roda as jornadas em desktop e celular e apaga tudo.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  // As jornadas dependem umas das outras (o chamado aberto é o que o atendente assume).
  workers: 1,
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  // Sem novas tentativas: um teste instável precisa aparecer, não ser escondido.
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  globalSetup: './e2e/global-setup.ts',
  globalTeardown: './e2e/global-teardown.ts',
  use: {
    baseURL: URL_WEB,
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    { name: 'celular', use: { ...devices['Galaxy S8'] } },
  ],
})
