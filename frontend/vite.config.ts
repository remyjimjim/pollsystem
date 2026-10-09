/// <reference types="vitest" />
import { defineConfig, searchForWorkspaceRoot } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath } from 'node:url'

// ../docs/manual locally and in CI/Pages builds (they check out the whole repo);
// /docs/manual in the docker-compose frontend container (mounted there).
const manualDir = fileURLToPath(new URL('../docs/manual', import.meta.url))

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    // Vite only watches its own root for NEW files, so pages added to
    // docs/manual after the dev server started never reached the help
    // library's import.meta.glob. Watch the manual folder too.
    { name: 'watch-manual', configureServer: server => { server.watcher.add(manualDir) } },
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // The help library lives outside the frontend, in docs/manual, so it
      // reads the same on GitHub and renders at /help (src/help/manual.ts).
      '@manual': manualDir
    }
  },
  server: {
    // host:true binds 0.0.0.0 so the dev server is reachable from outside the
    // container. Harmless on the host (still reachable at localhost:3000).
    host: true,
    port: 3000,
    // Let the dev server serve the help pages from outside the frontend root.
    fs: { allow: [searchForWorkspaceRoot(process.cwd()), manualDir] },
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
