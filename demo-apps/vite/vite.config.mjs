import { defineConfig } from 'vite'

// Ports default to the dev convention (8090/8091, see root settings.json) but are overridable via
// env vars so testing/setup.sh can point a test run at its own backend (testing/config.env) without
// colliding with a dev server already running on the default ports.
const frontendPort = parseInt(process.env.FRONTEND_PORT || '8091')
const backendPort = parseInt(process.env.BACKEND_PORT || '8090')

export default defineConfig({
    root: "kotlin",
    server: {
        host: true,
        port: frontendPort,
        allowedHosts: ["*"],
        proxy: {
            '/api': {
                target: `http://localhost:${backendPort}`,
                // changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api/, ''),
                ws: true,
            }
        }
    },
})