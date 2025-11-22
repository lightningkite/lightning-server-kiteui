import { defineConfig } from 'vite'

export default defineConfig({
    root: "kotlin",
    server: {
        host: true,
        port: 8941,
        allowedHosts: ["jivie.lightningkite.com", "localhost:8941"],
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                // changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api/, ''),
                ws: true,
            }
        }
    },
})