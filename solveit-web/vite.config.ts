import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    // "@/lib/api" aponta para "src/lib/api": imports curtos e estáveis.
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // Em desenvolvimento, o Vite repassa /api para a API Spring. O navegador enxerga
    // uma única origem, então o back-end não precisa liberar CORS (decisão D-2).
    proxy: {
      '/api': {
        target: process.env.SOLVEIT_API_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
})
