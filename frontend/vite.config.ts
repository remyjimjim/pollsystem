/// <reference types="vitest" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // host:true binds 0.0.0.0 so the dev server is reachable from outside the
    // container. Harmless on the host (still reachable at localhost:3000).
    host: true,
    port: 3000,
    proxy: {
      '/api': {
        // Host dev proxies to localhost:8080; inside docker-compose the frontend
        // container reaches the backend container by service name — set
        // API_PROXY_TARGET=http://backend:8080.
        target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    },
    // Bind-mounted source in a container doesn't always deliver inotify events;
    // polling (opt-in via VITE_USE_POLLING) makes HMR reliable there. Left off
    // on the host, where native file events work.
    watch: process.env.VITE_USE_POLLING ? { usePolling: true, interval: 100 } : undefined
  },
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.{test,spec}.ts'],
    setupFiles: ['./vitest.setup.ts']
  }
})
