import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

// Vite proxy target for the backend. Keep local development on localhost by
// default; WSL/Docker users can set VITE_BACKEND_TARGET in frontend/.env.local.
function backendTarget(env: Record<string, string>): string {
  return env.VITE_BACKEND_TARGET || 'http://localhost:8080'
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // loadEnv is required because Vite does not expose .env values on
  // process.env while evaluating the config file.
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react()],
    server: {
      port: 5173,
      watch: {
        usePolling: true,
      },
      proxy: {
        '/api': {
          target: backendTarget(env),
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      restoreMocks: true,
      clearMocks: true,
    },
  }
})
