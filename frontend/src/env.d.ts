/// <reference types="vite/client" />
// Vite's client types: import.meta.glob (the help library, src/help/manual.ts),
// import.meta.env, and `?raw` imports.

/** This build's id (vite.config.ts), compared with /version.json to spot a newer deploy. */
declare const __BUILD_ID__: string
