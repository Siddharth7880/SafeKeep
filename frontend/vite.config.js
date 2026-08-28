import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    // jsdom provides WebCrypto (crypto.subtle) — required for our vault.js tests
    environment: 'node',
    globals: true,
    // hash-wasm uses WASM — needs Node environment for proper WASM loading in tests
    pool: 'forks',
    poolOptions: {
      forks: {
        singleFork: true,
      },
    },
  },
})

