import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'
import { execSync } from 'node:child_process'

// Vite proxy target for the backend. When running Vite inside WSL with the
// backend on Windows (IntelliJ), localhost resolves to WSL itself, so default
// to the Windows host IP (WSL default gateway) unless overridden.
function backendTarget(): string {
  if (process.env.VITE_BACKEND_TARGET) return process.env.VITE_BACKEND_TARGET
  try {
    const host = execSync('ip route show | grep default | awk \'{print $3}\'', { encoding: 'utf8' }).trim()
    if (host) return `http://${host}:8080`
  } catch {
    // not WSL; fall back to localhost
  }
  return 'http://localhost:8080'
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    watch: {
      usePolling: true,
    },
    proxy: {
      '/api': {
        target: backendTarget(),
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
})
